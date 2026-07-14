package rj.qmce.lite.viewmodel

import androidx.lifecycle.ViewModel
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rj.qmce.lite.data.chat.ChatSettingsRepository

data class ChatSettingsState(
    val chatType: Int = 1,
    val peerUid: String = "",
    val peerUin: Long = 0L,
    val displayName: String = "",
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

class ChatSettingsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChatSettingsState())
    val state: StateFlow<ChatSettingsState> = _state

    private var loadedKey: String? = null

    fun load(contact: RecentContactInfo, peerUin: Long, peerUid: String, displayName: String) {
        val key = "${contact.chatType}:$peerUid:$peerUin"
        if (loadedKey == key) return
        loadedKey = key
        _state.value = ChatSettingsState(
            chatType = contact.chatType,
            peerUid = peerUid,
            peerUin = peerUin,
            displayName = displayName,
            pinned = contact.topFlag.toInt() != 0,
            muted = contact.isMsgDisturb || (contact.shieldFlag != 0L && contact.shieldFlag != 1L),
        )
    }

    fun togglePinned() {
        val current = _state.value
        if (current.busy) return
        update(
            target = current.pinned,
            request = { callback ->
                ChatSettingsRepository.setTop(
                    current.chatType,
                    current.peerUid,
                    current.peerUin,
                    !current.pinned,
                    callback,
                )
            },
            apply = { copy(pinned = it) },
        )
    }

    fun toggleMuted() {
        val current = _state.value
        if (current.busy) return
        update(
            target = current.muted,
            request = { callback ->
                ChatSettingsRepository.setMuted(
                    current.chatType,
                    current.peerUid,
                    current.peerUin,
                    !current.muted,
                    callback,
                )
            },
            apply = { copy(muted = it) },
        )
    }

    private fun update(
        target: Boolean,
        request: ((Boolean, String?) -> Unit) -> Boolean,
        apply: ChatSettingsState.(Boolean) -> ChatSettingsState,
    ) {
        val before = _state.value
        _state.value = before.copy(busy = true, error = null)
        val requested = request { success, message ->
            val latest = _state.value
            _state.value = if (success) {
                latest.copy(busy = false, error = null).apply(!target)
            } else {
                latest.copy(busy = false, error = message?.takeIf { it.isNotBlank() } ?: "设置失败")
            }
        }
        if (!requested) {
            _state.value = _state.value.copy(busy = false, error = "设置服务不可用")
        }
    }
}
