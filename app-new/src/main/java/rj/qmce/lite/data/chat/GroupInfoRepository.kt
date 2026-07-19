package rj.qmce.lite.data.chat

import com.tencent.qqnt.kernel.nativeinterface.BulletinFeedsRecord
import com.tencent.qqnt.kernel.nativeinterface.GroupDetailInfo
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import com.tencent.qqnt.watch.troop.api.ITroopRuntimeService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import rj.qmce.lite.QmceApplication

class GroupInfoRepository {
    suspend fun loadDetail(groupCode: Long, forceRefresh: Boolean): Result<GroupDetailInfo> =
        runCatching {
            require(groupCode > 0L) { "群号无效" }
            val service = troopService() ?: error("群资料服务不可用")
            val detail = withTimeoutOrNull(12_000L) {
                service.getGroupDetailInfo(groupCode, forceRefresh).first()
            } ?: error("获取群资料超时")
            normalizeCurrentOwnerPrivilege(detail)
            detail
        }

    suspend fun loadBulletin(groupCode: Long): Result<List<GroupBulletinItem>> =
        runCatching {
            require(groupCode > 0L) { "群号无效" }
            val service = troopService() ?: error("群公告服务不可用")
            val bulletin = withTimeoutOrNull(12_000L) {
                service.getGroupBulletin(groupCode).first()
            } ?: error("获取群公告超时")
            bulletin.feedsRecords.orEmpty().map(::toBulletinItem)
        }

    private fun troopService(): ITroopRuntimeService? = runCatching {
        QmceApplication.ensureRuntime()
            ?.getRuntimeService(ITroopRuntimeService::class.java, "")
    }.getOrNull()

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
