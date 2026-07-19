package rj.qmce.lite.data.chat

import android.util.Log
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberListResult
import rj.qmce.lite.kernel.KernelBridge
import java.util.concurrent.ConcurrentHashMap

object GroupMemberRepository {
    private const val TAG = "QMCE"

    data class Member(
        val uid: String,
        val uin: Long,
        val nick: String,
        val cardName: String,
        val displayName: String,
        val avatarPath: String,
        val role: String,
        val memberLevel: Int?,
        val entryIndex: Int,
    )

    private val cache = ConcurrentHashMap<Long, List<Member>>()
    private val memberLevelCache = ConcurrentHashMap<Long, ConcurrentHashMap<String, Int>>()
    private val memberTitleCache = ConcurrentHashMap<Long, ConcurrentHashMap<String, String>>()
    private val memberLevelRequests = ConcurrentHashMap.newKeySet<String>()

    fun cached(groupCode: Long): List<Member> = cache[groupCode].orEmpty()

    fun load(
        groupCode: Long,
        forceRefresh: Boolean = false,
        callback: (members: List<Member>?, error: String?) -> Unit,
    ): Boolean {
        if (groupCode <= 0L) {
            callback(null, "群号无效")
            return false
        }
        if (!forceRefresh && cache.containsKey(groupCode)) {
            callback(cache[groupCode].orEmpty(), null)
            return true
        }

        val service = KernelBridge.getGroupService()
        if (service == null) {
            callback(null, "群服务不可用")
            return false
        }

        return runCatching {
            service.getAllMemberList(groupCode, forceRefresh) { code, message, result ->
                if (code != 0 || result == null) {
                    Log.w(TAG, "group members failed: group=$groupCode code=$code message=$message")
                    callback(null, message?.takeIf { it.isNotBlank() } ?: "获取群成员失败")
                    return@getAllMemberList
                }
                val members = parse(groupCode, result)
                cache[groupCode] = members
                callback(members, null)
            }
            true
        }.onFailure {
            Log.w(TAG, "group members request threw: group=$groupCode", it)
            callback(null, "获取群成员失败")
        }.getOrDefault(false)
    }

    fun clear(groupCode: Long? = null) {
        if (groupCode == null) {
            cache.clear()
            memberLevelCache.clear()
            memberTitleCache.clear()
            memberLevelRequests.clear()
        } else {
            cache.remove(groupCode)
            memberLevelCache.remove(groupCode)
            memberTitleCache.remove(groupCode)
            memberLevelRequests
                .filter { it.startsWith("$groupCode:") }
                .forEach(memberLevelRequests::remove)
        }
    }

    fun cachedMemberLevels(groupCode: Long): Map<String, Int> =
        memberLevelCache[groupCode]?.toMap().orEmpty()

    fun cachedMemberTitles(groupCode: Long): Map<String, String> =
        memberTitleCache[groupCode]?.toMap().orEmpty()

    fun loadMemberLevels(
        groupCode: Long,
        memberUids: Collection<String>,
        callback: (levels: Map<String, Int>, error: String?) -> Unit,
    ): Boolean {
        if (groupCode <= 0L) {
            callback(emptyMap(), "群号无效")
            return false
        }
        val requestedUids = memberUids.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        if (requestedUids.isEmpty()) {
            callback(cachedMemberLevels(groupCode), null)
            return true
        }
        val levels = memberLevelCache.getOrPut(groupCode) { ConcurrentHashMap() }
        val titles = memberTitleCache.getOrPut(groupCode) { ConcurrentHashMap() }
        val pendingUids = requestedUids
            .filterNot(levels::containsKey)
            .filter { uid -> memberLevelRequests.add("$groupCode:$uid") }
        if (pendingUids.isEmpty()) {
            callback(levels.toMap(), null)
            return true
        }
        val service = KernelBridge.getGroupService()
        if (service == null) {
            pendingUids.forEach { uid -> memberLevelRequests.remove("$groupCode:$uid") }
            callback(levels.toMap(), "群服务不可用")
            return false
        }
        return runCatching {
            service.getMemberInfoForMqq(groupCode, ArrayList(pendingUids), false) { code, message, result ->
                pendingUids.forEach { uid -> memberLevelRequests.remove("$groupCode:$uid") }
                if (code != 0 || result == null) {
                    Log.w(TAG, "member levels failed: group=$groupCode code=$code message=$message")
                    callback(levels.toMap(), message?.takeIf { it.isNotBlank() } ?: "获取群等级失败")
                    return@getMemberInfoForMqq
                }
                pendingUids.forEach { uid ->
                    val info = result.infos?.get(uid) ?: return@forEach
                    if (info.memberLevel > 0) levels[uid] = info.memberLevel
                    val title = info.memberSpecialTitle?.trim().orEmpty()
                    if (title.isBlank()) titles.remove(uid) else titles[uid] = title
                }
                callback(levels.toMap(), null)
            }
            true
        }.onFailure {
            pendingUids.forEach { uid -> memberLevelRequests.remove("$groupCode:$uid") }
            Log.w(TAG, "member levels request threw: group=$groupCode", it)
            callback(levels.toMap(), "获取群等级失败")
        }.getOrDefault(false)
    }

    private fun parse(groupCode: Long, result: GroupMemberListResult): List<Member> = result.infos.orEmpty()
        .values
        .asSequence()
        .filter { !it.isDelete && !it.isRobot }
        .map { info ->
            val nick = info.nick.orEmpty()
            val cardName = info.cardName.orEmpty()
            Member(
                uid = info.uid.orEmpty(),
                uin = info.uin,
                nick = nick,
                cardName = cardName,
                displayName = cardName.takeIf { it.isNotBlank() }
                    ?: info.remark.takeIf { !it.isNullOrBlank() }
                    ?: nick.ifBlank { info.uin.toString() },
                avatarPath = info.avatarPath.orEmpty(),
                role = info.role?.name.orEmpty(),
                memberLevel = memberLevelCache[groupCode]?.get(info.uid.orEmpty()),
                entryIndex = 0,
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        .mapIndexed { index, member -> member.copy(entryIndex = index) }
        .toList()
}
