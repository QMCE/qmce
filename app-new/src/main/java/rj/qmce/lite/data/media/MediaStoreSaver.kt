package rj.qmce.lite.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStoreSaver {
    suspend fun saveImage(context: Context, source: String): Result<Unit> = runCatching {
        require(source.isNotBlank()) { "图片地址不可用" }
        val opened = openSource(context, source)
        try {
            val mimeType = opened.mimeType
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
                opened.input.use { input ->
                    resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                        ?: error("无法写入媒体文件")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val completed = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, completed, null, null)
                }
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } finally {
            opened.disconnect()
        }
    }

    private fun openSource(context: Context, source: String): OpenedSource {
        val uri = runCatching { Uri.parse(source) }.getOrNull()
        return when (uri?.scheme?.lowercase(Locale.ROOT)) {
            "http", "https" -> {
                val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
                connection.connect()
                check(connection.responseCode in 200..299) {
                    "下载失败：HTTP ${connection.responseCode}"
                }
                OpenedSource(
                    input = connection.inputStream,
                    mimeType = connection.contentType?.substringBefore(';')
                        ?.takeIf { it.startsWith("image/") }
                        ?: "image/jpeg",
                    disconnect = connection::disconnect,
                )
            }

            "content" -> OpenedSource(
                input = context.contentResolver.openInputStream(uri) ?: error("无法读取图片"),
                mimeType = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
                    ?: "image/jpeg",
            )

            else -> {
                val file = File(source.removePrefix("file://"))
                check(file.isFile && file.length() > 0L) { "本地图片不可用" }
                OpenedSource(
                    input = file.inputStream(),
                    mimeType = guessMimeType(file.name),
                )
            }
        }
    }

    private fun guessMimeType(name: String): String =
        when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }

    private class OpenedSource(
        val input: InputStream,
        val mimeType: String,
        private val disconnect: () -> Unit = {},
    ) {
        fun disconnect() = disconnect.invoke()
    }
}
