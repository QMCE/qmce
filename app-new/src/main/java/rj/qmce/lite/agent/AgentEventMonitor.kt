package rj.qmce.lite.agent

import com.tencent.qqnt.kernel.api.IBuddyService
import com.tencent.qqnt.kernel.api.IGroupService
import com.tencent.qqnt.kernel.api.IMsgService
import com.tencent.qqnt.kernel.nativeinterface.BuddyReqInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyMsg
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyMsgType
import com.tencent.qqnt.kernel.nativeinterface.IKernelBuddyListener
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupListener
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgListener
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rj.qmce.lite.agent.kernel.msgRecordToText
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import rj.qmce.lite.util.QmceLog
import kotlin.coroutines.resume

/** Event kinds the monitor can listen for. */
enum class AgentEventKind(val key: String) {
    MESSAGE("message"),
    FRIEND_REQUEST("friend_request"),
    GROUP_NOTICE("group_notice"),
    ;

    companion object {
        fun fromKey(key: String?): AgentEventKind? =
            entries.firstOrNull { it.key == key?.trim()?.lowercase() }
    }
}

/** A concrete monitor registration: which events, which filters, and a completion. */
private class MonitorEntry(
    val kinds: Set<AgentEventKind>,
    val peerUid: String?,
    val chatType: Int?,
    val description: String,
    val onTrigger: (String) -> Unit,
)

/**
 * Event bus + multi-kind event-monitor tool.
 *
 * Registers three kernel listeners once (message / buddy-request / group-notice)
 * and fans events out to active [MonitorEntry]s. The `event_monitor` tool
 * suspends until a matching event arrives (or a timeout), then returns a
 * description so the engine can continue the conversation.
 */
object AgentEventBus {

    private const val TAG = "QMCE-AgentEvent"

    private val monitors = mutableListOf<MonitorEntry>()
    private val lock = Any()

    // Registered kernel services + proxies (for clean removal on logout).
    @Volatile private var msgService: IMsgService? = null
    @Volatile private var buddyService: IBuddyService? = null
    @Volatile private var groupService: IGroupService? = null
    @Volatile private var msgProxy: IKernelMsgListener? = null
    @Volatile private var buddyProxy: IKernelBuddyListener? = null
    @Volatile private var groupProxy: IKernelGroupListener? = null
    @Volatile private var registered = false

    /** Ensure all three kernel listeners are installed. */
    fun ensure() {
        synchronized(lock) {
            if (registered) return
            registerMsgListener()
            registerBuddyListener()
            registerGroupListener()
            registered = true
        }
    }

    fun stop() {
        synchronized(lock) {
            runCatching {
                val ms = msgService
                val mp = msgProxy
                if (ms != null && mp != null) SdkCompat.removeMsgListener(ms, mp)
            }.onFailure { QmceLog.w(TAG, "remove msg listener failed", it) }
            runCatching {
                val bs = buddyService
                val bp = buddyProxy
                if (bs != null && bp != null) SdkCompat.removeBuddyListener(bs, bp)
            }.onFailure { QmceLog.w(TAG, "remove buddy listener failed", it) }
            runCatching {
                val gs = groupService
                val gp = groupProxy
                if (gs != null && gp != null) SdkCompat.removeGroupListener(gs, gp)
            }.onFailure { QmceLog.w(TAG, "remove group listener failed", it) }
            msgService = null
            buddyService = null
            groupService = null
            msgProxy = null
            buddyProxy = null
            groupProxy = null
            registered = false
            monitors.clear()
        }
    }

    private fun registerMsgListener() {
        val service = KernelBridge.getMsgService() ?: return
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            IKernelMsgListener::class.java.classLoader,
            arrayOf(IKernelMsgListener::class.java),
        ) { instance, method, args ->
            when (method.name) {
                "onRecvMsg" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args?.getOrNull(0) as? ArrayList<MsgRecord>)?.forEach { dispatchMessage(it) }
                    null
                }

                "onAddSendMsg" -> {
                    (args?.getOrNull(0) as? MsgRecord)?.let { dispatchMessage(it) }
                    null
                }

                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.getOrNull(0)
                "toString" -> "QMCE-AgentEventMsg"
                else -> null
            }
        } as IKernelMsgListener
        runCatching { SdkCompat.addMsgListener(service, proxy) }.onFailure {
            QmceLog.w(TAG, "register msg listener failed", it)
        }
        msgService = service
        msgProxy = proxy
    }

    private fun registerBuddyListener() {
        val service = KernelBridge.getBuddyService() ?: return
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            IKernelBuddyListener::class.java.classLoader,
            arrayOf(IKernelBuddyListener::class.java),
        ) { instance, method, args ->
            when (method.name) {
                "onBuddyReqChange" -> {
                    val info = args?.getOrNull(0) as? BuddyReqInfo
                    dispatchBuddyRequests(info)
                    null
                }

                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.getOrNull(0)
                "toString" -> "QMCE-AgentEventBuddy"
                else -> null
            }
        } as IKernelBuddyListener
        runCatching { SdkCompat.addBuddyListener(service, proxy) }.onFailure {
            QmceLog.w(TAG, "register buddy listener failed", it)
        }
        buddyService = service
        buddyProxy = proxy
    }

    private fun registerGroupListener() {
        val service = KernelBridge.getGroupService() ?: return
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            IKernelGroupListener::class.java.classLoader,
            arrayOf(IKernelGroupListener::class.java),
        ) { instance, method, args ->
            when (method.name) {
                "onGroupNotifiesUpdated" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args?.getOrNull(1) as? ArrayList<GroupNotifyMsg>)?.forEach { dispatchGroupNotice(it) }
                    null
                }

                "onGroupNotifiesUpdatedV2" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args?.getOrNull(2) as? ArrayList<GroupNotifyMsg>)?.forEach { dispatchGroupNotice(it) }
                    null
                }

                "onGroupSingleScreenNotifies" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args?.getOrNull(2) as? ArrayList<GroupNotifyMsg>)?.forEach { dispatchGroupNotice(it) }
                    null
                }

                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === args?.getOrNull(0)
                "toString" -> "QMCE-AgentEventGroup"
                else -> null
            }
        } as IKernelGroupListener
        runCatching { SdkCompat.addGroupListener(service, proxy) }.onFailure {
            QmceLog.w(TAG, "register group listener failed", it)
        }
        groupService = service
        groupProxy = proxy
    }

    // ---- dispatch ----

    private fun dispatchMessage(record: MsgRecord) {
        val text = msgRecordToText(record)
        val toRemove = mutableListOf<MonitorEntry>()
        synchronized(lock) {
            monitors.toList().forEach { m ->
                if (AgentEventKind.MESSAGE in m.kinds && matchesMessage(m, record)) {
                    runCatching {
                        m.onTrigger(
                            "事件触发（${m.description}）：来自 ${record.peerUid ?: "未知"} 的新消息：$text",
                        )
                    }
                    toRemove.add(m)
                }
            }
            toRemove.forEach(monitors::remove)
        }
    }

    private fun dispatchBuddyRequests(info: BuddyReqInfo?) {
        val pending = info?.buddyReqs.orEmpty()
            .filter { it.friendUid?.isNotBlank() == true && !it.isDecide && !it.isInitiator }
        if (pending.isEmpty()) return
        val first = pending.first()
        val text = "收到好友申请：${first.friendNick.orEmpty().ifBlank { first.friendUid }} " +
            "（${first.extWords.orEmpty().take(30)}）"
        fire(AgentEventKind.FRIEND_REQUEST, text)
    }

    private fun dispatchGroupNotice(msg: GroupNotifyMsg) {
        val group = msg.group
        val groupName = group?.groupName?.takeIf { it.isNotBlank() } ?: group?.groupCode?.toString() ?: "未知群"
        val text = buildString {
            append("收到群系统通知（$groupName）：")
            append(msg.showModuleMsg?.takeIf { it.isNotBlank() } ?: groupNoticeTitle(msg))
        }
        fire(AgentEventKind.GROUP_NOTICE, text)
    }

    private fun fire(kind: AgentEventKind, text: String) {
        val toRemove = mutableListOf<MonitorEntry>()
        synchronized(lock) {
            monitors.toList().forEach { m ->
                if (kind in m.kinds) {
                    runCatching { m.onTrigger("事件触发（${m.description}）：$text") }
                    toRemove.add(m)
                }
            }
            toRemove.forEach(monitors::remove)
        }
    }

    private fun matchesMessage(m: MonitorEntry, record: MsgRecord): Boolean {
        if (m.peerUid != null && record.peerUid != m.peerUid) return false
        if (m.chatType != null && record.chatType != m.chatType) return false
        return true
    }

    private fun groupNoticeTitle(msg: GroupNotifyMsg): String = when (msg.type) {
        GroupNotifyMsgType.REQUESTJOINNEEDADMINISTRATORPASS -> "有人申请加群"
        GroupNotifyMsgType.INVITEDNEEDADMINISTRATORPASS -> "有人邀请加群"
        GroupNotifyMsgType.INVITEDBYMEMBER -> "成员邀请通知"
        GroupNotifyMsgType.AGREEDTOJOINDIRECT -> "已同意加入"
        GroupNotifyMsgType.REFUSEDBYADMINISTRATOR -> "申请被拒绝"
        else -> "群系统通知"
    }

    // ---- public API ----

    /**
     * Suspend until an event matching [kinds] arrives or the timeout elapses.
     * [peerUid] / [chatType] only apply to MESSAGE-kind events.
     */
    suspend fun waitForEvent(
        kinds: Set<AgentEventKind>,
        peerUid: String?,
        chatType: Int?,
        timeoutMillis: Long,
        description: String,
    ): ToolResult {
        ensure()
        var entry: MonitorEntry? = null
        val result = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { cont ->
                val e = MonitorEntry(
                    kinds = kinds,
                    peerUid = peerUid,
                    chatType = chatType,
                    description = description,
                    onTrigger = { text ->
                        if (!cont.isCompleted) cont.resume(ToolResult(text))
                    },
                )
                entry = e
                synchronized(lock) { monitors.add(e) }
                cont.invokeOnCancellation {
                    synchronized(lock) { monitors.remove(e) }
                }
            }
        }
        if (result == null) {
            entry?.let { synchronized(lock) { monitors.remove(it) } }
            return ToolResult("等待事件超时：$description", isError = false)
        }
        return result
    }
}

/**
 * The `event_monitor` tool exposed to the Agent.
 *
 * event_type: "message"（新消息，可带 peerUid/chatType 过滤）|
 *             "friend_request"（好友申请）|
 *             "group_notice"（群系统通知）
 */
class EventMonitorTool : Tool(
    name = "event_monitor",
    description = "监听事件并在事件发生时返回，Agent 会在事件到达后继续处理。event_type 可选：\"message\"（新消息，可用 peerUid 只监听指定会话、chatType 指定 1=私聊/2=群聊）、\"friend_request\"（好友申请）、\"group_notice\"（群系统通知）。timeout_seconds 为等待秒数（默认300，最大600）。该工具会挂起直到事件发生或超时。",
    inputSchema = mapOf(
        "event_type" to mapOf(
            "type" to "string",
            "description" to "事件类型：message / friend_request / group_notice，默认 message",
        ),
        "peerUid" to mapOf("type" to "string", "description" to "只监听指定会话（message 事件用，可选）"),
        "chatType" to mapOf("type" to "integer", "description" to "会话类型 1=私聊 2=群聊（message 事件用，可选）"),
        "timeout_seconds" to mapOf("type" to "integer", "description" to "等待秒数，默认300，最大600"),
        "description" to mapOf("type" to "string", "description" to "事件描述"),
    ),
    requiresApproval = true,
    isEventMonitor = true,
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val kind = AgentEventKind.fromKey((input["event_type"] as? String))
            ?: AgentEventKind.MESSAGE
        val peerUid = (input["peerUid"] as? String)?.takeIf { it.isNotBlank() }
        val chatType = (input["chatType"] as? Number)?.toInt()
        val timeoutSeconds = ((input["timeout_seconds"] as? Number)?.toInt() ?: 300).coerceIn(1, 600)
        val description = (input["description"] as? String)?.takeIf { it.isNotBlank() } ?: "事件"
        return AgentEventBus.waitForEvent(
            kinds = setOf(kind),
            peerUid = peerUid,
            chatType = chatType,
            timeoutMillis = timeoutSeconds * 1000L,
            description = description,
        )
    }
}
