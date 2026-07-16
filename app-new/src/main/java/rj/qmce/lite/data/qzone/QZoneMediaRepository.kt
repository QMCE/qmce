package rj.qmce.lite.data.qzone

import android.content.Context
import rj.qmce.lite.data.media.MediaStoreSaver

class QZoneMediaRepository {
    private val mediaStoreSaver = MediaStoreSaver()

    suspend fun saveImage(context: Context, sourceUrl: String): Result<Unit> = runCatching {
        mediaStoreSaver.saveImage(context, sourceUrl).getOrThrow()
    }
}
