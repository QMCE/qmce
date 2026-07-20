package rj.qmce.lite.data.chat

import android.util.Log
import com.tencent.qqnt.kernel.api.IGroupService
import com.tencent.qqnt.kernel.nativeinterface.*
import com.tencent.qqnt.watch.troop.api.ITroopRuntimeService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.kernel.KernelBridge
import java.util.concurrent.ConcurrentHashMap

class GroupInfoRepository {
    private val pendingDetails = ConcurrentHashMap<Long, CompletableDeferred<Result<GroupDetailInfo>>>()
    private val pendingBulletins = ConcurrentHashMap<Long, CompletableDeferred<Result<GroupBulletin>>>()
    private val listenerLock = Any()
    private var listenerService: IGroupService? = null
    private var listenerRegistered = false

    fun close() {
        pendingDetails.values.forEach { it.cancel() }
        pendingBulletins.values.forEach { it.cancel() }
        pendingDetails.clear()
        pendingBulletins.clear()
        synchronized(listenerLock) {
            if (listenerRegistered) {
                runCatching { listenerService?.u(groupListener) }
            }
            listenerService = null
            listenerRegistered = false
        }
    }

    suspend fun loadDetail(groupCode: Long, forceRefresh: Boolean): Result<GroupDetailInfo> =
        runCatching {
            require(groupCode > 0L) { "群号无效" }
            val primary = loadDetailFromKernel(groupCode, forceRefresh)
            val detail = primary.getOrElse { primaryError ->
                loadDetailFromTroop(groupCode, forceRefresh).getOrElse { fallbackError ->
                    throw IllegalStateException(
                        fallbackError.message ?: primaryError.message ?: "获取群资料失败",
                    )
                }
            }
            normalizeCurrentOwnerPrivilege(detail)
            detail
        }

    suspend fun loadBulletin(groupCode: Long): Result<List<GroupBulletinItem>> =
        runCatching {
            require(groupCode > 0L) { "群号无效" }
            val primary = loadBulletinFromKernel(groupCode)
            val bulletin = primary.getOrElse { primaryError ->
                loadBulletinFromTroop(groupCode).getOrElse { fallbackError ->
                    throw IllegalStateException(
                        fallbackError.message ?: primaryError.message ?: "获取群公告失败",
                    )
                }
            }
            bulletin.feedsRecords.orEmpty().map(::toBulletinItem)
        }

    private suspend fun loadDetailFromKernel(
        groupCode: Long,
        forceRefresh: Boolean,
    ): Result<GroupDetailInfo> {
        val service = KernelBridge.awaitGroupService()
            ?: return Result.failure(IllegalStateException("群资料服务不可用"))
        if (!ensureListener(service)) {
            return Result.failure(IllegalStateException("群资料监听器注册失败"))
        }
        val pending = CompletableDeferred<Result<GroupDetailInfo>>()
        pendingDetails[groupCode]?.cancel()
        pendingDetails[groupCode] = pending
        return try {
            service.getGroupDetailInfo(
                groupCode,
                GroupInfoSource.KDATACARD,
                object : IOperateCallback {
                    override fun onResult(errorCode: Int, errorMessage: String?) {
                        if (errorCode != 0) {
                            pending.complete(
                                Result.failure(
                                    IllegalStateException(
                                        errorMessage?.takeIf(String::isNotBlank)
                                            ?: "获取群资料失败 ($errorCode)",
                                    ),
                                ),
                            )
                        }
                    }
                },
            )
            withTimeoutOrNull(12_000L) {
                pending.await()
            } ?: Result.failure(IllegalStateException("获取群资料超时"))
        } catch (throwable: Throwable) {
            Log.w("QMCE", "group detail request failed: group=$groupCode", throwable)
            Result.failure(IllegalStateException("获取群资料请求失败", throwable))
        } finally {
            pendingDetails.remove(groupCode, pending)
        }
    }

    private suspend fun loadBulletinFromKernel(groupCode: Long): Result<GroupBulletin> {
        val service = KernelBridge.awaitGroupService()
            ?: return Result.failure(IllegalStateException("群公告服务不可用"))
        if (!ensureListener(service)) {
            return Result.failure(IllegalStateException("群公告监听器注册失败"))
        }
        val pending = CompletableDeferred<Result<GroupBulletin>>()
        pendingBulletins[groupCode]?.cancel()
        pendingBulletins[groupCode] = pending
        return try {
            service.getGroupBulletin(
                groupCode,
                object : IOperateCallback {
                    override fun onResult(errorCode: Int, errorMessage: String?) {
                        if (errorCode != 0) {
                            pending.complete(
                                Result.failure(
                                    IllegalStateException(
                                        errorMessage?.takeIf(String::isNotBlank)
                                            ?: "获取群公告失败 ($errorCode)",
                                    ),
                                ),
                            )
                        }
                    }
                },
            )
            withTimeoutOrNull(12_000L) {
                pending.await()
            } ?: Result.failure(IllegalStateException("获取群公告超时"))
        } catch (throwable: Throwable) {
            Log.w("QMCE", "group bulletin request failed: group=$groupCode", throwable)
            Result.failure(IllegalStateException("获取群公告请求失败", throwable))
        } finally {
            pendingBulletins.remove(groupCode, pending)
        }
    }

    private suspend fun loadDetailFromTroop(
        groupCode: Long,
        forceRefresh: Boolean,
    ): Result<GroupDetailInfo> = runCatching {
        val service = troopService() ?: error("群资料服务不可用")
        withTimeoutOrNull(12_000L) {
            service.getGroupDetailInfo(groupCode, forceRefresh).first()
        } ?: error("获取群资料超时")
    }

    private suspend fun loadBulletinFromTroop(groupCode: Long): Result<GroupBulletin> = runCatching {
        val service = troopService() ?: error("群公告服务不可用")
        withTimeoutOrNull(12_000L) {
            service.getGroupBulletin(groupCode).first()
        } ?: error("获取群公告超时")
    }

    private fun troopService(): ITroopRuntimeService? = runCatching {
        QmceApplication.ensureRuntime()
            ?.getRuntimeService(ITroopRuntimeService::class.java, "")
    }.getOrNull()

    private fun ensureListener(service: IGroupService): Boolean = synchronized(listenerLock) {
        if (listenerRegistered && listenerService === service) return@synchronized true
        if (listenerRegistered) {
            runCatching { listenerService?.u(groupListener) }
            listenerRegistered = false
            listenerService = null
        }
        runCatching {
            service.m(groupListener)
            listenerService = service
            listenerRegistered = true
            true
        }.onFailure {
            Log.w("QMCE", "group listener registration failed", it)
        }.getOrDefault(false)
    }

    private val groupListener = object : IKernelGroupListener {
        override fun onGroupBulletinChange(groupCode: Long, bulletin: GroupBulletin) {
            pendingBulletins.remove(groupCode)?.complete(Result.success(bulletin))
        }

        override fun onGroupDetailInfoChange(detail: GroupDetailInfo) {
            val groupCode = detail.groupCode
            if (groupCode > 0L) {
                pendingDetails.remove(groupCode)?.complete(Result.success(detail))
            }
        }

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
        override fun onGroupListUpdate(type: GroupListUpdateType, groups: ArrayList<GroupSimpleInfo>) = Unit
        override fun onGroupNotifiesUnreadCountUpdated(
            isGroup: Boolean,
            groupCode: Long,
            count: Int,
        ) = Unit

        override fun onGroupNotifiesUpdated(
            isGroup: Boolean,
            notifies: ArrayList<GroupNotifyMsg>,
        ) = Unit

        override fun onGroupSingleScreenNotifies(
            isGroup: Boolean,
            groupCode: Long,
            notifies: ArrayList<GroupNotifyMsg>,
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

        override fun onShutUpMemberListChanged(
            groupCode: Long,
            members: ArrayList<MemberInfo>,
        ) = Unit
    }

    private fun normalizeCurrentOwnerPrivilege(detail: GroupDetailInfo) {
        val runtime = QmceApplication.ensureRuntime() ?: return
        val ownerUid = detail.ownerUid.orEmpty().trim()
        if (ownerUid.isBlank()) return
        val currentUid = runCatching { runtime.currentUid.orEmpty().trim() }.getOrDefault("")
        val currentUin = runCatching { runtime.currentUin.orEmpty().trim() }.getOrDefault("")
        val currentAccountUin = runCatching { runtime.currentAccountUin.orEmpty().trim() }
            .getOrDefault("")
        if (ownerUid == currentUid || ownerUid == currentUin || ownerUid == currentAccountUin) {
            detail.cmdUinPrivilege = MemberRole.OWNER
        }
    }

    private fun toBulletinItem(record: BulletinFeedsRecord): GroupBulletinItem {
        val text = record.feedsMsg?.feedsContents.orEmpty()
            .asSequence()
            .map { it.contentValue.orEmpty().trim() }
            .filter { it.isNotBlank() && it != "群公告" }
            .joinToString("\n")
        return GroupBulletinItem(
            feedId = record.feedsId.orEmpty(),
            fromUid = record.fromUid.orEmpty(),
            createTime = record.createTime,
            pinned = record.setTop != 0,
            text = text,
        )
    }
}

data class GroupBulletinItem(
    val feedId: String,
    val fromUid: String,
    val createTime: Int,
    val pinned: Boolean,
    val text: String,
)
