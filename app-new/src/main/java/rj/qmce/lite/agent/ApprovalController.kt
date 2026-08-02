package rj.qmce.lite.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

sealed class ApprovalDecision {
    data object Allow : ApprovalDecision()
    data object Deny : ApprovalDecision()
    data object Timeout : ApprovalDecision()
}

data class ApprovalRequest(
    val id: String,
    val toolName: String,
    val input: Map<String, Any>,
    val summary: String,
    val createdAt: Long,
)

/**
 * Blocks write tools until the user approves or denies them.
 *
 * Mirrors cocacode's PermissionRule ALLOW/DENY/ASK semantics with a default
 * DENY->ASK policy: read-only tools never reach here; write tools queue a
 * request and suspend until [decide] resolves it (or a timeout denies it).
 */
object ApprovalController {

    private val seq = AtomicLong(0)
    private val requests = mutableMapOf<String, ApprovalRequest>()
    private val completers = mutableMapOf<String, CompletableDeferred<ApprovalDecision>>()
    private val lock = Any()

    private val _pending = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val pending: StateFlow<List<ApprovalRequest>> = _pending.asStateFlow()

    val pendingCount: Int get() = _pending.value.size

    private const val APPROVAL_TIMEOUT_MILLIS = 120_000L

    /** Suspend until the user decides. Read-only tools call [runDirect] instead. */
    suspend fun request(tool: Tool, input: Map<String, Any>): ApprovalDecision {
        if (!tool.requiresApproval) return ApprovalDecision.Allow

        val id = "approve-${seq.incrementAndGet()}"
        val req = ApprovalRequest(
            id = id,
            toolName = tool.name,
            input = input,
            summary = summarize(tool, input),
            createdAt = System.currentTimeMillis(),
        )
        val completer = CompletableDeferred<ApprovalDecision>()
        synchronized(lock) {
            requests[id] = req
            completers[id] = completer
            _pending.value = _pending.value + req
        }
        return try {
            withTimeoutOrNull(APPROVAL_TIMEOUT_MILLIS) { completer.await() }
                ?: ApprovalDecision.Timeout
        } finally {
            synchronized(lock) {
                requests.remove(id)
                completers.remove(id)
                _pending.value = _pending.value.filterNot { it.id == id }
            }
        }
    }

    fun decide(id: String, allow: Boolean) {
        synchronized(lock) {
            val completer = completers[id] ?: return
            completer.complete(if (allow) ApprovalDecision.Allow else ApprovalDecision.Deny)
        }
    }

    /** Resolve every in-flight request with Deny (logout / shutdown). */
    fun cancelAll() {
        synchronized(lock) {
            completers.values.forEach { it.complete(ApprovalDecision.Deny) }
            completers.clear()
            requests.clear()
            _pending.value = emptyList()
        }
    }

    private fun summarize(tool: Tool, input: Map<String, Any>): String {
        val sb = StringBuilder(tool.name)
        sb.append("(")
        val entries = input.entries.take(6).joinToString(", ") { (k, v) ->
            val value = v.toString().take(40)
            "$k=$value"
        }
        sb.append(entries)
        sb.append(")")
        return sb.toString()
    }
}
