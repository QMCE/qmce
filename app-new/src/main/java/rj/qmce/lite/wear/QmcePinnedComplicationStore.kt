package rj.qmce.lite.wear

import android.content.Context
import rj.qmce.lite.viewmodel.SettingsViewModel

data class PinnedChat(
    val peerUid: String,
    val chatType: Int,
    val name: String,
)

object QmcePinnedComplicationStore {
    private const val KEY_UID = "pinned_complication_uid"
    private const val KEY_TYPE = "pinned_complication_type"
    private const val KEY_NAME = "pinned_complication_name"

    fun load(context: Context): PinnedChat? {
        val prefs = context.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val uid = prefs.getString(KEY_UID, null)?.takeIf { it.isNotBlank() } ?: return null
        return PinnedChat(
            peerUid = uid,
            chatType = prefs.getInt(KEY_TYPE, 1),
            name = prefs.getString(KEY_NAME, uid).orEmpty().ifBlank { uid },
        )
    }

    fun save(context: Context, chat: PinnedChat?) {
        val prefs = context.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        prefs.edit().apply {
            if (chat == null) {
                remove(KEY_UID)
                remove(KEY_TYPE)
                remove(KEY_NAME)
            } else {
                putString(KEY_UID, chat.peerUid)
                putInt(KEY_TYPE, chat.chatType)
                putString(KEY_NAME, chat.name)
            }
        }.apply()
    }
}
