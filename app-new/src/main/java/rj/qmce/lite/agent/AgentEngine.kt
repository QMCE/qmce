package rj.qmce.lite.agent

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rj.qmce.lite.agent.AgentRunStatus.Idle
import rj.qmce.lite.agent.AgentRunStatus.Running
import rj.qmce.lite.agent.AgentRunStatus.WaitingApproval
import rj.qmce.lite.util.QmceLog
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-turn agentic loop (OpenAI tool_calls), mirrors cocacode SubAgentEngine.execute:
 * call model -> if tool_calls, execute each (approval-gated) -> append tool results ->
 * continue until the model returns a plain answer.
 *
 * A new user message supersedes an in-flight run: [cancel] is called first, then a
 * fresh run starts. Run-id generation guards the finally blocks so a cancelled run
 * never clobbers its successor's status or job bookkeeping.
 */
object AgentEngine {

    private const val MAX_TURNS = 8
    private const val MAX_TOOL_CALLS_PER_RESPONSE = 4
    private const val LLM_WAIT_MILLIS = 200_000L
    private const val TOOL_EXEC_TIMEOUT_MILLIS = 30_000L
    private const val EVENT_MONITOR_TIMEOUT_MILLIS = 700_000L // > event_monitor's own 600s cap
    private const val TIMER_TIMEOUT_MILLIS = 6 * 3600_000L + 60_000L
    private const val MAX_TOOL_RESULT_LENGTH = 4_000
    private const val MAX_LLM_RETRIES = 2

    private val client = LlmClient()
    private val runSeq = AtomicLong(0)
    private val lock = Any()
    private var runningJob: Job? = null
    private var activeRunId: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val SYSTEM_PROMPT =
        "你是 Fluoxetine，QQ 手表上的智能助手，一个聪明、自然、贴合语境的 AI。你可以调用工具帮用户完成 QQ 操作（发消息、撤回、群管理、好友审批、计时器、事件监听等）。用中文回答，简洁友好。你的名字是 Fluoxetine，如果用户问你是谁，就介绍自己是 Fluoxetine。"

    /** Start a new run on the subsystem scope. No-op if one is already running. */
    fun start(scope: CoroutineScope) {
        synchronized(lock) {
            if (runningJob?.isActive == true) return
            val runId = runSeq.incrementAndGet()
            activeRunId = runId
            runningJob = scope.launch { run(runId) }
        }
    }

    /** Cancel the current run (new user message supersedes, or logout). */
    fun cancel() {
        val jobToCancel = synchronized(lock) { runningJob?.also { runningJob = null } }
        jobToCancel?.cancel()
        ApprovalController.cancelAll()
    }

    private suspend fun run(runId: Long) {
        val tools = KernelToolRegistry.all()
        AgentSession.setRunStatus(Running)
        QmceLog.d("QMCE-Agent", "run=$runId start tools=${tools.size}")
        try {
            var turn = 0
            while (turn < MAX_TURNS) {
                turn++
                val history = AgentSession.history.value
                val messages = listOf(AgentMessage(role = "system", content = SYSTEM_PROMPT)) + history

                val result = callLlmWithRetry(messages, tools) ?: return
                if (result.error != null) {
                    AgentSession.appendErrorMessage("Agent 请求失败: ${result.error}")
                    return
                }

                val text = result.streamedText
                val toolCalls = result.toolCalls
                AgentSession.addAssistantMessage(text, toolCalls)

                if (toolCalls.isEmpty()) {
                    return // plain answer -> done
                }

                val effectiveCalls = toolCalls.take(MAX_TOOL_CALLS_PER_RESPONSE)
                val dropped = toolCalls.drop(MAX_TOOL_CALLS_PER_RESPONSE)
                for (call in dropped) {
                    AgentSession.addToolResult(
                        toolCallId = call.id,
                        toolName = call.name,
                        result = ToolResult("本轮工具调用数超限，已跳过", isError = true),
                    )
                }
                for (call in effectiveCalls) {
                    val tool = KernelToolRegistry.get(call.name)
                    if (tool == null) {
                        AgentSession.addToolResult(
                            toolCallId = call.id,
                            toolName = call.name,
                            result = ToolResult("未知工具: ${call.name}", isError = true),
                        )
                        continue
                    }
                    if (tool.requiresApproval) {
                        AgentSession.setRunStatus(WaitingApproval)
                    }
                    val decision = ApprovalController.request(tool, call.arguments)
                    if (decision != ApprovalDecision.Allow) {
                        val reason = when (decision) {
                            ApprovalDecision.Allow -> ""
                            ApprovalDecision.Deny -> "用户拒绝执行"
                            ApprovalDecision.Timeout -> "批准超时"
                        }
                        AgentSession.addToolResult(
                            toolCallId = call.id,
                            toolName = call.name,
                            result = ToolResult(reason, isError = true),
                        )
                        AgentSession.setRunStatus(Running)
                        continue
                    }
                    AgentSession.setRunStatus(Running)
                    val execTimeout = when {
                        tool.isEventMonitor -> EVENT_MONITOR_TIMEOUT_MILLIS
                        tool.isTimer -> TIMER_TIMEOUT_MILLIS
                        else -> TOOL_EXEC_TIMEOUT_MILLIS
                    }
                    val execResult = withTimeoutOrNull(execTimeout) {
                        try {
                            tool.execute(call.arguments)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ToolResult(e.message ?: "执行异常", isError = true)
                        }
                    } ?: ToolResult("工具执行超时", isError = true)
                    AgentSession.addToolResult(
                        toolCallId = call.id,
                        toolName = call.name,
                        result = ToolResult(execResult.text.take(MAX_TOOL_RESULT_LENGTH), execResult.isError),
                    )
                }
            }
            AgentSession.appendErrorMessage("已达到最大工具调用轮次，请精简请求。")
        } catch (error: CancellationException) {
            // New message superseded this run; stop silently.
            withContext(Dispatchers.Main.immediate) { AgentSession.finishStreamingReply() }
            throw error
        } catch (error: Exception) {
            QmceLog.w("QMCE-Agent", "run=$runId failed", error)
            withContext(Dispatchers.Main.immediate) { AgentSession.finishStreamingReply() }
            AgentSession.appendErrorMessage("Agent 运行出错: ${error.message}")
        } finally {
            // Only the current run may clear status/job bookkeeping.
            synchronized(lock) {
                if (activeRunId == runId) {
                    AgentSession.setRunStatus(Idle)
                    runningJob = null
                }
            }
        }
    }

    private suspend fun callLlmWithRetry(
        messages: List<AgentMessage>,
        tools: List<Tool>,
    ): LlmOutcome? {
        var attempt = 0
        while (true) {
            val outcome = CompletableDeferred<LlmOutcome>()
            val textBuffer = StringBuilder()
            val request = client.stream(
                messages = messages,
                tools = tools,
                listener = object : LlmClient.Listener {
                    override fun onChunk(text: String) {
                        textBuffer.append(text)
                        // Handler FIFO keeps chunk order when callbacks arrive off-main.
                        mainHandler.post {
                            if (AgentSession.currentStreamingText().isEmpty()) {
                                AgentSession.beginStreamingReply()
                            }
                            AgentSession.appendStreamingChunk(text)
                        }
                    }

                    override fun onComplete(toolCalls: List<AgentToolCall>) {
                        if (outcome.isCompleted) return
                        outcome.complete(
                            LlmOutcome(textBuffer.toString(), toolCalls, retryable = false),
                        )
                    }

                    override fun onError(message: String, retryable: Boolean) {
                        if (outcome.isCompleted) return
                        outcome.complete(
                            LlmOutcome("", emptyList(), error = message, retryable = retryable),
                        )
                    }
                },
            )
            val ctxJob = currentCoroutineContext()[Job]
            ctxJob?.invokeOnCompletion { request.cancel() }

            val result = withTimeoutOrNull(LLM_WAIT_MILLIS) { outcome.await() }
            if (result == null) {
                request.cancel()
                withContext(Dispatchers.Main.immediate) { AgentSession.finishStreamingReply() }
                AgentSession.appendErrorMessage("Agent 响应超时，已取消。")
                return null
            }
            withContext(Dispatchers.Main.immediate) { AgentSession.finishStreamingReply() }

            if (result.error != null && result.retryable && attempt < MAX_LLM_RETRIES) {
                attempt++
                delay(1_000L * attempt)
                continue
            }
            return result
        }
    }

    private data class LlmOutcome(
        val streamedText: String,
        val toolCalls: List<AgentToolCall>,
        val error: String? = null,
        val retryable: Boolean = false,
    )
}
