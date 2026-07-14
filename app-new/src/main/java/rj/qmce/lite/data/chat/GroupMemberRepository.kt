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
    )

    private val cache = ConcurrentHashMap<Long, List<Member>>()

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
                val members = parse(result)
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
        if (groupCode == null) cache.clear() else cache.remove(groupCode)
    }

    private fun parse(result: GroupMemberListResult): List<Member> = result.infos.orEmpty()
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
                role = info.role?.toString().orEmpty(),
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        .distinctBy { if (it.uid.isNotBlank()) it.uid else "uin:${it.uin}" }
        .toList()
}
