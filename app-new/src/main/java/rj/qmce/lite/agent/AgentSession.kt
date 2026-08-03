package rj.qmce.lite.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Chat-message block sent to the LLM (OpenAI protocol). */
data class AgentMessage(
    val role: String, // user | assistant | tool | system
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<AgentToolCall> = emptyList(),
    val name: String? = null,
)

/** A tool invocation the assistant requested in a single message. */
data class AgentToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>,
)

/** A message rendered in the Agent chat screen. */
data class AgentUiMsg(
    val stableKey: String,
    val isSelf: Boolean,
    val text: String,
    val time: Long,
    val streaming: Boolean = false,
    val isSystem: Boolean = false,
)

enum class AgentRunStatus { Idle, Running, WaitingApproval }

/** Summary shown in the chat list for the pseudo contact. */
data class AgentSessionSummary(
    val lastText: String,
    val lastTimeMillis: Long,
    val unreadCount: Int,
    val busy: Boolean,
    val pendingApprovalCount: Int,
)

data class AgentPersistSnapshot(
    val history: List<AgentMessage>,
    val uiMessages: List<AgentUiMsg>,
    val lastText: String,
    val lastActiveMillis: Long,
    val unreadCount: Int,
    val markedRead: Boolean,
)

/**
 * The single Agent conversation: LLM history + UI messages + run state.
 * The pseudo contact (chatType=100) renders from [uiMessages].
 */
object AgentSession {

    const val PEER_UID = "agent"
    const val CHAT_TYPE = 100
    const val PEER_NAME = "Fluoxetine"
    const val PEER_UIN = 0L

    private const val MAX_HISTORY = 200

    private val msgSeq = AtomicLong(0)

    private val _history = MutableStateFlow<List<AgentMessage>>(emptyList())
    val history: StateFlow<List<AgentMessage>> = _history.asStateFlow()

    private val _uiMessages = MutableStateFlow<List<AgentUiMsg>>(emptyList())
    val uiMessages: StateFlow<List<AgentUiMsg>> = _uiMessages.asStateFlow()

    private val _runStatus = MutableStateFlow(AgentRunStatus.Idle)
    val runStatus: StateFlow<AgentRunStatus> = _runStatus.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _lastText = MutableStateFlow("")
    val lastText: StateFlow<String> = _lastText.asStateFlow()

    private val _lastActiveMillis = MutableStateFlow(0L)
    val lastActiveMillis: StateFlow<Long> = _lastActiveMillis.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    @Volatile
    private var markedRead = true

    @Volatile
    private var inChat = false

    fun summary(): AgentSessionSummary = AgentSessionSummary(
        lastText = _lastText.value,
        lastTimeMillis = _lastActiveMillis.value,
        unreadCount = if (markedRead) 0 else _unreadCount.value,
        busy = _busy.value,
        pendingApprovalCount = ApprovalController.pendingCount,
    )

    fun snapshotForPersist(): AgentPersistSnapshot = AgentPersistSnapshot(
        history = _history.value,
        uiMessages = _uiMessages.value.filterNot { it.streaming },
        lastText = _lastText.value,
        lastActiveMillis = _lastActiveMillis.value,
        unreadCount = _unreadCount.value,
        markedRead = markedRead,
    )

    fun restore(
        history: List<AgentMessage>,
        uiMessages: List<AgentUiMsg>,
        lastText: String,
        lastActiveMillis: Long,
        unreadCount: Int,
        markedRead: Boolean,
    ) {
        _history.value = history
        _uiMessages.value = uiMessages.filterNot { it.streaming }
        _runStatus.value = AgentRunStatus.Idle
        _busy.value = false
        _unreadCount.value = unreadCount
        _lastText.value = lastText
        _lastActiveMillis.value = lastActiveMillis
        _streamingText.value = ""
        this.markedRead = markedRead
        val maxSeq = uiMessages.mapNotNull {
            it.stableKey.substringAfterLast('-').toLongOrNull()
        }.maxOrNull() ?: 0L
        msgSeq.set(maxSeq)
    }

    fun reset() {
        _history.value = emptyList()
        _uiMessages.value = emptyList()
        _runStatus.value = AgentRunStatus.Idle
        _busy.value = false
        _unreadCount.value = 0
        _lastText.value = ""
        _lastActiveMillis.value = 0L
        _streamingText.value = ""
        markedRead = true
        inChat = false
        msgSeq.set(0L)
    }

    fun setInChat(value: Boolean) {
        inChat = value
        if (value) markRead()
    }

    fun markRead() {
        markedRead = true
        _unreadCount.value = 0
        AgentSessionStore.schedulePersist()
    }

    /** User sends a message from the chat input. */
    fun addUserMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        appendHistory(role = "user", content = trimmed)
        appendUi(isSelf = true, text = trimmed)
    }

    /** Append a tool-call result back into the history (role=tool). */
    fun addToolResult(toolCallId: String, toolName: String, result: ToolResult) {
        val content = if (result.isError) "Error: ${result.text}" else result.text
        _history.value = _history.value + AgentMessage(
            role = "tool",
            content = content,
            toolCallId = toolCallId,
            name = toolName,
        )
        trimHistory()
        AgentSessionStore.schedulePersist()
    }

    /** Append a complete assistant message (text and/or tool calls). */
    fun addAssistantMessage(text: String, toolCalls: List<AgentToolCall>) {
        if (text.isBlank() && toolCalls.isEmpty()) return
        _history.value = _history.value + AgentMessage(
            role = "assistant",
            content = text,
            toolCalls = toolCalls,
        )
        trimHistory()
        if (text.isNotBlank()) {
            appendUi(isSelf = false, text = text)
            if (!inChat) {
                markedRead = false
                _unreadCount.value = _unreadCount.value + 1
            }
        }
        _lastText.value = text.take(60).ifBlank { if (toolCalls.isNotEmpty()) "(调用工具中…)" else _lastText.value }
        _lastActiveMillis.value = System.currentTimeMillis()
        AgentSessionStore.schedulePersist()
    }

    /** Append an assistant error note (timeout, engine failure). */
    fun appendErrorMessage(text: String) {
        if (text.isBlank()) return
        appendUi(isSelf = false, text = text, isSystem = true)
        _lastText.value = text.take(60)
        _lastActiveMillis.value = System.currentTimeMillis()
        if (!inChat) {
            markedRead = false
            _unreadCount.value = _unreadCount.value + 1
        }
        AgentSessionStore.schedulePersist()
    }

    fun setRunStatus(status: AgentRunStatus) {
        _runStatus.value = status
        _busy.value = status != AgentRunStatus.Idle
    }

    // ---- streaming assistant reply ----

    fun beginStreamingReply() {
        _streamingText.value = ""
        _uiMessages.value = _uiMessages.value + AgentUiMsg(
            stableKey = "agent-streaming-${msgSeq.incrementAndGet()}",
            isSelf = false,
            text = "",
            time = System.currentTimeMillis(),
            streaming = true,
        )
    }

    fun appendStreamingChunk(chunk: String) {
        _streamingText.value = _streamingText.value + chunk
        val text = _streamingText.value
        _uiMessages.value = _uiMessages.value.map { if (it.streaming) it.copy(text = text) else it }
    }

    fun currentStreamingText(): String = _streamingText.value

    /** Remove the transient streaming bubble; the engine appends the final message. */
    fun finishStreamingReply() {
        _streamingText.value = ""
        _uiMessages.value = _uiMessages.value.filterNot { it.streaming }
    }

    private fun appendHistory(role: String, content: String) {
        _history.value = _history.value + AgentMessage(role = role, content = content)
        trimHistory()
        AgentSessionStore.schedulePersist()
    }

    private fun appendUi(isSelf: Boolean, text: String, isSystem: Boolean = false) {
        _uiMessages.value = _uiMessages.value + AgentUiMsg(
            stableKey = "agent-${if (isSelf) "u" else "a"}-${msgSeq.incrementAndGet()}",
            isSelf = isSelf,
            text = text,
            time = System.currentTimeMillis(),
            isSystem = isSystem,
        )
        AgentSessionStore.schedulePersist()
    }

    /**
     * Trim history to [MAX_HISTORY] while preserving assistant.tool_calls + matching
     * tool-result pairs (never leave a half turn at the start of the window).
     */
    private fun trimHistory() {
        val current = _history.value
        if (current.size <= MAX_HISTORY) return
        var start = current.size - MAX_HISTORY
        while (start > 0 && start < current.size && current[start].role == "tool") {
            start--
        }
        // If we landed on an assistant with tool_calls, keep it; if mid-pair, step back.
        if (start > 0 && current[start].role == "tool") {
            // Find preceding assistant
            var i = start
            while (i > 0 && current[i].role == "tool") i--
            start = i
        }
        _history.value = current.drop(start)
    }
}
