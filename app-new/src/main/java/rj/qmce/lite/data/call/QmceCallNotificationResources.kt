package rj.qmce.lite.data.call

import android.content.Context

object QmceCallNotificationResources {
    @JvmStatic
    fun getText(
        @Suppress("UNUSED_PARAMETER") context: Context,
        originalResourceId: Int
    ): CharSequence =
        when (originalResourceId) {
            OFFICIAL_AUDIO_CALLING -> "语音通话中"
            OFFICIAL_VIDEO_CALLING -> "视频通话中"
            else -> "QQ 通话中"
        }

    private const val OFFICIAL_AUDIO_CALLING = 0x7e120510
    private const val OFFICIAL_VIDEO_CALLING = 0x7e1206e3
}
