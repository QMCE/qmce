package rj.qmce.lite.notify

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import rj.qmce.lite.viewmodel.SettingsViewModel

/** LRU of chats the user has opened; used to gate conversation Shortcuts. */
object QmceRecentViewedChats {
    private const val KEY = "recent_viewed_chats_json"
    const val MAX_ENTRIES = 6

    data class Entry(
        val peerUid: String,
        val peerUin: Long,
        val chatType: Int,
        val title: String,
        val lastOpenedAt: Long,
    )

    fun contains(context: Context, peerUid: String, chatType: Int): Boolean =
        load(context).any { it.peerUid == peerUid && it.chatType == chatType }

    fun load(context: Context): List<Entry> {
        val raw = context.applicationContext
            .getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val peerUid = o.optString("peerUid").takeIf { it.isNotBlank() } ?: continue
                    add(
                        Entry(
                            peerUid = peerUid,
                            peerUin = o.optLong("peerUin", 0L),
                            chatType = o.optInt("chatType", 1),
                            title = o.optString("title", ""),
                            lastOpenedAt = o.optLong("lastOpenedAt", 0L),
                        ),
                    )
                }
            }.sortedByDescending { it.lastOpenedAt }.take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    fun record(
        context: Context,
        peerUid: String,
        peerUin: Long,
        chatType: Int,
        title: String,
    ) {
        if (peerUid.isBlank()) return
        if (chatType != 1 && chatType != 2) return
        val now = System.currentTimeMillis()
        val next = (
            listOf(
                Entry(peerUid, peerUin, chatType, title.ifBlank { peerUid }, now),
            ) + load(context).filterNot { it.peerUid == peerUid && it.chatType == chatType }
            )
            .sortedByDescending { it.lastOpenedAt }
            .take(MAX_ENTRIES)
        save(context, next)
    }

    private fun save(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("peerUid", e.peerUid)
                    .put("peerUin", e.peerUin)
                    .put("chatType", e.chatType)
                    .put("title", e.title)
                    .put("lastOpenedAt", e.lastOpenedAt),
            )
        }
        context.applicationContext
            .getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY, arr.toString()) }
    }
}
