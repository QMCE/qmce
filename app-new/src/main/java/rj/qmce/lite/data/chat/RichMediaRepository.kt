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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object RichMediaRepository {
    private const val TAG = "QMCE"
    private const val REQUEST_TIMEOUT_SECONDS = 20L
    private val pendingRequests = ConcurrentHashMap.newKeySet<RichMediaKey>()
    private val pendingTimeouts = ConcurrentHashMap<RichMediaKey, ScheduledFuture<*>>()
    private val requestStates = ConcurrentHashMap<RichMediaKey, RichMediaRequestState>()
    private val timeoutExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "QMCE-RichMediaTimeout").apply { isDaemon = true }
    }

    @Volatile
    private var activeMessageService: IMsgService? = null

    @Volatile
    private var invalidationListener: (() -> Unit)? = null

    fun attachMessageService(service: IMsgService) {
        activeMessageService = service
    }

    fun detachMessageService(service: IMsgService?) {
        if (activeMessageService === service) activeMessageService = null
    }

    fun setInvalidationListener(listener: (() -> Unit)?) {
        invalidationListener = listener
    }

    fun requestState(key: RichMediaKey): RichMediaRequestState =
        requestStates[key] ?: RichMediaRequestState.Idle

    fun isPending(key: RichMediaKey): Boolean = requestState(key) is RichMediaRequestState.Loading

    fun fileDownloadUnavailableReason(): String = "不支持文件下载"

    fun requestImageThumbnail(
        messageId: Long,
        peerUid: String,
        chatType: Int,
        elementId: Long,
    ): Boolean {
        if (messageId <= 0L || elementId <= 0L || peerUid.isBlank()) return false
        val key = RichMediaKey(messageId, elementId)
        if (!beginRequest(key)) return true

        val service = activeMessageService ?: KernelBridge.getMsgService()
        if (service == null) {
            finishRequest(key, RichMediaRequestState.Failed("消息服务不可用"))
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
            finishRequest(key, RichMediaRequestState.Failed("图片请求失败"))
            Log.w(TAG, "richMedia: request image thumbnail failed", it)
            false
        }
    }

    fun requestPttAudio(
        messageId: Long,
        peerUid: String,
        chatType: Int,
        elementId: Long,
    ): Boolean {
        if (messageId <= 0L || elementId <= 0L || peerUid.isBlank()) return false
        val key = RichMediaKey(messageId, elementId)
        if (!beginRequest(key)) return true

        val service = activeMessageService ?: KernelBridge.getMsgService()
        if (service == null) {
            finishRequest(key, RichMediaRequestState.Failed("消息服务不可用"))
            return false
        }

        return runCatching {
            service.getRichMediaElement(
                RichMediaElementGetReq().apply {
                    msgId = messageId
                    this.peerUid = peerUid
                    this.chatType = chatType
                    this.elementId = elementId
                    downloadType = 1
                    downSourceType = 1
                    triggerType = 1
                },
            )
            Log.d(TAG, "richMedia: request ptt audio msg=$messageId, element=$elementId")
            true
        }.getOrElse {
            finishRequest(key, RichMediaRequestState.Failed("语音请求失败"))
            Log.w(TAG, "richMedia: request ptt audio failed", it)
            false
        }
    }

    fun requestFile(
        messageId: Long,
        peerUid: String,
        chatType: Int,
        elementId: Long,
        filePath: String? = null,
    ): Boolean {
        if (messageId <= 0L || elementId <= 0L || peerUid.isBlank()) return false
        val key = RichMediaKey(messageId, elementId)
        requestStates[key] = RichMediaRequestState.Failed(fileDownloadUnavailableReason())
        invalidationListener?.invoke()
        return false
    }

    fun consumeDownloadCompletion(notify: FileTransNotifyInfo): Boolean {
        val key = RichMediaKey(notify.msgId, notify.msgElementId)
        if (!pendingRequests.contains(key)) return false
        val state = if (notify.fileErrCode == 0L) {
            RichMediaRequestState.Idle
        } else {
            RichMediaRequestState.Failed("下载失败 (${notify.fileErrCode})")
        }
        finishRequest(key, state)
        Log.d(
            TAG,
            "richMedia: complete msg=${notify.msgId}, element=${notify.msgElementId}, " +
                    "status=${notify.trasferStatus}, error=${notify.fileErrCode}",
        )
        return true
    }

    private fun scheduleTimeout(key: RichMediaKey) {
        pendingTimeouts[key] = timeoutExecutor.schedule({
            if (pendingRequests.remove(key)) {
                pendingTimeouts.remove(key)
                requestStates[key] = RichMediaRequestState.Failed("加载超时")
                Log.w(
                    TAG,
                    "richMedia: request timed out msg=${key.messageId}, element=${key.elementId}"
                )
                invalidationListener?.invoke()
            }
        }, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private fun beginRequest(key: RichMediaKey): Boolean {
        if (!pendingRequests.add(key)) return false
        requestStates[key] = RichMediaRequestState.Loading
        scheduleTimeout(key)
        invalidationListener?.invoke()
        return true
    }

    private fun finishRequest(key: RichMediaKey, state: RichMediaRequestState) {
        pendingRequests.remove(key)
        pendingTimeouts.remove(key)?.cancel(false)
        if (state is RichMediaRequestState.Idle) requestStates.remove(key) else requestStates[key] =
            state
        invalidationListener?.invoke()
    }

    fun resolvePttPath(media: PttMediaRef): String? {
        LocalMediaResolver.resolveFile(media.filePath)?.let { return it.absolutePath }

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

        return LocalMediaResolver.resolveFile(resolved)?.absolutePath
    }

    fun resolveLocalPicturePaths(element: MsgElement): List<String> =
        PicSize.values().mapNotNull { size ->
            runCatching {
                val downloader =
                    AIOPicDownloader::class.java.getField("a").get(null) as AIOPicDownloader
                downloader.d(element, size)
            }.getOrNull()
                ?.let(LocalMediaResolver::resolveFile)
                ?.absolutePath
        }.distinct()
}
