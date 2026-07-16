package rj.qmce.lite.data.qzone

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.tencent.mobileqq.activity.photo.LocalMediaInfo
import com.tencent.watch.qzone_impl.common.QZoneBusinessLooper
import com.tencent.watch.qzone_impl.common.task.QZoneTask
import com.tencent.watch.qzone_impl.feed.model.BusinessFeedData
import com.tencent.watch.qzone_impl.protocol.request.QZoneAddCommentRequest
import com.tencent.watch.qzone_impl.publish.business.model.ImageInfo
import com.tencent.watch.qzone_impl.publish.business.model.MediaWrapper
import com.tencent.watch.qzone_impl.publish.business.model.QzoneShuoShuoParams
import com.tencent.watch.qzone_impl.publish.business.publishqueue.QZonePublishQueue
import com.tencent.watch.qzone_impl.publish.business.task.QZoneLikeFeedTask
import com.tencent.watch.qzone_impl.publish.business.task.QZoneUploadShuoShuoTask
import com.tencent.watch.qzone_impl.service.QZoneWriteOperationService
import java.io.File
import java.util.UUID

class QZoneWriteRepository {

    suspend fun publishText(content: String): Result<Unit> = runCatching {
        val params = QzoneShuoShuoParams().apply { a = content }
        QZoneWriteOperationService.h().i(params)
    }

    suspend fun publishImages(
        context: Context,
        content: String,
        uris: List<Uri>,
    ): Result<Unit> = runCatching {
        val files = uris.mapIndexed { index, uri -> copyUriToQZoneFile(context, uri, index) }
        val params = QzoneShuoShuoParams().apply {
            a = content
            b = files.map { it.absolutePath }
            c = ArrayList(files.map(::toLocalMediaInfo))
            e = ArrayList(files.map { file ->
                MediaWrapper(ImageInfo(file.absolutePath).apply { mDescription = content })
            })
        }
        val task = QZoneUploadShuoShuoTask(6, 1, params).apply {
            uploadEntrance = 0
            refer = null
        }
        QZonePublishQueue.e().b(task)
    }

    suspend fun comment(data: BusinessFeedData, content: String): Result<Unit> = runCatching {
        val feedCommInfo = data.feedCommInfo
        val ownerUin = runCatching { data.user?.uin }.getOrNull() ?: data.owner_uin
        val cellId = runCatching { data.idInfo?.cellId }.getOrNull().orEmpty()
        val busiParam = runCatching { data.operationInfo?.busiParam }.getOrNull()
        val request = QZoneAddCommentRequest(
            feedCommInfo.appid,
            ownerUin,
            cellId,
            content,
            null,
            false,
            busiParam,
        )
        val task = QZoneTask(request, null, QZoneWriteOperationService.h(), 0)
        task.addParameter("ugckey", feedCommInfo.ugckey)
        task.addParameter("feedkey", feedCommInfo.feedskey)
        task.addParameter("uniKey", UUID.randomUUID().toString())
        task.addParameter("clickScene", 0)
        QZoneBusinessLooper.a().c(task)
    }

    suspend fun updateLike(data: BusinessFeedData, liked: Boolean): Result<Unit> = runCatching {
        val feedCommInfo = data.feedCommInfo
        val params = QZoneWriteOperationService.LikeParams().apply {
            a = feedCommInfo.ugckey
            b = feedCommInfo.curlikekey
            c = feedCommInfo.orglikekey
            d = liked
            e = feedCommInfo.appid
            f = data.operationInfo?.busiParam?.let(::HashMap)
            g = -1
            h = data
            i = data.feedType
        }
        val task = QZoneLikeFeedTask(null, params, 1)
        runCatching { task.javaClass.getField("clientKey").set(task, feedCommInfo.clientkey) }
        QZonePublishQueue.e().b(task)
    }

    private fun copyUriToQZoneFile(context: Context, uri: Uri, index: Int): File {
        val directory = File(
            context.getExternalFilesDir("qzone_photo") ?: context.cacheDir,
            "publish",
        ).apply { mkdirs() }
        val target = File(directory, "qzone_${System.currentTimeMillis()}_${index}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("无法读取图片")
        check(target.isFile && target.length() > 0L) { "图片为空" }
        return target
    }

    private fun toLocalMediaInfo(file: File): LocalMediaInfo = LocalMediaInfo().apply {
        c = file.absolutePath
        C = 0
        runCatching {
            BitmapFactory.Options().also { options ->
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(file.absolutePath, options)
                E = options.outWidth
                F = options.outHeight
            }
        }
    }
}
