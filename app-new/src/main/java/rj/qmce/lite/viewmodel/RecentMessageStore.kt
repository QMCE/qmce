package rj.qmce.lite.viewmodel

import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/** 双层表: id(QQ号) → (msgTime → MsgRecord)，自己发的消息内核不记录时兜底 */
object RecentMessageStore {
    private val table = ConcurrentHashMap<String, ConcurrentHashMap<Long, MsgRecord>>()
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version

    fun put(id: String, record: MsgRecord) {
        if (id.isBlank()) return
        val ts = record.msgTime
        val bucket = table.getOrPut(id) { ConcurrentHashMap() }
        bucket[ts] = record
        _version.value++
    }

    /** 某个会话最新的 MsgRecord */
    fun latest(id: String): MsgRecord? {
        if (id.isBlank()) return null
        val bucket = table[id] ?: return null
        return bucket.maxByOrNull { it.key }?.value
    }

    fun clear() {
        table.clear()
        _version.value++
    }
}
