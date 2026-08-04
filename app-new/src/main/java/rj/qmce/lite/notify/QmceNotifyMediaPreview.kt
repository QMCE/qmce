package rj.qmce.lite.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IMsgOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import rj.qmce.lite.data.chat.RichMediaRepository
import rj.qmce.lite.data.emotion.EmotionRepository
import rj.qmce.lite.data.emotion.QFaceRemoteStore
import rj.qmce.lite.kernel.KernelBridge
import java.io.File
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object QmceNotifyMediaPreview {
    private const val TAG = "QmceNotifyMedia"
    private const val WAIT_MS = 8_000L

    fun resolveImageContentUri(context: Context, contact: RecentContactInfo): Uri? {
        if (contact.msgId <= 0L) return null
        val peerUid = QmceMessageNotifier.peerKey(contact)
        if (peerUid.isBlank()) return null
        val record = loadRecord(contact.chatType, peerUid, contact.msgId) ?: return null
        resolvePicUri(context, contact, record, peerUid)?.let { return it }
        resolveFaceUri(context, record)?.let { return it }
        resolveMarketFaceUri(context, record)?.let { return it }
        return null
    }

    private fun resolvePicUri(
        context: Context,
        contact: RecentContactInfo,
        record: MsgRecord,
        peerUid: String,
    ): Uri? {
        val picMsgElement = record.elements?.firstOrNull { it.picElement != null } ?: return null
        val elementId = picMsgElement.elementId
        var paths = RichMediaRepository.resolveLocalPicturePaths(picMsgElement)
        if (paths.isEmpty() && elementId > 0L) {
            RichMediaRepository.requestImageThumbnail(
                messageId = contact.msgId,
                peerUid = peerUid,
                chatType = contact.chatType,
                elementId = elementId,
            )
            val deadline = System.currentTimeMillis() + WAIT_MS
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(250)
                val refreshed = loadRecord(contact.chatType, peerUid, contact.msgId)
                    ?.elements
                    ?.firstOrNull { it.elementId == elementId && it.picElement != null }
                    ?: picMsgElement
                paths = RichMediaRepository.resolveLocalPicturePaths(refreshed)
                if (paths.isNotEmpty()) break
            }
        }
        val src = paths.map(::File).firstOrNull { it.isFile } ?: return null
        return copyToNotifyCache(context, src)
    }

    private fun resolveFaceUri(context: Context, record: MsgRecord): Uri? {
        val face = record.elements?.firstOrNull { it.faceElement != null }?.faceElement
            ?: return null
        val faceIndex = face.faceIndex
        val emojiId = QFaceRemoteStore.resolveEmojiId(
            serverId = null,
            faceIndex = faceIndex,
            stickerId = face.stickerId,
        ) ?: faceIndex.toString()
        val file = QFaceRemoteStore.ensureAsset(emojiId, QFaceRemoteStore.Kind.Png)
            ?: run {
                val selection = EmotionRepository.systemFaceForMessage(
                    faceType = face.faceType,
                    ntFaceIndex = faceIndex,
                    label = face.faceText.orEmpty(),
                    packId = face.packId,
                    imageType = face.imageType,
                    stickerId = face.stickerId,
                    stickerType = face.stickerType,
                    resultId = face.resultId,
                    surpriseId = face.surpriseId,
                )
                EmotionRepository.qfacePngFileForNotify(selection)
            }
        return file?.takeIf { it.isFile }?.let { copyToNotifyCache(context, it) }
    }

    private fun resolveMarketFaceUri(context: Context, record: MsgRecord): Uri? {
        val market = record.elements?.firstOrNull { it.marketFaceElement != null }
            ?.marketFaceElement
            ?: return null
        val paths = EmotionRepository.cachedMarketFacePaths(context, market)
        val staticPreferred = paths.firstOrNull { path ->
            val lower = path.lowercase()
            lower.endsWith("_aio.png") || lower.endsWith("_thu.png") || lower.endsWith(".png")
        }
        val candidate = (listOfNotNull(market.staticFacePath, staticPreferred) + paths)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .map(::File)
            .firstOrNull { it.isFile }
            ?: return null
        return copyToNotifyCache(context, candidate)
    }

    private fun loadRecord(chatType: Int, peerUid: String, msgId: Long): MsgRecord? {
        val service = KernelBridge.getMsgService() ?: return null
        val latch = CountDownLatch(1)
        var result: MsgRecord? = null
        val ok = runCatching {
            service.getMsgsByMsgId(
                Contact(chatType, peerUid, ""),
                arrayListOf(msgId),
                object : IMsgOperateCallback {
                    override fun onResult(
                        errorCode: Int,
                        errorMessage: String?,
                        records: ArrayList<MsgRecord>?,
                    ) {
                        result = records?.firstOrNull()
                        latch.countDown()
                    }
                },
            )
            true
        }.getOrDefault(false)
        if (!ok) return null
        latch.await(4, TimeUnit.SECONDS)
        return result
    }

    private fun copyToNotifyCache(context: Context, src: File): Uri? {
        val dir = File(context.cacheDir, "notify-media").apply { mkdirs() }
        val dest = File(dir, src.name.ifBlank { "thumb_${src.hashCode()}.jpg" })
        return runCatching {
            src.copyTo(dest, overwrite = true)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                dest,
            )
            runCatching {
                context.grantUriPermission(
                    "com.android.systemui",
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            uri
        }.onFailure { Log.w(TAG, "copy notify media failed", it) }.getOrNull()
    }
}
