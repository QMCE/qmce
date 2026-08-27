package rj.qmce.lite.agent.kernel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rj.qmce.lite.agent.ToolResult
import rj.qmce.lite.agent.WriteTool
import rj.qmce.lite.data.chat.ChatSettingsRepository
import rj.qmce.lite.data.notify.ContactNotifyRepository
import rj.qmce.lite.data.notify.GroupNotifyRepository
import rj.qmce.lite.data.notify.UiGroupNotice
import rj.qmce.lite.util.QmceLog

/** Approve a friend request. */
class ApproveFriendTool : WriteTool(
    name = "approve_friend",
    description = "同意一条好友申请。参数：uid（申请人的 UID）、reqTime（申请时间戳，可选）、accept（true=同意，false=拒绝）。执行前需用户批准。",
    inputSchema = mapOf(
        "uid" to schemaString("申请人的 UID"),
        "reqTime" to schemaInt("申请时间戳（秒），可选"),
        "accept" to schemaBool("true=同意，false=拒绝，默认 true"),
    ),
    requiredParams = listOf("uid"),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val uid = requireString(input, "uid") ?: return err("缺少 uid")
        val reqTime = requireLong(input, "reqTime") ?: (System.currentTimeMillis() / 1000L)
        val accept = (input["accept"] as? Boolean) ?: true
        val repository = ContactNotifyRepository(onListChanged = {})
        val deferred = CompletableDeferred<Boolean>()
        repository.approve(uid, reqTime, accept) { success, message ->
            deferred.complete(success)
            if (!success) QmceLog.w("QMCE-Agent", "approve_friend failed: $message")
        }
        return if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
            ok("好友申请已处理")
        } else {
            err("好友申请处理失败")
        }
    }
}

/** Set a chat as top or muted. */
class SetChatTopTool : WriteTool(
    name = "set_chat_top",
    description = "将会话置顶或取消置顶。参数：peerUid（会话 UID）、chatType（1=私聊，2=群聊）、peerUin（QQ号，群聊时为群号）、enabled（true=置顶）。执行前需用户批准。",
    inputSchema = mapOf(
        "peerUid" to schemaString("会话 UID"),
        "chatType" to schemaInt("会话类型：1=私聊，2=群聊"),
        "peerUin" to schemaInt("QQ号（私聊）或群号（群聊）"),
        "enabled" to schemaBool("true=置顶，false=取消置顶"),
    ),
    requiredParams = listOf("peerUid", "enabled"),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val peerUid = requireString(input, "peerUid") ?: return err("缺少 peerUid")
        val chatType = requireInt(input, "chatType") ?: 1
        val peerUin = requireLong(input, "peerUin") ?: 0L
        val enabled = (input["enabled"] as? Boolean) ?: true
        val deferred = CompletableDeferred<Boolean>()
        val requested = ChatSettingsRepository.setTop(chatType, peerUid, peerUin, enabled) { success, message ->
            deferred.complete(success)
            if (!success) QmceLog.w("QMCE-Agent", "set_chat_top failed: $message")
        }
        if (!requested) return err("设置置顶请求失败")
        return if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
            ok("置顶设置成功")
        } else {
            err("置顶设置失败")
        }
    }
}

/** Set a chat as muted. */
class SetChatMutedTool : WriteTool(
    name = "set_chat_muted",
    description = "将会话设为免打扰。参数：peerUid、chatType、peerUin、muted（true=免打扰）。执行前需用户批准。",
    inputSchema = mapOf(
        "peerUid" to schemaString("会话 UID"),
        "chatType" to schemaInt("会话类型：1=私聊，2=群聊"),
        "peerUin" to schemaInt("QQ号（私聊）或群号（群聊）"),
        "muted" to schemaBool("true=免打扰，false=恢复提醒"),
    ),
    requiredParams = listOf("peerUid", "muted"),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val peerUid = requireString(input, "peerUid") ?: return err("缺少 peerUid")
        val chatType = requireInt(input, "chatType") ?: 1
        val peerUin = requireLong(input, "peerUin") ?: 0L
        val muted = (input["muted"] as? Boolean) ?: true
        val deferred = CompletableDeferred<Boolean>()
        val requested = ChatSettingsRepository.setMuted(chatType, peerUid, peerUin, muted) { success, message ->
            deferred.complete(success)
            if (!success) QmceLog.w("QMCE-Agent", "set_chat_muted failed: $message")
        }
        if (!requested) return err("设置免打扰请求失败")
        return if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
            ok("免打扰设置成功")
        } else {
            err("免打扰设置失败")
        }
    }
}

/** Approve / reject a group system notice (join request). */
class ApproveGroupNoticeTool : WriteTool(
    name = "approve_group_notice",
    description = "处理一条群系统通知（入群申请/邀请）。参数：seq（通知序号）、groupCode（群号）、accept（true=同意，false=拒绝）。执行前需用户批准。",
    inputSchema = mapOf(
        "seq" to schemaInt("通知序号"),
        "groupCode" to schemaInt("群号"),
        "accept" to schemaBool("true=同意，false=拒绝"),
    ),
    requiredParams = listOf("seq", "groupCode"),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val seq = requireLong(input, "seq") ?: return err("缺少 seq")
        val groupCode = requireLong(input, "groupCode") ?: return err("缺少 groupCode")
        val accept = (input["accept"] as? Boolean) ?: true

        val noticesDeferred = CompletableDeferred<List<UiGroupNotice>>()
        val repository = GroupNotifyRepository { list ->
            if (!noticesDeferred.isCompleted && list.isNotEmpty()) {
                noticesDeferred.complete(list)
            }
        }
        withContext(Dispatchers.IO) {
            repository.start()
            repository.refresh()
        }
        return try {
            val notices = withTimeoutOrNull(8_000) { noticesDeferred.await() } ?: emptyList()
            val notice = notices.firstOrNull { it.seq == seq && it.groupCode == groupCode }
                ?: return err("找不到对应的群通知（seq=$seq）")
            val deferred = CompletableDeferred<Boolean>()
            repository.operate(notice, accept) { success, message ->
                deferred.complete(success)
                if (!success) QmceLog.w("QMCE-Agent", "approve_group_notice failed: $message")
            }
            if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
                ok("群通知已处理")
            } else {
                err("群通知处理失败")
            }
        } finally {
            repository.stop()
        }
    }
}
