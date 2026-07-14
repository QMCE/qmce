package rj.qmce.lite.viewmodel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SentMessageEvent(
    val peerUid: String,
    val msgTime: Long,
    val text: String
)

object MessageSendBus {
    private val _events = MutableSharedFlow<SentMessageEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    suspend fun emit(event: SentMessageEvent) {
        _events.emit(event)
    }
}
