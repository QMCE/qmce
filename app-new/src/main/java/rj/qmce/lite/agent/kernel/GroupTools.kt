package rj.qmce.lite.agent.kernel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import rj.qmce.lite.agent.ReadOnlyTool
import rj.qmce.lite.agent.ToolResult
import rj.qmce.lite.agent.WriteTool
import rj.qmce.lite.data.chat.GroupInfoRepository
import rj.qmce.lite.data.chat.GroupManagementRepository
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import com.tencent.qqnt.kernelpublic.nativeinterface.MemberRole

/** List joined groups (from the recent-contact cache, which includes chatType=2 entries). */
class ListGroupsTool : ReadOnlyTool(
    name = "list_groups",
    description = "列出当前账号的群聊（来自会话缓存，包含最近活跃的群）。返回每个群的 groupCode、群名称、成员数。后续群操作工具需要其中的 groupCode。",
    inputSchema = mapOf(),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val recentService = KernelBridge.getRecentContactService()
            ?: return err("会话服务不可用")
        val list = SdkCompat.getRecentContactFromCache(recentService, 1)
            ?: return err("未能读取会话缓存")
        val groups = list.filter { it.chatType == 2 }.take(30)
        if (groups.isEmpty()) return ok("暂无群聊")
        val lines = groups.mapIndexed { index, group ->
            val name = group.peerName?.takeIf { it.isNotBlank() }
                ?: group.id?.takeIf { it.isNotBlank() }
                ?: "未知群"
            "${index + 1}. $name (groupCode=${group.peerUid ?: group.id ?: ""}, " +
                "unread=${group.unreadCnt})"
        }
        return ok(lines.joinToString("\n"))
    }
}

/** Fetch group info by groupCode. */
class GetGroupInfoTool : ReadOnlyTool(
    name = "get_group_info",
    description = "读取某个群的资料详情。参数：groupCode（群号，来自 list_groups）。返回群名、群号、成员数、我的角色等。",
    inputSchema = mapOf(
        "groupCode" to schemaInt("群号，来自 list_groups"),
    ),
    requiredParams = listOf("groupCode"),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val groupCode = requireLong(input, "groupCode") ?: return err("缺少 groupCode")
        val repository = GroupInfoRepository()
        val detail = repository.loadDetail(groupCode, forceRefresh = false).getOrNull()
            ?: return err(repository.loadDetail(groupCode, forceRefresh = false)
                .exceptionOrNull()?.message ?: "获取群资料失败")
        return ok(
            "群名: ${detail.groupName}\n" +
                "群号: ${detail.groupCode}\n" +
                "成员数: ${detail.memberNum}\n" +
                "我的角色: ${detail.cmdUinPrivilege}",
        )
    }
}

/** Set whole-group mute. */
class SetGroupAllMutedTool : WriteTool(
    name = "set_group_all_muted",
    description = "设置群全员禁言。参数：groupCode（群号）、enabled（true=全员禁言，false=解除）。需要群管理权限。执行前需用户批准。",
    inputSchema = mapOf(
        "groupCode" to schemaInt("群号"),
        "enabled" to schemaBool("true=开启全员禁言，false=解除"),
    ),
    requiredParams = listOf("groupCode", "enabled"),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val groupCode = requireLong(input, "groupCode") ?: return err("缺少 groupCode")
        val enabled = (input["enabled"] as? Boolean) ?: return err("缺少 enabled")
        val role = currentGroupRole(groupCode)
        val deferred = CompletableDeferred<Boolean>()
        val requested = GroupManagementRepository.setAllMuted(groupCode, enabled, role) { success, message ->
            deferred.complete(success)
            if (!success) rj.qmce.lite.util.QmceLog.w("QMCE-Agent", "set_group_all_muted failed: $message")
        }
        if (!requested) return err("群管理请求失败")
        return if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
            ok("操作成功")
        } else {
            err("操作失败（可能无权限）")
        }
    }
}

/** Kick a group member. */
class KickGroupMemberTool : WriteTool(
    name = "kick_group_member",
    description = "将成员移出群聊。参数：groupCode（群号）、memberUid（成员 UID）、targetRole（成员角色：OWNER/ADMIN/MEMBER，默认 MEMBER）。需要群管理权限。执行前需用户批准。",
    inputSchema = mapOf(
        "groupCode" to schemaInt("群号"),
        "memberUid" to schemaString("成员 UID"),
        "targetRole" to schemaString("成员角色：OWNER/ADMIN/MEMBER，默认 MEMBER"),
    ),
    requiredParams = listOf("groupCode", "memberUid"),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val groupCode = requireLong(input, "groupCode") ?: return err("缺少 groupCode")
        val memberUid = requireString(input, "memberUid") ?: return err("缺少 memberUid")
        val targetRole = requireString(input, "targetRole") ?: "MEMBER"
        val actorRole = currentGroupRole(groupCode)
        val deferred = CompletableDeferred<Boolean>()
        val requested = GroupManagementRepository.kickMember(
            groupCode, memberUid, actorRole, targetRole,
        ) { success, message ->
            deferred.complete(success)
            if (!success) rj.qmce.lite.util.QmceLog.w("QMCE-Agent", "kick_group_member failed: $message")
        }
        if (!requested) return err("群管理请求失败")
        return if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
            ok("已移出成员")
        } else {
            err("移出成员失败（可能无权限）")
        }
    }
}

/** Publish a group bulletin. */
class PublishGroupBulletinTool : WriteTool(
    name = "publish_group_bulletin",
    description = "发布群公告。参数：groupCode（群号）、text（公告内容）、pinned（true=置顶，默认false）。需要群管理权限。执行前需用户批准。",
    inputSchema = mapOf(
        "groupCode" to schemaInt("群号"),
        "text" to schemaString("公告内容"),
        "pinned" to schemaBool("是否置顶，默认 false"),
    ),
    requiredParams = listOf("groupCode", "text"),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val groupCode = requireLong(input, "groupCode") ?: return err("缺少 groupCode")
        val text = requireString(input, "text") ?: return err("缺少 text")
        val pinned = (input["pinned"] as? Boolean) ?: false
        val role = currentGroupRole(groupCode)
        val deferred = CompletableDeferred<Boolean>()
        val requested = GroupManagementRepository.publishBulletin(
            groupCode, null, text, pinned, role,
        ) { success, message ->
            deferred.complete(success)
            if (!success) rj.qmce.lite.util.QmceLog.w("QMCE-Agent", "publish_group_bulletin failed: $message")
        }
        if (!requested) return err("群公告请求失败")
        return if (withTimeoutOrNull(5_000) { deferred.await() } == true) {
            ok("公告发布成功")
        } else {
            err("公告发布失败（可能无权限）")
        }
    }
}

private suspend fun currentGroupRole(groupCode: Long): MemberRole? =
    runCatching {
        val detail = GroupInfoRepository().loadDetail(groupCode, forceRefresh = false).getOrNull()
        when (detail?.cmdUinPrivilege) {
            MemberRole.OWNER -> MemberRole.OWNER
            MemberRole.ADMIN -> MemberRole.ADMIN
            else -> null
        }
    }.getOrNull()
