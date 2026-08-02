package rj.qmce.lite.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
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

    private val client = LlmClient()
    private val runSeq = AtomicLong(0)
    private val lock = Any()
    private var runningJob: Job? = null
    private var activeRunId: Long = 0L

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

                val outcome = CompletableDeferred<LlmOutcome>()
                val textBuffer = StringBuilder()
                val request = client.stream(
                    messages = history,
                    tools = tools,
                    listener = object : LlmClient.Listener {
                        override fun onChunk(text: String) {
                            textBuffer.append(text)
                            if (AgentSession.currentStreamingText().isEmpty()) {
                                AgentSession.beginStreamingReply()
                            }
                            AgentSession.appendStreamingChunk(text)
                        }

                        override fun onComplete(toolCalls: List<AgentToolCall>) {
                            if (outcome.isCompleted) return
                            outcome.complete(LlmOutcome(textBuffer.toString(), toolCalls))
                        }

                        override fun onError(message: String, retryable: Boolean) {
                            if (outcome.isCompleted) return
                            outcome.complete(LlmOutcome("", emptyList(), error = message))
                        }
                    },
                )
                val ctxJob = currentCoroutineContext()[Job]
                ctxJob?.invokeOnCompletion { request.cancel() }

                val result = withTimeoutOrNull(LLM_WAIT_MILLIS) { outcome.await() }
                if (result == null) {
                    request.cancel()
                    AgentSession.finishStreamingReply()
                    AgentSession.appendErrorMessage("Agent 响应超时，已取消。")
                    return
                }
                AgentSession.finishStreamingReply()

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
                    AgentSession.setRunStatus(WaitingApproval)
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
            AgentSession.finishStreamingReply()
            throw error
        } catch (error: Exception) {
            QmceLog.w("QMCE-Agent", "run=$runId failed", error)
            AgentSession.finishStreamingReply()
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

    private data class LlmOutcome(
        val streamedText: String,
        val toolCalls: List<AgentToolCall>,
        val error: String? = null,
    )
}
