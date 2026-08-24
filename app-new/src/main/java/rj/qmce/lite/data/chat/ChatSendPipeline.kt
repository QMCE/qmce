package rj.qmce.lite.data.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.PicElement
import com.tencent.qqnt.kernel.nativeinterface.RichMediaFilePathInfo
import java.io.File

/**
 * Shared helpers for preparing outbound media elements off the main thread.
 */
object ChatSendPipeline {

    fun buildPicElementFromUri(
        context: Context,
        uri: Uri,
        pathResolver: (RichMediaFilePathInfo) -> String?,
        md5File: (File) -> String,
    ): MsgElement {
        val tmpFile = File(context.cacheDir, "send_img_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmpFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("读取图片失败")

        val md5 = md5File(tmpFile)
        val fileName = tmpFile.name
        val fileSize = tmpFile.length()

        val origPath = pathResolver(
            RichMediaFilePathInfo(2, 0, md5, fileName, 1, 0, null, "", true),
        ) ?: tmpFile.absolutePath

        val thumbPath = pathResolver(
            RichMediaFilePathInfo(2, 0, md5, fileName, 2, 720, null, "", true),
        )

        if (origPath != tmpFile.absolutePath) {
            runCatching { tmpFile.copyTo(File(origPath), overwrite = true) }
        }
        if (thumbPath != null && thumbPath != tmpFile.absolutePath) {
            runCatching { tmpFile.copyTo(File(thumbPath), overwrite = true) }
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(origPath, opts)
        val width = opts.outWidth.takeIf { it > 0 } ?: 800
        val height = opts.outHeight.takeIf { it > 0 } ?: 600

        val ext = fileName.substringAfterLast('.', "").lowercase()
        val picType = when (ext) {
            "jpg", "jpeg" -> 1000
            "png" -> 1001
            "webp" -> 1002
            "gif" -> 2000
            "bmp" -> 1005
            else -> 1001
        }

        val picElement = PicElement().apply {
            sourcePath = origPath
            this.fileName = fileName
            this.fileSize = fileSize
            md5HexStr = md5
            picWidth = width
            picHeight = height
            this.picType = picType
            picSubType = 0
            original = true
            storeID = 0
        }

        return MsgElement().apply {
            elementType = 2
            elementId = 0
            this.picElement = picElement
        }
    }
}
