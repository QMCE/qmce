package rj.qmce.lite.data.chat

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.api.IMsgService
import com.tencent.qqnt.kernel.api.impl.MsgService
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.FileTransNotifyInfo
import com.tencent.qqnt.kernel.nativeinterface.GetMsgsAndStatusRecord
import com.tencent.qqnt.kernel.nativeinterface.GetMsgsStatusEnum
import com.tencent.qqnt.kernel.nativeinterface.IClickInlineKeyboardButtonCallback
import com.tencent.qqnt.kernel.nativeinterface.IGetAioFirstViewLatestMsgCallback
import com.tencent.qqnt.kernel.nativeinterface.IGetMsgWithStatusCallback
import com.tencent.qqnt.kernel.nativeinterface.IGetMsgSeqCallback
import com.tencent.qqnt.kernel.nativeinterface.IGetMultiMsgCallback
import com.tencent.qqnt.kernel.nativeinterface.IGetMsgsBoxesCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgListener
import com.tencent.qqnt.kernel.nativeinterface.IMsgOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.InlineKeyboardClickInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgAttributeInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.RichMediaFilePathInfo
import mqq.app.AppRuntime
import rj.qmce.lite.kernel.KernelBridge
import java.util.concurrent.atomic.AtomicBoolean

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
    private val mainHandler = Handler(Looper.getMainLooper())

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

    fun loadMessageNavigation(
        contact: Contact,
        fallback: MessageNavigationSnapshot,
        callback: (MessageNavigationSnapshot) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        return runCatching {
            currentService.getABatchOfContactMsgBoxInfo(
                arrayListOf(contact),
                object : IGetMsgsBoxesCallback {
                    override fun onMsgBoxesInfo(
                        result: Int,
                        errorMessage: String?,
                        messageBoxes: ArrayList<com.tencent.qqnt.kernel.nativeinterface.ContactMsgBoxInfo>?,
                    ) {
                        val snapshot = if (result == 0) {
                            MessageNavigationSnapshot.fromMessageBox(messageBoxes?.firstOrNull())
                                .mergeFallback(fallback)
                        } else {
                            Log.w(
                                TAG,
                                "chatRepository: message navigation query failed " +
                                        "result=$result error=$errorMessage",
                            )
                            fallback
                        }
                        if (snapshot.unreadCount > 0 && snapshot.firstUnreadSequence == null) {
                            loadFirstUnreadSequence(contact, snapshot, callback)
                        } else {
                            callback(snapshot)
                        }
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: load message navigation failed", it)
        }.getOrDefault(false)
    }

    private fun loadFirstUnreadSequence(
        contact: Contact,
        snapshot: MessageNavigationSnapshot,
        callback: (MessageNavigationSnapshot) -> Unit,
    ) {
        val currentService = service
        if (currentService == null) {
            callback(snapshot)
            return
        }
        runCatching {
            currentService.getFirstUnreadMsgSeq(
                contact,
                object : IGetMsgSeqCallback {
                    override fun onGetMsgSeq(result: Int, errorMessage: String?, sequence: Long) {
                        if (result != 0) {
                            Log.w(
                                TAG,
                                "chatRepository: first unread sequence failed " +
                                        "result=$result error=$errorMessage",
                            )
                        }
                        callback(
                            snapshot.copy(
                                firstUnreadSequence = sequence.takeIf { result == 0 && it > 0L },
                            ),
                        )
                    }
                },
            )
        }.onFailure {
            Log.w(TAG, "chatRepository: load first unread sequence failed", it)
            callback(snapshot)
        }
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

    fun markMessagesRead(
        contact: Contact,
        runtime: AppRuntime? = null,
        callback: (errorCode: Int, errorMessage: String?) -> Unit = { _, _ -> },
    ): Boolean {
        val officialService = runCatching {
            KernelBridge.ensureOfficialMessageBridge(runtime)
        }.getOrNull()
        val readCallback = object : IOperateCallback {
            override fun onResult(errorCode: Int, errorMessage: String?) {
                callback(errorCode, errorMessage)
            }
        }
        if (officialService != null) {
            val reported = runCatching {
                officialService.setMsgRead(contact, readCallback)
                true
            }.onFailure {
                Log.w(TAG, "chatRepository: mark messages read failed via official bridge", it)
            }.getOrDefault(false)
            if (reported) return true
        }
        val currentService = service ?: return false
        return runCatching {
            currentService.setMsgRead(contact, readCallback)
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: mark messages read failed via kernel service", it)
        }.getOrDefault(false)
    }

    fun markPttPlayed(
        contact: Contact,
        messageId: Long,
        elementId: Long,
        callback: (errorCode: Int, errorMessage: String?) -> Unit = { _, _ -> },
    ): Boolean {
        if (messageId <= 0L || elementId <= 0L) return false
        val currentService = service ?: KernelBridge.getMsgService() ?: return false
        val playedCallback = object : IOperateCallback {
            override fun onResult(errorCode: Int, errorMessage: String?) {
                callback(errorCode, errorMessage)
            }
        }
        return runCatching {
            currentService.t(contact, messageId, elementId, playedCallback)
            true
        }.onFailure {
            Log.w(
                TAG,
                "chatRepository: mark ptt played failed msg=$messageId element=$elementId",
                it,
            )
        }.getOrDefault(false)
    }

    fun translatePttToText(
        contact: Contact,
        messageId: Long,
        element: MsgElement,
        callback: (errorCode: Int, errorMessage: String?) -> Unit = { _, _ -> },
    ): Boolean {
        if (messageId <= 0L || element.pttElement == null) return false
        val currentService = service ?: KernelBridge.getMsgService() ?: return false
        val translateCallback = object : IOperateCallback {
            override fun onResult(errorCode: Int, errorMessage: String?) {
                callback(errorCode, errorMessage)
            }
        }
        return runCatching {
            currentService.translatePtt2Text(
                messageId,
                contact,
                element,
                translateCallback,
            )
            true
        }.onFailure {
            Log.w(
                TAG,
                "chatRepository: translate ptt failed msg=$messageId element=${element.elementId}",
                it,
            )
        }.getOrDefault(false)
    }

    fun sendMessage(
        contact: Contact,
        elements: ArrayList<MsgElement>,
        callback: (errorCode: Int, errorMessage: String?) -> Unit,
    ): Boolean {
        val currentService = service ?: return false
        val containsMarketFace = elements.any {
            it.elementType == 11 && it.marketFaceElement != null
        }
        if (containsMarketFace) {
            val hasLegacyControlCharacter = elements.any { element ->
                element.textElement?.content?.any { character ->
                    character == '\u0001' || character == '\u0002' ||
                        character == MessageTokenCodec.BOUNDARY_START ||
                        character == MessageTokenCodec.BOUNDARY_END
                } == true
            }
            if (hasLegacyControlCharacter) {
                Log.w(TAG, "chatRepository: refuse market-face send with token control character")
                callback(-2, "消息内容仍包含旧版表情标记")
                return false
            }
            val officialService = KernelBridge.ensureOfficialMessageBridge()
            if (officialService == null) {
                Log.w(TAG, "chatRepository: official MsgService unavailable for market face")
                callback(-1, "官方消息服务不可用")
                return false
            }
            val completed = AtomicBoolean(false)
            val timeout = Runnable {
                if (completed.compareAndSet(false, true)) {
                    Log.w(TAG, "chatRepository: official market-face send callback timeout")
                    callback(-3, "官方消息服务未返回结果")
                }
            }
            return runCatching {
                Log.d(
                    TAG,
                    "chatRepository: send market face through official MsgService " +
                        "count=${elements.size} contact=${contact.chatType}/${contact.peerUid}",
                )
                elements.forEachIndexed { index, element ->
                    val face = element.marketFaceElement
                    if (face != null) {
                        Log.d(
                            TAG,
                            "chatRepository: market[$index] type=${element.elementType} " +
                                "epId=${face.emojiPackageId} eId=${face.emojiId} " +
                                "keyLength=${face.key.length} faceName=${face.faceName}",
                        )
                    }
                }
                mainHandler.postDelayed(timeout, 20_000L)
                officialService.sendMsg(contact, 0L, elements, object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        if (completed.compareAndSet(false, true)) {
                            mainHandler.removeCallbacks(timeout)
                            Log.d(
                                TAG,
                                "chatRepository: official market-face result code=$code msg=$errMsg",
                            )
                            callback(code, errMsg)
                        }
                    }
                })
                true
            }.onFailure {
                if (completed.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeout)
                    Log.w(TAG, "chatRepository: official market-face send failed", it)
                    callback(-1, it.message ?: "官方消息服务调用失败")
                }
            }.getOrDefault(false)
        }
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
        callback: (
            result: Int,
            errorMessage: String?,
            status: GetMsgsStatusEnum?,
            records: ArrayList<MsgRecord>?,
        ) -> Unit,
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
                        status: GetMsgsStatusEnum?,
                        records: ArrayList<MsgRecord>?,
                    ) {
                        callback(result, errMsg, status, records)
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

    fun loadMessageById(
        contact: Contact,
        messageId: Long,
        callback: (errorCode: Int, errorMessage: String?, records: ArrayList<MsgRecord>?) -> Unit,
    ): Boolean {
        if (messageId <= 0L) return false
        val currentService = service ?: return false
        return runCatching {
            currentService.getMsgsByMsgId(
                contact,
                arrayListOf(messageId),
                object : IMsgOperateCallback {
                    override fun onResult(
                        errorCode: Int,
                        errorMessage: String?,
                        records: ArrayList<MsgRecord>?,
                    ) {
                        callback(errorCode, errorMessage, records)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "chatRepository: load message by id failed msg=$messageId", it)
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
