package rj.qmce.lite.agent

import com.tencent.qqnt.kernel.api.IMsgService
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgListener
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rj.qmce.lite.agent.kernel.err
import rj.qmce.lite.agent.kernel.msgRecordToText
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import rj.qmce.lite.util.QmceLog
import kotlin.coroutines.resume

/**
 * Event bus + event-monitor tool.
 *
 * [AgentEventBus] receives kernel message callbacks and fans them out to
 * registered [Listener]s. The `event_monitor` tool suspends until a matching
 * message arrives (or a timeout), then returns a description so the engine can
 * continue the conversation.
 */
object AgentEventBus {

    private const val TAG = "QMCE-AgentEvent"

    fun interface Listener {
        /** Return true when the monitor is done (consume + wake). */
        fun onMessage(record: MsgRecord, text: String): Boolean
    }

    private val listeners = mutableListOf<Listener>()
    private val lock = Any()

    @Volatile
    private var msgService: IMsgService? = null

    @Volatile
    private var registered = false

    @Volatile
    private var proxyListener: IKernelMsgListener? = null

    /** Ensure a kernel message listener is installed that feeds the bus. */
    fun ensure() {
        synchronized(lock) {
            if (registered) return
            val service = KernelBridge.getMsgService() ?: return
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                IKernelMsgListener::class.java.classLoader,
                arrayOf(IKernelMsgListener::class.java),
            ) { instance, method, args ->
                when (method.name) {
                    "onRecvMsg" -> {
                        @Suppress("UNCHECKED_CAST")
                        (args?.getOrNull(0) as? ArrayList<MsgRecord>)?.forEach { dispatch(it) }
                        null
                    }

                    "onAddSendMsg" -> {
                        (args?.getOrNull(0) as? MsgRecord)?.let { dispatch(it) }
                        null
                    }

                    "hashCode" -> System.identityHashCode(instance)
                    "equals" -> instance === args?.getOrNull(0)
                    "toString" -> "QMCE-AgentEventBus"
                    else -> null
                }
            } as IKernelMsgListener
            runCatching { SdkCompat.addMsgListener(service, proxy) }.onFailure {
                QmceLog.w(TAG, "register msg listener failed", it)
            }
            msgService = service
            proxyListener = proxy
            registered = true
        }
    }

    fun stop() {
        synchronized(lock) {
            val service = msgService
            val proxy = proxyListener
            if (registered && service != null && proxy != null) {
                runCatching { SdkCompat.removeMsgListener(service, proxy) }.onFailure {
                    QmceLog.w(TAG, "remove msg listener failed", it)
                }
            }
            msgService = null
            proxyListener = null
            registered = false
            listeners.clear()
        }
    }

    private fun dispatch(record: MsgRecord) {
        val text = msgRecordToText(record)
        val toRemove = mutableListOf<Listener>()
        synchronized(lock) {
            listeners.toList().forEach { listener ->
                val done = runCatching { listener.onMessage(record, text) }.getOrDefault(false)
                if (done) toRemove.add(listener)
            }
            toRemove.forEach(listeners::remove)
        }
    }

    private fun register(listener: Listener) {
        synchronized(lock) { listeners.add(listener) }
    }

    private fun unregister(listener: Listener) {
        synchronized(lock) { listeners.remove(listener) }
    }

    /**
     * Suspend until a matching message arrives or the timeout elapses.
     */
    suspend fun waitForEvent(
        peerUid: String?,
        chatType: Int?,
        timeoutMillis: Long,
        description: String,
    ): ToolResult {
        ensure()
        var waiter: Listener? = null
        return withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                waiter = Listener { record, _ ->
                    if (listenerMatches(record, peerUid, chatType)) {
                        if (!cont.isCompleted) {
                            cont.resume(
                                ToolResult(
                                    "事件触发（${description}）：来自 ${record.peerUid ?: "未知"} 的新消息",
                                ),
                            )
                        }
                        true
                    } else {
                        false
                    }
                }
                register(waiter!!)
                cont.invokeOnCancellation {
                    waiter?.let { unregister(it) }
                }
            }
        }?.also { } ?: ToolResult("等待事件超时：$description", isError = false)
    }

    private fun listenerMatches(record: MsgRecord, peerUid: String?, chatType: Int?): Boolean {
        if (peerUid != null && record.peerUid != peerUid) return false
        if (chatType != null && record.chatType != chatType) return false
        return true
    }
}

/**
 * The `event_monitor` tool exposed to the Agent.
 */
class EventMonitorTool : Tool(
    name = "event_monitor",
    description = "监听事件并在事件发生时返回。当前支持：新消息到达。参数：peerUid（可选，只监听指定会话）、chatType（可选，1=私聊，2=群聊）、timeout_seconds（等待秒数，默认300，最大600）、description（事件描述）。该工具会挂起直到事件发生或超时，事件发生后 Agent 继续处理。",
    inputSchema = mapOf(
        "peerUid" to mapOf("type" to "string", "description" to "要监听的会话 UID（可选）"),
        "chatType" to mapOf("type" to "integer", "description" to "会话类型：1=私聊，2=群聊（可选）"),
        "timeout_seconds" to mapOf("type" to "integer", "description" to "等待秒数，默认300，最大600"),
        "description" to mapOf("type" to "string", "description" to "事件描述"),
    ),
    requiresApproval = true,
    isEventMonitor = true,
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val peerUid = (input["peerUid"] as? String)?.takeIf { it.isNotBlank() }
        val chatType = (input["chatType"] as? Number)?.toInt()
        val timeoutSeconds = ((input["timeout_seconds"] as? Number)?.toInt() ?: 300).coerceIn(1, 600)
        val description = (input["description"] as? String)?.takeIf { it.isNotBlank() } ?: "事件"
        return AgentEventBus.waitForEvent(
            peerUid = peerUid,
            chatType = chatType,
            timeoutMillis = timeoutSeconds * 1000L,
            description = description,
        )
    }
}
