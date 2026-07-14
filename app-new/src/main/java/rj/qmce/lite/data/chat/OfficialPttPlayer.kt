package rj.qmce.lite.data.chat

import android.util.Log
import com.tencent.watch.aio_impl.ui.cell.ptt.AIOPttAudioPlayerStateListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

object OfficialPttPlayer {
    private const val TAG = "QMCE"

    private val ownedMessageIds = ConcurrentHashMap.newKeySet<Long>()
    private val listeners = ConcurrentHashMap<Long, AIOPttAudioPlayerStateListener>()
    private val _states = MutableStateFlow<Map<Long, PttPlaybackState>>(emptyMap())
    val states: StateFlow<Map<Long, PttPlaybackState>> = _states

    fun toggle(media: PttMediaRef) {
        if (media.messageId <= 0L) {
            publish(media.messageId) { it.copy(phase = PttPlaybackPhase.Failed, error = "语音消息无效") }
            return
        }
        val path = RichMediaRepository.resolvePttPath(media)
        if (path.isNullOrBlank()) {
            publish(media.messageId) {
                it.copy(phase = PttPlaybackPhase.Failed, error = "语音文件路径不可用")
            }
            return
        }

        installListener(media.messageId, path)
        val current = _states.value[media.messageId]
        if (current?.phase != PttPlaybackPhase.Playing) {
            publish(media.messageId) {
                it.copy(
                    phase = PttPlaybackPhase.Preparing,
                    error = null,
                    durationMillis = maxOf(it.durationMillis, media.durationSeconds * 1_000),
                )
            }
        }
        runCatching {
            OfficialPttPlayerBridge.toggle(media.messageId, path, current?.positionMillis ?: 0)
        }.onFailure {
            Log.w(TAG, "ptt: toggle failed msg=${media.messageId}", it)
            publish(media.messageId) {
                it.copy(phase = PttPlaybackPhase.Failed, error = "无法播放此语音")
            }
        }
    }

    fun stopAndRelease() {
        runCatching { OfficialPttPlayerBridge.stop() }
            .onFailure { Log.w(TAG, "ptt: stop failed", it) }
        ownedMessageIds.forEach(OfficialPttPlayerBridge::unregister)
        ownedMessageIds.clear()
        listeners.clear()
        _states.value = emptyMap()
    }

    private fun installListener(messageId: Long, path: String) {
        val listener = listeners.getOrPut(messageId) { createListener(messageId) }
        OfficialPttPlayerBridge.register(messageId, path, listener)
        ownedMessageIds.add(messageId)
    }

    private fun createListener(expectedMessageId: Long) = object : AIOPttAudioPlayerStateListener {
        override fun a(msgId: Long, speed: Float) = Unit

        override fun b(msgId: Long, isNearToEar: Boolean) = Unit

        override fun c(msgId: Long, path: String, currentPosition: Int, duration: Int) {
            if (msgId != expectedMessageId) return
            publish(msgId) {
                it.copy(
                    phase = PttPlaybackPhase.Playing,
                    positionMillis = currentPosition.coerceAtLeast(0),
                    durationMillis = duration.coerceAtLeast(it.durationMillis),
                    error = null,
                )
            }
        }

        override fun d(msgId: Long, path: String) {
            if (msgId != expectedMessageId) return
            publish(msgId) { it.copy(phase = PttPlaybackPhase.Playing, error = null) }
        }

        override fun e(msgId: Long, path: String, currentPosition: Int) {
            if (msgId != expectedMessageId) return
            publish(msgId) {
                it.copy(
                    phase = PttPlaybackPhase.Paused,
                    positionMillis = currentPosition.coerceAtLeast(0),
                )
            }
        }

        override fun f(msgId: Long, path: String) {
            if (msgId != expectedMessageId) return
            publish(msgId) {
                it.copy(phase = PttPlaybackPhase.Failed, error = "无法播放此语音")
            }
        }
    }

    private fun publish(
        messageId: Long,
        transform: (PttPlaybackState) -> PttPlaybackState,
    ) {
        synchronized(_states) {
            val current = _states.value[messageId] ?: PttPlaybackState(messageId)
            _states.value = _states.value + (messageId to transform(current))
        }
    }
}
