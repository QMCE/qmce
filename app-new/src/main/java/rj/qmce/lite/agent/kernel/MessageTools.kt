package rj.qmce.lite.agent.kernel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.agent.ReadOnlyTool
import rj.qmce.lite.agent.ToolResult
import rj.qmce.lite.agent.WriteTool
import rj.qmce.lite.data.chat.ChatRepository
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.TextElement
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact

/** List recent chat sessions (cached). */
class ListSessionsTool : ReadOnlyTool(
    name = "list_sessions",
    description = "列出最近聊天会话（私聊与群聊），返回每个会话的 peerUid、chatType（1=私聊，2=群聊）、名称、未读数与最近消息摘要。后续发消息/读消息工具需要其中的 peerUid 与 chatType。",
    inputSchema = mapOf(),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val recentService = KernelBridge.getRecentContactService()
            ?: return err("会话服务不可用")
        val list = SdkCompat.getRecentContactFromCache(recentService, 1)
            ?: return err("未能读取会话缓存")
        if (list.isEmpty()) return ok("暂无会话")
        val lines = list.take(30).mapIndexed { index, contact ->
            val chatType = contact.chatType
            val name = contact.remark?.takeIf { it.isNotBlank() }
                ?: contact.peerName?.takeIf { it.isNotBlank() }
                ?: contact.id?.takeIf { it.isNotBlank() }
                ?: "未知"
            val abstract = contact.abstractContent
                ?.joinToString("") { it.content ?: "" }
                ?.takeIf { it.isNotBlank() }
                ?: ""
            "${index + 1}. $name (peerUid=${contact.peerUid ?: ""}, chatType=$chatType, " +
                "id=${contact.id ?: ""}, 未读=${contact.unreadCnt}) $abstract"
        }
        return ok(lines.joinToString("\n"))
    }
}

/** Read recent messages in a chat. */
class ReadMessagesTool : ReadOnlyTool(
    name = "read_messages",
    description = "读取某个会话的最近聊天记录。参数：peerUid（会话 UID，来自 list_sessions/list_groups）、chatType（1=私聊，2=群聊，默认1）、count（读取条数，默认20）。返回按时间排序的消息列表。",
    inputSchema = mapOf(
        "peerUid" to schemaString("会话 UID，来自 list_sessions 或 list_groups"),
        "chatType" to schemaInt("会话类型：1=私聊，2=群聊，默认1"),
        "count" to schemaInt("读取条数，默认20，最大50"),
    ),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val peerUid = requireString(input, "peerUid")
            ?: return err("缺少 peerUid，请先用 list_sessions 查询")
        val chatType = requireInt(input, "chatType") ?: 1
        val count = (requireInt(input, "count") ?: 20).coerceIn(1, 50)
        val runtime = QmceApplication.ensureRuntime() ?: return err("登录运行时不可用")

        val repository = ChatRepository()
        val connection = repository.connect(runtime)
        if (connection !is ChatRepository.Connection.Ready) {
            return err("消息服务不可用")
        }
        val contact = Contact(chatType, peerUid, "")
        val deferred = CompletableDeferred<List<MsgRecord>>()
        val requested = repository.loadLatest(contact, count) { _, _, list, _ ->
            deferred.complete(list.orEmpty())
        }
        if (!requested) return err("读取消息请求失败")
        val records = withTimeoutOrNull(5_000) { deferred.await() } ?: return err("读取消息超时")
        if (records.isEmpty()) return ok("该会话暂无消息")
        val lines = records.map { record ->
            val sender = record.sendNickName?.takeIf { it.isNotBlank() }
                ?: record.senderUin.takeIf { it > 0L }?.toString()
                ?: "未知"
            "id=${record.msgId} $sender: ${msgRecordToText(record)}"
        }
        return ok(lines.joinToString("\n"))
    }
}

/** Send a plain text message. */
class SendMessageTool : WriteTool(
    name = "send_message",
    description = "向指定会话发送一条纯文本消息。参数：peerUid（会话 UID）、chatType（1=私聊，2=群聊）、text（消息内容）。执行前需用户批准。",
    inputSchema = mapOf(
        "peerUid" to schemaString("会话 UID，来自 list_sessions 或 list_groups"),
        "chatType" to schemaInt("会话类型：1=私聊，2=群聊"),
        "text" to schemaString("要发送的文本内容"),
    ),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val peerUid = requireString(input, "peerUid") ?: return err("缺少 peerUid")
        val chatType = requireInt(input, "chatType") ?: 1
        val text = requireString(input, "text") ?: return err("缺少 text")
        val runtime = QmceApplication.ensureRuntime() ?: return err("登录运行时不可用")

        val repository = ChatRepository()
        val connection = repository.connect(runtime)
        if (connection !is ChatRepository.Connection.Ready) {
            return err("消息服务不可用")
        }
        val element = MsgElement().apply {
            elementType = 1
            elementId = 0
            textElement = TextElement().apply {
                content = text
                atType = 0
                atUid = 0L
                atNtUid = ""
            }
        }
        val contact = Contact(chatType, peerUid, "")
        val deferred = CompletableDeferred<Boolean>()
        val sent = repository.sendMessage(contact, arrayListOf(element)) { code, errMsg ->
            deferred.complete(code == 0)
            if (code != 0) {
                rj.qmce.lite.util.QmceLog.w("QMCE-Agent", "send_message failed: code=$code err=$errMsg")
            }
        }
        if (!sent) return err("消息服务不可用")
        return if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
            ok("发送成功")
        } else {
            err("发送失败")
        }
    }
}

/** Recall a message by msgId. */
class RecallMessageTool : WriteTool(
    name = "recall_message",
    description = "撤回一条已发送的消息。参数：peerUid、chatType、msgId（消息 ID，来自 read_messages 返回的 id）。仅能撤回自己发送的消息。执行前需用户批准。",
    inputSchema = mapOf(
        "peerUid" to schemaString("会话 UID"),
        "chatType" to schemaInt("会话类型：1=私聊，2=群聊"),
        "msgId" to schemaInt("要撤回的消息 ID"),
    ),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val peerUid = requireString(input, "peerUid") ?: return err("缺少 peerUid")
        val chatType = requireInt(input, "chatType") ?: 1
        val msgId = requireLong(input, "msgId") ?: return err("缺少 msgId")
        val runtime = QmceApplication.ensureRuntime() ?: return err("登录运行时不可用")

        val repository = ChatRepository()
        val connection = repository.connect(runtime)
        if (connection !is ChatRepository.Connection.Ready) {
            return err("消息服务不可用")
        }
        val contact = Contact(chatType, peerUid, "")
        val deferred = CompletableDeferred<Boolean>()
        val requested = repository.recallMessage(contact, msgId) { code, _ ->
            deferred.complete(code == 0)
        }
        if (!requested) return err("撤回请求失败")
        return if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
            ok("撤回成功")
        } else {
            err("撤回失败")
        }
    }
}

/** Mark a chat as read. */
class MarkReadTool : ReadOnlyTool(
    name = "mark_read",
    description = "将会话标记为已读（会清除该会话未读数）。参数：peerUid、chatType。",
    inputSchema = mapOf(
        "peerUid" to schemaString("会话 UID"),
        "chatType" to schemaInt("会话类型：1=私聊，2=群聊"),
    ),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val peerUid = requireString(input, "peerUid") ?: return err("缺少 peerUid")
        val chatType = requireInt(input, "chatType") ?: 1
        val runtime = QmceApplication.ensureRuntime() ?: return err("登录运行时不可用")
        val repository = ChatRepository()
        val connection = repository.connect(runtime)
        if (connection !is ChatRepository.Connection.Ready) {
            return err("消息服务不可用")
        }
        val contact = Contact(chatType, peerUid, "")
        val deferred = CompletableDeferred<Boolean>()
        val requested = repository.markMessagesRead(contact, runtime) { code, _ ->
            deferred.complete(code == 0)
        }
        if (!requested) return err("标记已读请求失败")
        return if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
            ok("已标记为已读")
        } else {
            err("标记已读失败")
        }
    }
}
