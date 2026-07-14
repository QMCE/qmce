package rj.qmce.lite.data.chat

import android.util.Log
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.IMsgService
import com.tencent.qqnt.kernel.api.impl.MsgService
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.FileTransNotifyInfo
import com.tencent.qqnt.kernel.nativeinterface.GetMsgsAndStatusRecord
import com.tencent.qqnt.kernel.nativeinterface.IGetAioFirstViewLatestMsgCallback
import com.tencent.qqnt.kernel.nativeinterface.IGetMsgWithStatusCallback
import com.tencent.qqnt.kernel.nativeinterface.IGetMultiMsgCallback
import com.tencent.qqnt.kernel.nativeinterface.IClickInlineKeyboardButtonCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgListener
import com.tencent.qqnt.kernel.nativeinterface.IMsgOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.InlineKeyboardClickInfo
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.RichMediaFilePathInfo
import mqq.app.AppRuntime
import rj.qmce.lite.kernel.KernelBridge

class ChatRepository {
    data class HistoryRequest(
        val contact: Contact,
        val anchorMessageId: Long,
        val anchorMessageTime: Long,
        val count: Int,
    )

    sealed interface Connection {
        data class Ready(val service: IMsgService) : Connection
        data object KernelUnavailable : Connection
        data class MsgServiceUnavailable(val timedOut: Boolean) : Connection
    }

    interface Listener {
        fun onReceived(messages: ArrayList<MsgRecord>)
        fun onAddedSendMessage(message: MsgRecord)
        fun onRichMediaDownloadComplete(notify: FileTransNotifyInfo)
    }

    private var service: IMsgService? = null
    private var listenerService: IMsgService? = null
    private var kernelListener: IKernelMsgListener? = null

    fun connect(runtime: AppRuntime?): Connection {
        val ready = KernelBridge.awaitCoreServices()
        val kernelService = KernelBridge.getKernelService() ?: runCatching {
            runtime?.getRuntimeService(IKernelService::class.java, "")
        }.getOrNull()
        if (kernelService == null) return Connection.KernelUnavailable

        val messageService = KernelBridge.getMsgService() ?: runCatching {
            kernelService.msgService
        }.getOrNull()
        if (messageService == null) return Connection.MsgServiceUnavailable(timedOut = !ready)

        service = messageService
        RichMediaRepository.attachMessageService(messageService)
        return Connection.Ready(messageService)
    }

    fun loadLatest(
        contact: Contact,
        count: Int,
        callback: (errorCode: Int, errorMessage: String?, messages: ArrayList<MsgRecord>?, needContinue: Boolean) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        return runCatching {
            currentService.getAioFirstViewLatestMsgs(
                contact,
                count,
                object : IGetAioFirstViewLatestMsgCallback {
                    override fun onResult(
                        errorCode: Int,
                        errMsg: String?,
                        list: ArrayList<MsgRecord>?,
                        needContinue: Boolean,
                    ) {
                        callback(errorCode, errMsg, list, needContinue)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: load latest failed", it)
        }.getOrDefault(false)
    }

    fun startListening(listener: Listener): Boolean {
        stopListening()
        val currentService = service ?: return false
        val proxy = java.lang.reflect.Proxy.newProxyInstance(
            IKernelMsgListener::class.java.classLoader,
            arrayOf(IKernelMsgListener::class.java),
        ) { proxyInstance, method, args ->
            when (method.name) {
                "onRecvMsg" -> {
                    @Suppress("UNCHECKED_CAST")
                    (args?.getOrNull(0) as? ArrayList<MsgRecord>)?.let(listener::onReceived)
                    null
                }
                "onAddSendMsg" -> {
                    (args?.getOrNull(0) as? MsgRecord)?.let(listener::onAddedSendMessage)
                    null
                }
                "onRichMediaDownloadComplete" -> {
                    (args?.getOrNull(0) as? FileTransNotifyInfo)?.let(listener::onRichMediaDownloadComplete)
                    null
                }
                "hashCode" -> System.identityHashCode(proxyInstance)
                "equals" -> proxyInstance === args?.getOrNull(0)
                "toString" -> "QMCE-ChatRepositoryListener"
                else -> null
            }
        } as IKernelMsgListener

        return runCatching {
            currentService.s(proxy)
            kernelListener = proxy
            listenerService = currentService
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: register listener failed", it)
        }.getOrDefault(false)
    }

    fun isConnected(): Boolean = service != null

    fun sendMessage(
        contact: Contact,
        elements: ArrayList<MsgElement>,
        callback: (errorCode: Int, errorMessage: String?) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        return runCatching {
            currentService.sendMsg(
                0L,
                contact,
                elements,
                HashMap<Int, MsgAttributeInfo>(),
                object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        callback(code, errMsg)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: send message failed", it)
        }.getOrDefault(false)
    }

    fun clickInlineKeyboardButton(
        info: InlineKeyboardClickInfo,
        callback: (errorCode: Int, errorMessage: String?, resultCode: Int, resultMessage: String?) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        return runCatching {
            currentService.clickInlineKeyboardButton(
                info,
                object : IClickInlineKeyboardButtonCallback {
                    override fun onResult(
                        errorCode: Int,
                        errorMessage: String?,
                        resultCode: Int,
                        resultMessage: String?,
                        retryCount: Int,
                        cooldownSeconds: Int,
                    ) {
                        callback(errorCode, errorMessage, resultCode, resultMessage)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: click inline keyboard failed", it)
        }.getOrDefault(false)
    }

    fun recallMessage(
        contact: Contact,
        msgId: Long,
        callback: (errorCode: Int, errorMessage: String?) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        return runCatching {
            currentService.recallMsg(
                contact,
                msgId,
                object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        callback(code, errMsg)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: recall message failed", it)
        }.getOrDefault(false)
    }

    fun deleteMessages(
        contact: Contact,
        msgIds: ArrayList<Long>,
        callback: (errorCode: Int, errorMessage: String?) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        return runCatching {
            currentService.deleteMsg(
                contact,
                msgIds,
                object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        callback(code, errMsg)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: delete messages failed", it)
        }.getOrDefault(false)
    }

    fun resendMessage(
        contact: Contact,
        msgId: Long,
        callback: (errorCode: Int, errorMessage: String?) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        return runCatching {
            currentService.resendMsg(
                contact,
                msgId,
                object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        callback(code, errMsg)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: resend message failed", it)
        }.getOrDefault(false)
    }

    fun getMobileQQSendPath(info: RichMediaFilePathInfo): String? {
        val currentService = service ?: return null
        return runCatching {
            currentService.getRichMediaFilePathForMobileQQSend(info)
        }.onFailure {
            Log.w(TAG, "chatRepository: resolve send media path failed", it)
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    fun loadOlder(
        request: HistoryRequest,
        callback: (result: Int, errorMessage: String?, records: ArrayList<MsgRecord>?) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        return runCatching {
            currentService.getMsgsWithStatus(
                GetMsgsAndStatusRecord().apply {
                    peer = request.contact
                    msgId = request.anchorMessageId
                    msgTime = request.anchorMessageTime
                    cnt = request.count
                    queryOrder = true
                    isIncludeSelf = false
                    appid = 0L
                },
                object : IGetMsgWithStatusCallback {
                    override fun onResult(
                        result: Int,
                        errMsg: String?,
                        status: com.tencent.qqnt.kernel.nativeinterface.GetMsgsStatusEnum?,
                        records: ArrayList<MsgRecord>?,
                    ) {
                        callback(result, errMsg, records)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: load older failed", it)
        }.getOrDefault(false)
    }

    fun loadForwardDetail(
        contact: Contact,
        rootMessageId: Long,
        rawMessageId: Long,
        callback: (errorCode: Int, errorMessage: String?, records: ArrayList<MsgRecord>?) -> Unit,
    ): Boolean {
        val nativeService = (service as? MsgService)?.service ?: return false
        return runCatching {
            nativeService.getMultiMsg(
                contact,
                rootMessageId,
                rawMessageId,
                object : IGetMultiMsgCallback {
                    override fun onResult(
                        errorCode: Int,
                        errMsg: String?,
                        records: ArrayList<MsgRecord>?,
                    ) {
                        callback(errorCode, errMsg, records)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: load forward detail failed", it)
        }.getOrDefault(false)
    }

    fun loadReplySource(
        contact: Contact,
        messageId: Long,
        messageSequence: Long,
        callback: (errorCode: Int, errorMessage: String?, records: ArrayList<MsgRecord>?) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        return runCatching {
            currentService.getSourceOfReplyMsg(
                contact,
                messageId,
                messageSequence,
                object : IMsgOperateCallback {
                    override fun onResult(
                        errorCode: Int,
                        errMsg: String?,
                        records: ArrayList<MsgRecord>?,
                    ) {
                        callback(errorCode, errMsg, records)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: load reply source failed", it)
        }.getOrDefault(false)
    }

    fun stopListening() {
        val listener = kernelListener ?: return
        runCatching { listenerService?.g(listener) }
            .onFailure { Log.w(TAG, "chatRepository: remove listener failed", it) }
        kernelListener = null
        listenerService = null
    }

    fun close() {
        stopListening()
        RichMediaRepository.detachMessageService(service)
        service = null
    }

    private companion object {
        const val TAG = "QMCE"
    }
}
