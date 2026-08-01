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
        val opened = openSource(context, source, preferVideo = false)
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

    suspend fun saveVideo(context: Context, source: String): Result<Unit> = runCatching {
        require(source.isNotBlank()) { "视频地址不可用" }
        val opened = openSource(context, source, preferVideo = true)
        try {
            val mimeType = opened.mimeType.takeIf { it.startsWith("video/") } ?: "video/mp4"
            val extension = when (mimeType) {
                "video/webm" -> "webm"
                "video/3gpp" -> "3gp"
                else -> "mp4"
            }
            val displayName = "QMCE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())}.$extension"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/QMCE")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建视频文件")
            try {
                opened.input.use { input ->
                    resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
                        ?: error("无法写入视频文件")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val completed = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
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

    private fun openSource(context: Context, source: String, preferVideo: Boolean): OpenedSource {
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
                val contentType = connection.contentType?.substringBefore(';')
                OpenedSource(
                    input = connection.inputStream,
                    mimeType = when {
                        preferVideo -> contentType?.takeIf { it.startsWith("video/") } ?: "video/mp4"
                        else -> contentType?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
                    },
                    disconnect = connection::disconnect,
                )
            }

            "content" -> OpenedSource(
                input = context.contentResolver.openInputStream(uri) ?: error("无法读取媒体"),
                mimeType = context.contentResolver.getType(uri)
                    ?: if (preferVideo) "video/mp4" else "image/jpeg",
            )

            else -> {
                val file = File(source.removePrefix("file://"))
                check(file.isFile && file.length() > 0L) { "本地媒体不可用" }
                OpenedSource(
                    input = file.inputStream(),
                    mimeType = if (preferVideo) guessVideoMimeType(file.name) else guessMimeType(file.name),
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

    private fun guessVideoMimeType(name: String): String =
        when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "webm" -> "video/webm"
            "3gp", "3gpp" -> "video/3gpp"
            else -> "video/mp4"
        }

    private class OpenedSource(
        val input: InputStream,
        val mimeType: String,
        private val disconnect: () -> Unit = {},
    ) {
        fun disconnect() = disconnect.invoke()
    }
}
