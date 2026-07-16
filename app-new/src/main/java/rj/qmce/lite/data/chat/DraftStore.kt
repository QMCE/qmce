package rj.qmce.lite.data.chat

import android.content.Context

object DraftStore {
    private const val PREFS_NAME = "qmce_drafts"

    fun save(context: Context, peerUid: String, chatType: Int, text: String) {
        val k = key(peerUid, chatType)
        if (text.isBlank()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(k).apply()
        } else {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(k, text)
                .apply()
        }
    }

    fun load(context: Context, peerUid: String, chatType: Int): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(peerUid, chatType), "") ?: ""

    fun clear(context: Context, peerUid: String, chatType: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(key(peerUid, chatType)).apply()
    }

    private fun key(peerUid: String, chatType: Int) = "${chatType}:${peerUid}"
}
