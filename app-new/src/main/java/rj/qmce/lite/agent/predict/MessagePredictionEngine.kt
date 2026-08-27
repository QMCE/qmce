package rj.qmce.lite.agent.predict

import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.agent.AgentMessage
import rj.qmce.lite.agent.AgentToolCall
import rj.qmce.lite.agent.LlmClient
import rj.qmce.lite.agent.ReadOnlyTool
import rj.qmce.lite.agent.ToolResult
import rj.qmce.lite.agent.kernel.msgRecordToText
import rj.qmce.lite.data.chat.ChatRepository
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.util.QmceLog

/**
 * One-shot message prediction. Runs a mini tool-use loop against the same
 * LlmClient (Fluoxetine persona) WITHOUT touching AgentSession history.
 *
 * Flow: seed with ~20 recent messages -> call LLM -> if it asks for more
 * history, fetch older records via ChatRepository.loadOlder -> repeat (≤3
 * rounds, ≤100 messages / ≤32000 tokens) -> parse a JSON array of 1~3
 * suggestions.
 */
object MessagePredictionEngine {

    private const val TAG = "QMCE-Predict"
    private const val MAX_TURNS = 3
    private const val MAX_SUGGESTIONS = 3
    private const val MAX_COLLECTED_MESSAGES = 100
    private const val TOKEN_BUDGET = 32_000
    private const val INITIAL_COUNT = 20
    private const val LLM_WAIT_MILLIS = 90_000L
    private const val TOOL_EXEC_TIMEOUT_MILLIS = 8_000L

    private val client = LlmClient()

    private class CollectState(
        val peerUid: String,
        val chatType: Int,
        val messages: MutableList<PredictionMessage> = mutableListOf(),
    ) {
        val oldestId: Long get() = messages.firstOrNull()?.msgId ?: 0L
        val oldestTime: Long get() = messages.firstOrNull()?.msgTime ?: 0L

        fun transcript(): String = messages.joinToString("\n") { "${it.sender}: ${it.text}" }

        /** A tool bound to this prediction's history + peer. */
        fun tool(): GetMoreMessagesTool = GetMoreMessagesTool(this)
    }

    /** Per-prediction tool that pulls older history via the shared ChatRepository. */
    private class GetMoreMessagesTool(private val state: CollectState) : ReadOnlyTool(
        name = "get_more_messages",
        description = "获取当前会话更早的历史消息。参数：count（需要的历史消息条数，默认30，最大80）。返回追加的较早消息。",
        inputSchema = mapOf(
            "count" to mapOf("type" to "integer", "description" to "需要的历史消息条数，默认30，最大80"),
        ),
    ) {
        override suspend fun execute(input: Map<String, Any>): ToolResult {
            val count = ((input["count"] as? Number)?.toInt() ?: 30).coerceIn(1, 80)
            val lines = fetchOlder(count)
            if (lines.isEmpty()) {
                return ToolResult("没有更多历史消息可获取")
            }
            return ToolResult("已追加 ${lines.size} 条更早消息：\n${lines.joinToString("\n")}")
        }

        private suspend fun fetchOlder(count: Int): List<String> {
            val runtime = QmceApplication.ensureRuntime() ?: return emptyList()
            val repository = ChatRepository()
            try {
                val connection = repository.connect(runtime, bindRichMedia = false)
                if (connection !is ChatRepository.Connection.Ready) return emptyList()

                val oldest = state.messages.firstOrNull() ?: return emptyList()
                val contact = Contact(state.chatType, state.peerUid, "")
                val deferred = CompletableDeferred<List<com.tencent.qqnt.kernel.nativeinterface.MsgRecord>>()
                val requested = repository.loadOlder(
                    ChatRepository.HistoryRequest(
                        contact = contact,
                        anchorMessageId = oldest.msgId,
                        anchorMessageTime = oldest.msgTime,
                        count = count,
                    ),
                ) { result, _, _, records ->
                    deferred.complete(if (result == 0) records.orEmpty() else emptyList())
                }
                if (!requested) return emptyList()
                val records = withTimeoutOrNull(5_000) { deferred.await() } ?: return emptyList()
                if (records.isEmpty()) return emptyList()

                val lines = mutableListOf<String>()
                for (rec in records) {
                    if (state.messages.size >= MAX_COLLECTED_MESSAGES) break
                    val sender = rec.sendNickName?.takeIf { it.isNotBlank() }
                        ?: rec.senderUin.takeIf { it > 0L }?.toString()
                        ?: "未知"
                    val text = msgRecordToText(rec)
                    if (text.isBlank()) continue
                    state.messages.add(0, PredictionMessage(sender, text, rec.msgId, rec.msgTime))
                    lines.add("$sender: $text")
                }
                return lines
            } finally {
                repository.close()
            }
        }
    }

    suspend fun predict(
        peerUid: String,
        chatType: Int,
        initial: List<PredictionMessage>,
    ): PredictionUiState {
        val collected = CollectState(peerUid, chatType)
        collected.messages.addAll(initial.take(INITIAL_COUNT))
        val selfName = resolveSelfName()
        QmceLog.d(
            TAG,
            "predict start peer=$peerUid chatType=$chatType seed=${collected.messages.size} self=$selfName",
        )

        try {
            var turn = 0
            while (turn < MAX_TURNS) {
                turn++
                val transcript = collected.transcript()
                if (estimateTokens(transcript) > TOKEN_BUDGET) {
                    return PredictionUiState.Error("上下文过长，无法预测")
                }

                val outcome = CompletableDeferred<LlmOutcome>()
                val textBuffer = StringBuilder()
                val request = client.stream(
                    messages = listOf(
                        AgentMessage(role = "system", content = systemPromptFor(selfName)),
                        AgentMessage(role = "user", content = transcript),
                    ),
                    tools = listOf(collected.tool()),
                    listener = object : LlmClient.Listener {
                        override fun onChunk(text: String) {
                            textBuffer.append(text)
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
                    return PredictionUiState.Error("预测超时，请重试")
                }
                if (result.error != null) {
                    return PredictionUiState.Error(result.error)
                }

                val toolCall = result.toolCalls.firstOrNull()
                if (toolCall != null && toolCall.name == "get_more_messages") {
                    val more = withTimeoutOrNull(TOOL_EXEC_TIMEOUT_MILLIS) {
                        try {
                            collected.tool().execute(toolCall.arguments)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ToolResult(e.message ?: "执行异常", isError = true)
                        }
                    } ?: ToolResult("获取历史超时", isError = true)
                    if (!more.isError && more.text.contains("已追加")) {
                        QmceLog.d(
                            TAG,
                            "prediction: get_more_messages total=${collected.messages.size}",
                        )
                        continue
                    }
                    // No more history / error; fall through to parse whatever text exists.
                }

                val suggestions = parseSuggestions(result.streamedText)
                return if (suggestions.isEmpty()) {
                    PredictionUiState.Error("预测结果解析失败")
                } else {
                    PredictionUiState.Ready(suggestions)
                }
            }
            return PredictionUiState.Error("已达到预测轮次上限")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            QmceLog.w(TAG, "predict failed", error)
            return PredictionUiState.Error(error.message ?: "预测失败")
        }
    }

    /**
     * Rough token estimate: every CJK char ≈1 token, ASCII runs ≈1 token per 4 chars.
     * Good enough to bound the context within the 32768 budget.
     */
    fun estimateTokens(s: String): Int {
        if (s.isEmpty()) return 0
        var tokens = 0
        var asciiRun = 0
        for (ch in s) {
            if (ch.code <= 0x2E7F) {
                asciiRun++
                if (asciiRun >= 4) {
                    tokens++
                    asciiRun = 0
                }
            } else {
                tokens++
                asciiRun = 0
            }
        }
        if (asciiRun > 0) tokens++
        return tokens
    }

    /** Parse the LLM's output as a JSON string array (strip fences). */
    fun parseSuggestions(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        var text = raw.trim()
        // Strip ```json / ``` fences.
        text = text.replace(Regex("```(?:json)?\\s*", RegexOption.IGNORE_CASE), "").trim()
        if (text.endsWith("```")) text = text.dropLast(3).trim()

        // Extract the first [...] block.
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val jsonText = text.substring(start, end + 1)
        val arr = runCatching { JSONArray(jsonText) }.getOrNull() ?: return emptyList()

        val result = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val item = arr.optString(i).trim()
            if (item.isBlank() || item in seen) continue
            seen.add(item)
            result.add(item)
            if (result.size >= MAX_SUGGESTIONS) break
        }
        return result
    }

    private data class LlmOutcome(
        val streamedText: String,
        val toolCalls: List<AgentToolCall>,
        val error: String? = null,
    )

    /** Best-effort current logged-in user's display name (fall back to QQ number). */
    private fun resolveSelfName(): String {
        val runtime = runCatching { QmceApplication.ensureRuntime() }.getOrNull() ?: return "用户"
        val uin = runCatching { runtime.currentUin.orEmpty() }.getOrDefault("")
        val nick = runCatching {
            KernelBridge.getSelfProfileService()?.getCurrentAccountNickName(uin)
        }.getOrNull()?.trim()
        return nick?.takeIf { it.isNotBlank() } ?: uin.ifBlank { "用户" }
    }

    private fun systemPromptFor(selfName: String): String =
        "你是智能回复助手，现在代替 QQ 用户「$selfName」预测其接下来最可能发送的消息。基于下面的聊天记录，预测该用户最可能发送的 1~3 条消息。要求：口语自然、贴合上下文与该用户的说话风格；群聊考虑回复最新话题；只输出一个 JSON 字符串数组（如 [\"好的，马上到\",\"哈哈哈\",\"晚上一起吃饭吗？\"]），不要输出任何其他文字；若上下文不足可调用 get_more_messages 获取更多历史。"
}
