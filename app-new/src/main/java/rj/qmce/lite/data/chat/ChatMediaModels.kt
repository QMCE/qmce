package rj.qmce.lite.data.chat

data class RichMediaKey(
    val messageId: Long,
    val elementId: Long,
)

data class PttMediaRef(
    val messageId: Long,
    val elementId: Long,
    val filePath: String?,
    val md5Hex: String?,
    val fileName: String?,
    val importRichMediaContext: ByteArray?,
    val fileUuid: String?,
    val durationSeconds: Int,
)

enum class PttPlaybackPhase {
    Idle,
    Preparing,
    Playing,
    Paused,
    Failed,
}

data class PttPlaybackState(
    val messageId: Long,
    val phase: PttPlaybackPhase = PttPlaybackPhase.Idle,
    val positionMillis: Int = 0,
    val durationMillis: Int = 0,
    val error: String? = null,
)
