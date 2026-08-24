package rj.qmce.lite.wear

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import rj.qmce.lite.viewmodel.SettingsViewModel

data class WatchlistEntry(
    val peerUid: String,
    val peerUin: Long,
    val chatType: Int,
    val name: String,
)

object QmceWatchlistStore {
    const val MAX_ENTRIES = 2

    private val _entries = MutableStateFlow<List<WatchlistEntry>>(emptyList())
    val entries: StateFlow<List<WatchlistEntry>> = _entries.asStateFlow()

    fun load(context: Context): List<WatchlistEntry> {
        val loaded = readFromPrefs(context)
        _entries.value = loaded
        return loaded
    }

    fun save(context: Context, entries: List<WatchlistEntry>) {
        val normalized = entries.take(MAX_ENTRIES)
        writeToPrefs(context, normalized)
        _entries.value = normalized
    }

    fun add(context: Context, entry: WatchlistEntry): Boolean {
        val current = load(context).toMutableList()
        if (current.any { it.peerUid == entry.peerUid && it.chatType == entry.chatType }) {
            return false
        }
        if (current.size >= MAX_ENTRIES) return false
        current.add(entry)
        save(context, current)
        return true
    }

    fun remove(context: Context, peerUid: String, chatType: Int) {
        save(context, load(context).filterNot { it.peerUid == peerUid && it.chatType == chatType })
    }

    private fun readFromPrefs(context: Context): List<WatchlistEntry> {
        val raw = context.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).getString(SettingsViewModel.KEY_WATCHLIST_JSON, "[]").orEmpty()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val uid = o.optString("peerUid")
                    if (uid.isBlank()) continue
                    add(
                        WatchlistEntry(
                            peerUid = uid,
                            peerUin = o.optLong("peerUin"),
                            chatType = o.optInt("chatType", 1),
                            name = o.optString("name").ifBlank { uid },
                        ),
                    )
                }
            }.take(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    private fun writeToPrefs(context: Context, entries: List<WatchlistEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("peerUid", e.peerUid)
                    .put("peerUin", e.peerUin)
                    .put("chatType", e.chatType)
                    .put("name", e.name),
            )
        }
        context.getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SettingsViewModel.KEY_WATCHLIST_JSON, arr.toString())
            .apply()
    }
}
