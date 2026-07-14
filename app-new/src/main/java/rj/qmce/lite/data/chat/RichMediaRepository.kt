package rj.qmce.lite.data.chat

import android.util.Log
import com.tencent.mobileqq.aio.msglist.holder.base.PicSize
import com.tencent.qqnt.kernel.api.IMsgService
import com.tencent.qqnt.kernel.nativeinterface.FileTransNotifyInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.RichMediaElementGetReq
import com.tencent.qqnt.kernel.nativeinterface.RichMediaFilePathInfo
import com.tencent.watch.aio_impl.ext.AIOPicDownloader
import rj.qmce.lite.kernel.KernelBridge
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object RichMediaRepository {
    private const val TAG = "QMCE"
    private val pendingRequests = ConcurrentHashMap.newKeySet<RichMediaKey>()
    @Volatile
    private var activeMessageService: IMsgService? = null

    fun attachMessageService(service: IMsgService) {
        activeMessageService = service
    }

    fun detachMessageService(service: IMsgService?) {
        if (activeMessageService === service) activeMessageService = null
    }

    fun isPending(key: RichMediaKey): Boolean = key in pendingRequests

    fun requestImageThumbnail(
        messageId: Long,
        peerUid: String,
        chatType: Int,
        elementId: Long,
    ): Boolean {
        if (messageId <= 0L || elementId <= 0L || peerUid.isBlank()) return false
        val key = RichMediaKey(messageId, elementId)
        if (!pendingRequests.add(key)) return true

        val service = activeMessageService ?: KernelBridge.getMsgService()
        if (service == null) {
            pendingRequests.remove(key)
            return false
        }

        return runCatching {
            service.getRichMediaElement(
                RichMediaElementGetReq().apply {
                    msgId = messageId
                    this.peerUid = peerUid
                    this.chatType = chatType
                    this.elementId = elementId
                    downloadType = 2
                    thumbSize = 198
                    downSourceType = 1
                    triggerType = 0
                },
            )
            Log.d(TAG, "richMedia: request image thumbnail msg=$messageId, element=$elementId")
            true
        }.getOrElse {
            pendingRequests.remove(key)
            Log.w(TAG, "richMedia: request image thumbnail failed", it)
            false
        }
    }

    fun consumeDownloadCompletion(notify: FileTransNotifyInfo): Boolean {
        val key = RichMediaKey(notify.msgId, notify.msgElementId)
        if (!pendingRequests.remove(key)) return false
        Log.d(
            TAG,
            "richMedia: complete msg=${notify.msgId}, element=${notify.msgElementId}, " +
                "status=${notify.trasferStatus}, error=${notify.fileErrCode}",
        )
        return true
    }

    fun resolvePttPath(media: PttMediaRef): String? {
        media.filePath
            ?.removePrefix("file://")
            ?.takeIf(String::isNotBlank)
            ?.let { return it }

        val service = activeMessageService ?: KernelBridge.getMsgService() ?: return null
        val resolved = runCatching {
            service.assembleMobileQQRichMediaFilePath(
                RichMediaFilePathInfo(
                    4,
                    3,
                    media.md5Hex,
                    media.fileName,
                    1,
                    0,
                    media.importRichMediaContext,
                    media.fileUuid,
                    false,
                ),
            )
        }.onFailure {
            Log.w(TAG, "richMedia: resolve PTT path failed msg=${media.messageId}", it)
        }.getOrNull()

        return resolved
            ?.removePrefix("file://")
            ?.takeIf(String::isNotBlank)
    }

    fun resolveLocalPicturePaths(element: MsgElement): List<String> =
        PicSize.values().mapNotNull { size ->
            runCatching {
                val downloader = AIOPicDownloader::class.java.getField("a").get(null) as AIOPicDownloader
                downloader.d(element, size)
            }.getOrNull()
                ?.takeIf { path -> path.isNotBlank() && File(path).isFile }
        }.distinct()
}
