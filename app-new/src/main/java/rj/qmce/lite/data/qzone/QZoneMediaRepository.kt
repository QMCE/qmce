package rj.qmce.lite.data.qzone

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QZoneMediaRepository {
    suspend fun saveImage(context: Context, sourceUrl: String): Result<Unit> = runCatching {
        val url = URL(sourceUrl)
        require(url.protocol == "https" || url.protocol == "http") { "图片地址不可用" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        connection.connect()
        check(connection.responseCode in 200..299) { "下载失败：HTTP ${connection.responseCode}" }
        val mimeType = connection.contentType?.substringBefore(';')
            ?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val displayName = "QMCE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())}.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QMCE")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建媒体文件")
        try {
            connection.inputStream.use { input ->
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                    ?: error("无法写入媒体文件")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
