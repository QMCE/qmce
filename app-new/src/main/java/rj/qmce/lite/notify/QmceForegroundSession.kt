package rj.qmce.lite.notify

import java.util.concurrent.atomic.AtomicReference

/** Tracks the chat currently open in UI so message notifications can be suppressed. */
object QmceForegroundSession {
    data class Key(val peerUid: String, val chatType: Int)

    private val current = AtomicReference<Key?>(null)
    @Volatile
    var appInForeground: Boolean = false

    fun setActiveChat(peerUid: String?, chatType: Int?) {
        if (peerUid.isNullOrBlank() || chatType == null) {
            current.set(null)
        } else {
            current.set(Key(peerUid, chatType))
        }
    }

    fun matches(peerUid: String, chatType: Int): Boolean {
        if (!appInForeground) return false
        val active = current.get() ?: return false
        return active.peerUid == peerUid && active.chatType == chatType
    }
}
