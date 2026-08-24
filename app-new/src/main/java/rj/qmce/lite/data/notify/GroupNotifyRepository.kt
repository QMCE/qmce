package rj.qmce.lite.data.notify

import android.util.Log
import com.tencent.qqnt.kernel.api.IGroupService
import com.tencent.qqnt.kernel.nativeinterface.BulletinFeedsDownloadInfo
import com.tencent.qqnt.kernel.nativeinterface.DataSource
import com.tencent.qqnt.kernel.nativeinterface.FirstGroupBulletinInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupAllInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupArkInviteStateInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupBulletin
import com.tencent.qqnt.kernel.nativeinterface.GroupBulletinListResult
import com.tencent.qqnt.kernel.nativeinterface.GroupDetailInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupExtInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupExtListUpdateType
import com.tencent.qqnt.kernel.nativeinterface.GroupListUpdateType
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberInfoListId
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberLevelInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberListChangeInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupMsgMaskInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyMsg
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyMsgStatus
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyMsgType
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyOperateMsg
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyOperateSource
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyOperateType
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyTargetMsg
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyTemplateItem
import com.tencent.qqnt.kernel.nativeinterface.GroupSimpleInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupStatisticInfo
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupListener
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.JoinGroupNotifyMsg
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.qqnt.kernel.nativeinterface.RemindGroupBulletinMsg
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat

data class UiGroupNotice(
    val seq: Long,
    val groupCode: Long,
    val groupName: String,
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val pending: Boolean,
    val raw: GroupNotifyMsg,
)

class GroupNotifyRepository(
    private val onListChanged: (List<UiGroupNotice>) -> Unit,
) : IKernelGroupListener {

    companion object {
        private const val TAG = "QMCE-GroupNotify"
        private const val FETCH_COUNT = 50
    }

    private val listenerLock = Any()
    private var groupService: IGroupService? = null
    private var listenerRegistered = false
    private var cachedNotifies: List<UiGroupNotice> = emptyList()

    fun start() {
        val service = KernelBridge.getGroupService()
            ?: KernelBridge.awaitGroupService()
            ?: run {
                Log.w(TAG, "group service unavailable")
                publish(emptyList())
                return
            }
        synchronized(listenerLock) {
            if (!listenerRegistered || groupService !== service) {
                unregisterInternal()
                runCatching {
                    SdkCompat.addGroupListener(service, this)
                    groupService = service
                    listenerRegistered = true
                }.onFailure {
                    Log.w(TAG, "group listener registration failed", it)
                    publish(emptyList())
                    return
                }
            }
        }
        runCatching {
            service.getSingleScreenNotifies(
                false,
                0L,
                FETCH_COUNT,
                object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        Log.d(TAG, "getSingleScreenNotifies: code=$code errMsg=$errMsg")
                    }
                },
            )
            service.clearGroupNotifiesUnreadCount(false, object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "clearGroupNotifiesUnreadCount: code=$code")
                }
            })
        }.onFailure {
            Log.w(TAG, "fetch group notifies failed", it)
        }
    }

    fun stop() {
        synchronized(listenerLock) {
            unregisterInternal()
        }
        cachedNotifies = emptyList()
    }

    /** Re-fetch the group notices so the UI reflects decisions. */
    fun refresh() {
        val service = groupService ?: KernelBridge.getGroupService() ?: return
        runCatching {
            service.getSingleScreenNotifies(
                false,
                0L,
                FETCH_COUNT,
                object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        Log.d(TAG, "group refresh: code=$code errMsg=$errMsg")
                    }
                },
            )
        }.onFailure {
            Log.w(TAG, "group refresh failed", it)
        }
    }

    fun operate(notice: UiGroupNotice, accept: Boolean, callback: (Boolean, String?) -> Unit) {
        val service = groupService ?: KernelBridge.getGroupService()
        if (service == null) {
            callback(false, "群服务不可用")
            return
        }
        val raw = notice.raw
        val target = GroupNotifyTargetMsg().apply {
            seq = raw.seq
            groupCode = raw.group?.groupCode ?: notice.groupCode
            type = raw.type
            postscript = raw.postscript.orEmpty()
            operateTransInfo = raw.operateTransInfo
        }
        val operateMsg = GroupNotifyOperateMsg().apply {
            operateSource = GroupNotifyOperateSource.NOTIFY_LIST
            operateType = if (accept) {
                GroupNotifyOperateType.KAGREE
            } else {
                GroupNotifyOperateType.KREFUSE
            }
            setTargetMsg(target)
        }
        runCatching {
            service.operateSysNotify(false, operateMsg, object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    callback(code == 0, errMsg?.takeIf { it.isNotBlank() })
                }
            })
        }.onFailure {
            Log.w(TAG, "operateSysNotify failed", it)
            callback(false, it.message)
        }
    }

    override fun onGroupSingleScreenNotifies(
        isGroup: Boolean,
        groupCode: Long,
        notifies: ArrayList<GroupNotifyMsg>,
    ) {
        Log.d(TAG, "onGroupSingleScreenNotifies: count=${notifies.size} isGroup=$isGroup code=$groupCode")
        publish(notifies.mapNotNull(::mapGroupNotify))
    }

    override fun onGroupNotifiesUpdated(
        isGroup: Boolean,
        notifies: ArrayList<GroupNotifyMsg>,
    ) {
        Log.d(TAG, "onGroupNotifiesUpdated: count=${notifies.size}")
        if (notifies.isNotEmpty()) {
            publish(notifies.mapNotNull(::mapGroupNotify))
        }
    }

    override fun onGroupSingleScreenNotifiesV2(
        isGroup: Boolean,
        groupCode: Long,
        p2: Long,
        p3: Boolean,
        p4: Int,
        notifies: ArrayList<GroupNotifyMsg>?,
        templates: ArrayList<GroupNotifyTemplateItem>?,
    ) {
        val list = notifies.orEmpty()
        if (list.isNotEmpty()) {
            Log.d(TAG, "onGroupSingleScreenNotifiesV2: count=${list.size}")
            publish(list.mapNotNull(::mapGroupNotify))
        }
    }

    override fun onGroupNotifiesUpdatedV2(
        isGroup: Boolean,
        groupCode: Long,
        notifies: ArrayList<GroupNotifyMsg>?,
        templates: ArrayList<GroupNotifyTemplateItem>?,
    ) {
        val list = notifies.orEmpty()
        if (list.isNotEmpty()) {
            Log.d(TAG, "onGroupNotifiesUpdatedV2: count=${list.size}")
            publish(list.mapNotNull(::mapGroupNotify))
        }
    }

    override fun onGroupListUpdate(type: GroupListUpdateType, groups: ArrayList<GroupSimpleInfo>) = Unit
    override fun onGroupBulletinChange(groupCode: Long, bulletin: GroupBulletin) = Unit
    override fun onGroupDetailInfoChange(detail: GroupDetailInfo) = Unit
    override fun onGetGroupBulletinListResult(
        groupCode: Long,
        errorMessage: String,
        result: GroupBulletinListResult,
    ) = Unit
    override fun onGroupAdd(groupCode: Long) = Unit
    override fun onGroupAllInfoChange(info: GroupAllInfo) = Unit
    override fun onGroupArkInviteStateResult(groupCode: Long, info: GroupArkInviteStateInfo) = Unit
    override fun onGroupBulletinRemindNotify(groupCode: Long, info: RemindGroupBulletinMsg) = Unit
    override fun onGroupBulletinRichMediaDownloadComplete(info: BulletinFeedsDownloadInfo) = Unit
    override fun onGroupBulletinRichMediaProgressUpdate(info: BulletinFeedsDownloadInfo) = Unit
    override fun onGroupConfMemberChange(groupCode: Long, memberUids: ArrayList<String>) = Unit
    override fun onGroupExtListUpdate(type: GroupExtListUpdateType, infos: ArrayList<GroupExtInfo>) = Unit
    override fun onGroupFirstBulletinNotify(info: FirstGroupBulletinInfo) = Unit
    override fun onGroupNotifiesUnreadCountUpdated(isGroup: Boolean, groupCode: Long, count: Int) = Unit
    override fun onGroupEssenceListChange(groupCode: Long) = Unit
    override fun onGroupListInited(inited: Boolean) = Unit
    override fun onGroupMemberLevelInfoChange(groupCode: Long, info: GroupMemberLevelInfo?) = Unit
    override fun onGroupNotifiesUnreadCountUpdatedV2(
        isGroup: Boolean,
        groupCode: Long,
        p2: Int,
        p3: Int,
        p4: Int,
        p5: Int,
    ) = Unit
    override fun onGroupStatisticInfoChange(groupCode: Long, info: GroupStatisticInfo) = Unit
    override fun onGroupsMsgMaskResult(infos: ArrayList<GroupMsgMaskInfo>) = Unit
    override fun onJoinGroupNoVerifyFlag(groupCode: Long, first: Boolean, second: Boolean) = Unit
    override fun onJoinGroupNotify(info: JoinGroupNotifyMsg) = Unit
    override fun onMemberInfoChange(
        groupCode: Long,
        source: DataSource,
        members: HashMap<String, MemberInfo>,
    ) = Unit
    override fun onMemberListChange(info: GroupMemberListChangeInfo) = Unit
    override fun onSearchMemberChange(
        first: String,
        second: String,
        ids: ArrayList<GroupMemberInfoListId>,
        members: HashMap<String, MemberInfo>,
    ) = Unit
    override fun onShutUpMemberListChanged(groupCode: Long, members: ArrayList<MemberInfo>) = Unit

    private fun publish(items: List<UiGroupNotice>) {
        cachedNotifies = items.sortedByDescending { it.seq }
        onListChanged(cachedNotifies)
    }

    private fun unregisterInternal() {
        if (!listenerRegistered) return
        runCatching {
            groupService?.let { SdkCompat.removeGroupListener(it, this) }
        }
        groupService = null
        listenerRegistered = false
    }

    private fun mapGroupNotify(msg: GroupNotifyMsg): UiGroupNotice? {
        if (msg.seq <= 0L) return null
        val group = msg.group
        val groupCode = group?.groupCode ?: 0L
        val groupName = group?.groupName.orEmpty().ifBlank {
            if (groupCode > 0L) groupCode.toString() else "群通知"
        }
        val title = msg.showModuleMsg.orEmpty().ifBlank { buildFallbackTitle(msg) }
        val subtitle = buildSubtitle(msg)
        val status = msg.status ?: GroupNotifyMsgStatus.KUNHANDLE
        return UiGroupNotice(
            seq = msg.seq,
            groupCode = groupCode,
            groupName = groupName,
            title = title,
            subtitle = subtitle,
            statusLabel = statusLabel(status),
            pending = isActionable(msg),
            raw = msg,
        )
    }

    private fun buildFallbackTitle(msg: GroupNotifyMsg): String {
        val user = msg.user1?.nickName.orEmpty().ifBlank { msg.actionUser?.nickName.orEmpty() }
        val type = msg.type
        return when (type) {
            GroupNotifyMsgType.REQUESTJOINNEEDADMINISTRATORPASS -> "$user 申请加群"
            GroupNotifyMsgType.INVITEDNEEDADMINISTRATORPASS -> "$user 邀请加群"
            GroupNotifyMsgType.INVITEDBYMEMBER -> "$user 邀请成员"
            GroupNotifyMsgType.AGREEDTOJOINDIRECT -> "$user 已同意加群"
            GroupNotifyMsgType.REFUSEDBYADMINISTRATOR -> "管理员已拒绝"
            else -> user.ifBlank { "群系统通知" }
        }
    }

    private fun buildSubtitle(msg: GroupNotifyMsg): String {
        val parts = buildList {
            msg.group?.groupName?.takeIf { it.isNotBlank() }?.let { add(it) }
            msg.postscript?.takeIf { it.isNotBlank() }?.let { add(it) }
            msg.warningTips?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
        return parts.joinToString(" · ")
    }

    private fun isActionable(msg: GroupNotifyMsg): Boolean {
        val status = msg.status ?: return false
        if (status != GroupNotifyMsgStatus.KUNHANDLE && status != GroupNotifyMsgStatus.KINIT) {
            return false
        }
        return when (msg.type) {
            GroupNotifyMsgType.REQUESTJOINNEEDADMINISTRATORPASS,
            GroupNotifyMsgType.INVITEDNEEDADMINISTRATORPASS,
            -> true
            else -> false
        }
    }

    private fun statusLabel(status: GroupNotifyMsgStatus): String = when (status) {
        GroupNotifyMsgStatus.KAGREED -> "已同意"
        GroupNotifyMsgStatus.KREFUSED -> "已拒绝"
        GroupNotifyMsgStatus.KIGNORED -> "已忽略"
        GroupNotifyMsgStatus.KHANDLED -> "已处理"
        GroupNotifyMsgStatus.KUNHANDLE, GroupNotifyMsgStatus.KINIT -> "待处理"
    }
}
