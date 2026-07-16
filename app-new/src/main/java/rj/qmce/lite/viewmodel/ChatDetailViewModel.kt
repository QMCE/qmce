package rj.qmce.lite.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.aio.api.IAIOFileTransfer
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.InlineKeyboardClickInfo
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.PicElement
import com.tencent.qqnt.kernel.nativeinterface.PttElement
import com.tencent.qqnt.kernel.nativeinterface.QQNTWrapperUtil
import com.tencent.qqnt.kernel.nativeinterface.RichMediaFilePathInfo
import com.tencent.qqnt.kernel.nativeinterface.TextElement
import com.tencent.qqnt.kernel.nativeinterface.VideoElement
import com.tencent.qqnt.msg.api.IMsgUtilApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mqq.app.AppRuntime
import rj.qmce.lite.data.chat.AtMention
import rj.qmce.lite.data.chat.ChatRepository
import rj.qmce.lite.data.chat.GroupMemberRepository
import rj.qmce.lite.data.chat.LinkPreviewRepository
import rj.qmce.lite.data.chat.LinkPreviewState
import rj.qmce.lite.data.chat.LocalMediaResolver
import rj.qmce.lite.data.chat.OfficialPttPlayer
import rj.qmce.lite.data.chat.PttMediaRef
import rj.qmce.lite.data.chat.RichMediaKey
import rj.qmce.lite.data.chat.RichMediaRepository
import rj.qmce.lite.data.chat.RichMediaRequestState
import rj.qmce.lite.data.chat.RichMessageMetadataParser
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class ChatDetailViewModel : ViewModel() {

    companion object {
        private const val TAG = "QMCE"
        private const val LOAD_COUNT = 30
        private const val HISTORY_PAGE_COUNT = 10
        private const val REPLY_SOURCE_TIMEOUT_MS = 15_000L
    }

    sealed interface MessageContent {
        data class Text(val value: String) : MessageContent
        data class Image(
            val elementId: Long,
            val localPaths: List<String>,
            val sourcePath: String?,
            val thumbnailPaths: List<String>,
            val width: Int,
            val height: Int,
            val transferStatus: Int?,
            val isLoading: Boolean,
            val loadError: String?,
        ) : MessageContent

        data class Face(
            val text: String,
            val packId: String?,
            val stickerId: String?,
        ) : MessageContent

        data class MarketFace(
            val name: String,
            val staticPath: String?,
            val dynamicPath: String?,
            val width: Int,
            val height: Int,
        ) : MessageContent

        data class Giphy(
            val id: String,
            val mediaUrl: String?,
            val width: Int,
            val height: Int,
        ) : MessageContent

        data class Voice(
            val media: PttMediaRef,
            val progress: Int?,
            val transferStatus: Int?,
            val transcript: String?,
        ) : MessageContent

        data class Video(
            val filePath: String?,
            val thumbnailPaths: List<String>,
            val width: Int,
            val height: Int,
            val durationSeconds: Int,
            val progress: Int?,
            val transferStatus: Int?,
        ) : MessageContent

        data class File(
            val elementId: Long,
            val name: String,
            val path: String?,
            val sizeBytes: Long,
            val progress: Int?,
            val transferStatus: Int?,
            val fileUuid: String?,
            val fileMd5: String?,
            val fileSha: String?,
            val fileBizId: Int?,
            val fileSubId: String?,
            val fileTransType: Int?,
            val folderId: String?,
            val expireTime: Long?,
            val invalidState: Int?,
            val pictureWidth: Int?,
            val pictureHeight: Int?,
            val thumbnailPaths: List<String>,
            val videoDurationSeconds: Int?,
            val isDownloading: Boolean,
            val downloadError: String?,
        ) : MessageContent

        data class Reply(
            val senderName: String,
            val summary: String,
            val targetMessageId: Long,
            val targetSequence: Long?,
            val expired: Boolean,
        ) : MessageContent

        data class Card(
            val title: String,
            val description: String,
            val tag: String?,
            val previewUrl: String?,
            val actionUrl: String? = null,
        ) : MessageContent

        data class StructCard(
            val title: String,
            val description: String,
            val groupCode: String?,
        ) : MessageContent

        data class Forward(
            val title: String,
            val preview: List<String>,
            val resourceId: String?,
            val rootMessageId: Long,
            val rawMessageId: Long,
        ) : MessageContent

        data class SystemTip(val text: String) : MessageContent
        data class Location(
            val title: String,
            val detail: String,
        ) : MessageContent

        data class Wallet(
            val title: String,
            val description: String,
            val notice: String?,
            val iconUrl: String?,
        ) : MessageContent

        data class Calendar(
            val title: String,
            val description: String,
            val expired: Boolean,
        ) : MessageContent

        data class InlineKeyboard(
            val botAppid: Long,
            val rows: List<List<InlineKeyboardButton>>,
        ) : MessageContent

        data class InlineKeyboardButton(
            val stableKey: String,
            val id: String,
            val data: String,
            val label: String,
            val visitedLabel: String?,
            val type: Int,
            val unsupportTips: String?,
        )

        data class Markdown(val value: String) : MessageContent
        data class LinkPreview(
            val url: String,
            val state: LinkPreviewState,
        ) : MessageContent

        data class CallRecord(
            val text: String,
            val type: Int,
            val time: Long,
            val hasRead: Boolean,
        ) : MessageContent

        data class Unsupported(
            val elementType: Int,
            val detail: String? = null,
        ) : MessageContent
    }

    data class UiMsg(
        val stableKey: String,
        val msgId: Long,
        val msgSeq: Long,
        val senderUid: String,
        val senderNick: String,
        val time: Long,
        val peerUid: String,
        val chatType: Int,
        val guildId: String,
        val dmFlag: Int,
        val contents: List<MessageContent>,
        val text: String,
        val isSelf: Boolean,
        val status: Int, // sendStatus
    )

    data class ReplyTarget(
        val messageId: Long,
        val senderUid: String,
        val senderName: String,
        val summary: String,
        val isGroupChat: Boolean,
    )

    sealed interface ForwardDetailState {
        data object Idle : ForwardDetailState
        data class Loading(val title: String) : ForwardDetailState
        data class Ready(
            val title: String,
            val messages: List<UiMsg>,
        ) : ForwardDetailState

        data class Error(
            val title: String,
            val message: String,
        ) : ForwardDetailState
    }

    sealed interface ReplySourceState {
        data object Idle : ReplySourceState
        data class Loading(
            val messageId: Long,
            val sequence: Long?,
        ) : ReplySourceState

        data class Loaded(
            val messageId: Long,
            val sequence: Long?,
        ) : ReplySourceState

        data class Error(
            val messageId: Long,
            val sequence: Long?,
            val message: String,
        ) : ReplySourceState
    }

    private val _messages = MutableStateFlow<List<UiMsg>>(emptyList())
    val messages: StateFlow<List<UiMsg>> = _messages

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private val _peerName = MutableStateFlow("")
    val peerName: StateFlow<String> = _peerName
    private val _forwardDetailState = MutableStateFlow<ForwardDetailState>(ForwardDetailState.Idle)
    val forwardDetailState: StateFlow<ForwardDetailState> = _forwardDetailState
    private val _replySourceState = MutableStateFlow<ReplySourceState>(ReplySourceState.Idle)
    val replySourceState: StateFlow<ReplySourceState> = _replySourceState

    private val _pendingForwardMessage = MutableStateFlow<UiMsg?>(null)
    val pendingForwardMessage: StateFlow<UiMsg?> = _pendingForwardMessage
    private val _pendingReplyTarget = MutableStateFlow<ReplyTarget?>(null)
    val pendingReplyTarget: StateFlow<ReplyTarget?> = _pendingReplyTarget

    private val _multiSelectMode = MutableStateFlow(false)
    val multiSelectMode: StateFlow<Boolean> = _multiSelectMode
    private val _selectedMsgIds = MutableStateFlow<LinkedHashSet<Long>>(linkedSetOf())
    val selectedMsgIds: StateFlow<LinkedHashSet<Long>> = _selectedMsgIds

    private val _editingMsgId = MutableStateFlow(0L)
    val editingMsgId: StateFlow<Long> = _editingMsgId
    private val _editingText = MutableStateFlow("")
    val editingText: StateFlow<String> = _editingText

    private val _groupMembers = MutableStateFlow<List<GroupMemberRepository.Member>>(emptyList())
    val groupMembers: StateFlow<List<GroupMemberRepository.Member>> = _groupMembers
    private val _groupMembersLoading = MutableStateFlow(false)
    val groupMembersLoading: StateFlow<Boolean> = _groupMembersLoading
    private val _groupMembersError = MutableStateFlow<String?>(null)
    val groupMembersError: StateFlow<String?> = _groupMembersError

    enum class InlineKeyboardActionPhase { Pending, Succeeded }

    data class InlineKeyboardActionState(
        val phase: InlineKeyboardActionPhase,
        val label: String,
    )

    private val _inlineKeyboardActions =
        MutableStateFlow<Map<String, InlineKeyboardActionState>>(emptyMap())
    val inlineKeyboardActions: StateFlow<Map<String, InlineKeyboardActionState>> =
        _inlineKeyboardActions

    private val msgList = CopyOnWriteArrayList<MsgRecord>()
    private val messageLock = Any()
    private val _isLoadingOlder = MutableStateFlow(false)
    val isLoadingOlder: StateFlow<Boolean> = _isLoadingOlder
    private val _hasOlderMessages = MutableStateFlow(true)
    val hasOlderMessages: StateFlow<Boolean> = _hasOlderMessages
    private val _olderPageVersion = MutableStateFlow(0L)
    val olderPageVersion: StateFlow<Long> = _olderPageVersion
    private val chatSession = java.util.concurrent.atomic.AtomicLong(0)
    private val forwardDetailRequest = java.util.concurrent.atomic.AtomicLong(0)
    private val replySourceRequest = java.util.concurrent.atomic.AtomicLong(0)
    private val replyTimeoutHandler = Handler(Looper.getMainLooper())
    private val forwardDetailBackStack = ArrayDeque<ForwardDetailState.Ready>()
    private val chatRepository = ChatRepository()
    private var contact: Contact? = null
    @Volatile
    private var chatRuntime: AppRuntime? = null
    private val pendingMessageServiceActions = mutableListOf<Pair<Long, () -> Unit>>()
    private val messageServiceConnectionInFlight = AtomicBoolean(false)
    private var selfUin: Long = 0L
    private var peerId: String = "" // QQ号，用于 RecentMessageStore key

    val currentPeerUid: String get() = contact?.peerUid ?: ""
    val currentPeerUin: String get() = peerId
    val currentChatType: Int get() = contact?.chatType ?: 1

    val pttPlaybackStates = OfficialPttPlayer.states

    init {
        RichMediaRepository.setInvalidationListener { emitMessages() }
        LinkPreviewRepository.setInvalidationListener { emitMessages() }
    }

    fun openChat(
        runtime: AppRuntime?,
        peerUid: String,
        peerUin: String,
        chatType: Int,
        name: String,
        myUin: String = ""
    ) {
        val session = chatSession.incrementAndGet()
        forwardDetailRequest.incrementAndGet()
        replySourceRequest.incrementAndGet()
        replyTimeoutHandler.removeCallbacksAndMessages(null)
        forwardDetailBackStack.clear()
        OfficialPttPlayer.stopAndRelease()
        chatRepository.close()
        synchronized(pendingMessageServiceActions) {
            pendingMessageServiceActions.clear()
        }
        msgList.clear()
        _messages.value = emptyList()
        _groupMembers.value = emptyList()
        _groupMembersLoading.value = false
        _groupMembersError.value = null
        _isLoadingOlder.value = false
        _hasOlderMessages.value = true
        _peerName.value = name
        _forwardDetailState.value = ForwardDetailState.Idle
        _replySourceState.value = ReplySourceState.Idle
        _pendingReplyTarget.value = null
        selfUin = myUin.toLongOrNull() ?: 0L
        peerId = peerUin  // 保存 QQ 号
        contact = Contact(chatType, peerUid, "")
        chatRuntime = runtime
        loadMessages(runtime, session)
    }

    private fun loadMessages(runtime: AppRuntime?, session: Long) {
        _statusText.value = "正在等待消息服务..."
        Thread {
            if (!isCurrentSession(session)) return@Thread
            when (val connection = chatRepository.connect(runtime)) {
                is ChatRepository.Connection.Ready -> {
                    loadMessagesFromService(session)
                }

                ChatRepository.Connection.KernelUnavailable -> {
                    _statusText.value = "KernelService 不可用"
                }

                is ChatRepository.Connection.MsgServiceUnavailable -> {
                    _statusText.value =
                        if (connection.timedOut) "消息服务初始化超时" else "MsgService 不可用"
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun loadMessagesFromService(session: Long) {
        if (!isCurrentSession(session)) return
        if (!chatRepository.isConnected()) {
            _statusText.value = "MsgService 不可用"
            return
        }
        val c = contact ?: return
        _statusText.value = "加载中..."
        if (!startMessageListener(session, c)) {
            Log.w(TAG, "chatDetail: message listener registration failed")
        }
        val requested =
            chatRepository.loadLatest(c, LOAD_COUNT) { errorCode, errMsg, list, needContinue ->
                if (!isCurrentSession(session)) return@loadLatest
                Log.d(
                    TAG,
                    "getAioFirstViewLatestMsgs: code=$errorCode, count=${list?.size}, needContinue=$needContinue"
                )
                if (errorCode == 0) {
                    msgList.clear()
                    mergeMessages(list.orEmpty(), c)
                    emitMessages()
                    _hasOlderMessages.value = needContinue
                    _statusText.value = ""
                } else {
                    _statusText.value = "消息记录加载失败: ${errMsg ?: errorCode}"
                }
            }
        if (!requested) {
            _statusText.value = "消息记录加载请求失败"
        }
    }

    private fun startMessageListener(session: Long, chatContact: Contact): Boolean =
        chatRepository.startListening(object : ChatRepository.Listener {
            override fun onReceived(messages: ArrayList<MsgRecord>) {
                if (!isCurrentSession(session)) return
                val previousSize = msgList.size
                mergeMessages(messages, chatContact)
                if (msgList.size != previousSize) {
                    emitMessages()
                    Log.d(TAG, "chatDetail: onRecvMsg +${msgList.size - previousSize}")
                }
            }

            override fun onAddedSendMessage(message: MsgRecord) {
                if (!isCurrentSession(session)) return
                val previousSize = msgList.size
                mergeMessages(listOf(message), chatContact)
                if (msgList.size != previousSize) {
                    emitMessages()
                    Log.d(
                        TAG,
                        "chatDetail: onAddSendMsg msgId=${message.msgId}, time=${message.msgTime}"
                    )
                }
            }

            override fun onRichMediaDownloadComplete(
                notify: com.tencent.qqnt.kernel.nativeinterface.FileTransNotifyInfo,
            ) {
                if (!isCurrentSession(session)) return
                if (RichMediaRepository.consumeDownloadCompletion(notify)) emitMessages()
            }
        })

    private fun runWhenMessageServiceReady(action: () -> Unit) {
        if (chatRepository.isConnected()) {
            action()
            return
        }
        val session = chatSession.get()
        synchronized(pendingMessageServiceActions) {
            pendingMessageServiceActions += session to action
        }
        _statusText.value = "正在等待消息服务..."
        if (!messageServiceConnectionInFlight.compareAndSet(false, true)) return

        val runtime = chatRuntime
        Thread {
            val connection = chatRepository.connect(runtime)
            val pendingActions = synchronized(pendingMessageServiceActions) {
                pendingMessageServiceActions.toList().also { pendingMessageServiceActions.clear() }
                    .also {
                        messageServiceConnectionInFlight.set(false)
                    }
            }
            when (connection) {
                is ChatRepository.Connection.Ready -> {
                    pendingActions.forEach { (actionSession, pendingAction) ->
                        if (isCurrentSession(actionSession)) {
                            _statusText.value = ""
                            pendingAction()
                        }
                    }
                }

                ChatRepository.Connection.KernelUnavailable -> {
                    if (isCurrentSession(session)) _statusText.value = "KernelService 不可用"
                }

                is ChatRepository.Connection.MsgServiceUnavailable -> {
                    if (isCurrentSession(session)) {
                        _statusText.value = if (connection.timedOut) {
                            "消息服务初始化超时"
                        } else {
                            "MsgService 不可用"
                        }
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun sendImage(context: Context, uri: Uri) {
        val c = contact ?: return
        if (!chatRepository.isConnected()) {
            runWhenMessageServiceReady { sendImage(context, uri) }
            return
        }

        // Copy image to a temp file so we can compute md5
        val tmpFile = File(context.cacheDir, "send_img_${System.currentTimeMillis()}.jpg")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmpFile.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                _statusText.value = "读取图片失败"
                return
            }
        } catch (e: Exception) {
            _statusText.value = "读取图片失败: ${e.message}"
            return
        }

        val md5 = md5File(tmpFile)
        val fileName = tmpFile.name
        val fileSize = tmpFile.length()

        // Resolve kernel-managed file paths (original + thumbnail)
        val origPath = chatRepository.getMobileQQSendPath(
            RichMediaFilePathInfo(2, 0, md5, fileName, 1, 0, null, "", true),
        ) ?: tmpFile.absolutePath

        val thumbPath = chatRepository.getMobileQQSendPath(
            RichMediaFilePathInfo(2, 0, md5, fileName, 2, 720, null, "", true),
        )

        // Copy to kernel paths if different from tmp
        if (origPath != tmpFile.absolutePath) {
            runCatching { tmpFile.copyTo(File(origPath), overwrite = true) }
        }
        if (thumbPath != null && thumbPath != tmpFile.absolutePath) {
            runCatching { tmpFile.copyTo(File(thumbPath), overwrite = true) }
        }

        // Decode dimensions
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(origPath, opts)
        val width = opts.outWidth.takeIf { it > 0 } ?: 800
        val height = opts.outHeight.takeIf { it > 0 } ?: 600

        // picType: 0x3E8=jpg, 0x3E9=png, 0x3EA=webp, 0x7D0=gif
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val picType = when (ext) {
            "jpg", "jpeg" -> 1000
            "png" -> 1001
            "webp" -> 1002
            "gif" -> 2000
            "bmp" -> 1005
            else -> 1001 // default png
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

        val element = MsgElement().apply {
            elementType = 2 // PIC
            elementId = 0
            this.picElement = picElement
        }
        val elements = arrayListOf(element)
        val sent = chatRepository.sendMessage(c, elements) { code, errMsg ->
            Log.d(TAG, "sendImage: code=$code, errMsg=$errMsg")
            if (code == 0) {
                val now = System.currentTimeMillis() / 1000
                val rec = MsgRecord().apply {
                    peerUid = c.peerUid
                    chatType = c.chatType
                    msgTime = now
                    senderUin = selfUin
                    sendNickName = ""
                    sendStatus = 2
                }
                runCatching {
                    val f = MsgRecord::class.java.getDeclaredField("elements")
                    f.isAccessible = true
                    f.set(rec, arrayListOf(element))
                }
                RecentMessageStore.put(peerId, rec)
                Log.d(TAG, "sendImage: put to RecentMessageStore id=$peerId")
            } else {
                _statusText.value = "发送图片失败: $errMsg"
            }
        }
        if (!sent) _statusText.value = "消息服务不可用"
    }

    fun sendVideo(context: Context, uri: Uri) {
        val c = contact ?: run {
            _statusText.value = "聊天不可用，无法发送视频"
            return
        }
        if (!chatRepository.isConnected()) {
            runWhenMessageServiceReady { sendVideo(context, uri) }
            return
        }
        _statusText.value = "正在准备视频…"
        Thread {
            runCatching {
                val videoFile =
                    File(context.cacheDir, "send_video_${System.currentTimeMillis()}.mp4")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    videoFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("读取视频失败")
                if (!videoFile.isFile || videoFile.length() == 0L) error("视频为空")

                val fileName = videoFile.name
                val videoMd5 = runCatching {
                    QQNTWrapperUtil.CppProxy.genFileMd5Hex(videoFile.absolutePath)
                }.getOrElse { md5File(videoFile) }
                val originalPath = chatRepository.getMobileQQSendPath(
                    RichMediaFilePathInfo(5, 1, videoMd5, fileName, 1, 0, null, "", true),
                ) ?: videoFile.absolutePath
                if (originalPath != videoFile.absolutePath) {
                    File(originalPath).parentFile?.mkdirs()
                    videoFile.copyTo(File(originalPath), overwrite = true)
                }

                val thumbPath = chatRepository.getMobileQQSendPath(
                    RichMediaFilePathInfo(5, 1, videoMd5, fileName, 2, 720, null, "", true),
                ) ?: File(
                    context.cacheDir,
                    "send_video_thumb_${System.currentTimeMillis()}.jpg"
                ).absolutePath
                val thumbFile = File(thumbPath)
                thumbFile.parentFile?.mkdirs()
                if (!thumbFile.isFile || thumbFile.length() == 0L) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(videoFile.absolutePath)
                        val frame =
                            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                ?: error("无法生成视频缩略图")
                        thumbFile.outputStream().use { output ->
                            frame.compress(
                                android.graphics.Bitmap.CompressFormat.JPEG,
                                seventyPercent(),
                                output
                            )
                        }
                        frame.recycle()
                    } finally {
                        retriever.release()
                    }
                }
                val thumbOptions = BitmapFactory.Options()
                BitmapFactory.decodeFile(thumbFile.absolutePath, thumbOptions)
                val durationSeconds = MediaMetadataRetriever().run {
                    try {
                        setDataSource(videoFile.absolutePath)
                        (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                            ?: 0L)
                            .div(1000L).toInt()
                    } finally {
                        release()
                    }
                }
                val video = VideoElement().apply {
                    filePath = originalPath
                    this.videoMd5 = videoMd5
                    fileTime = durationSeconds
                    fileSize = videoFile.length()
                    this.fileName = fileName
                    fileFormat = 2
                    thumbSize = thumbFile.length().toInt()
                    thumbWidth = thumbOptions.outWidth
                    thumbHeight = thumbOptions.outHeight
                    thumbMd5 = runCatching {
                        QQNTWrapperUtil.CppProxy.genFileMd5Hex(thumbFile.absolutePath)
                    }.getOrElse { md5File(thumbFile) }
                    this.thumbPath = hashMapOf(0 to thumbFile.absolutePath)
                }
                val element = MsgElement().apply {
                    elementType = 5
                    elementId = 0L
                    videoElement = video
                }
                val sent = chatRepository.sendMessage(c, arrayListOf(element)) { code, errMsg ->
                    if (code == 0) {
                        val now = System.currentTimeMillis() / 1000
                        val record = MsgRecord().apply {
                            peerUid = c.peerUid
                            chatType = c.chatType
                            msgTime = now
                            senderUin = selfUin
                            sendNickName = ""
                            sendStatus = 2
                        }
                        runCatching {
                            MsgRecord::class.java.getDeclaredField("elements")
                                .apply { isAccessible = true }
                                .set(record, arrayListOf(element))
                        }
                        RecentMessageStore.put(peerId, record)
                        _statusText.value = "视频已发送"
                    } else {
                        _statusText.value = "发送视频失败：${errMsg ?: code}"
                    }
                }
                if (!sent) _statusText.value = "消息服务不可用"
            }.onFailure {
                Log.w(TAG, "sendVideo failed", it)
                _statusText.value = "发送视频失败：${it.message ?: "未知错误"}"
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun seventyPercent(): Int = 70

    fun requestFileDownload(message: UiMsg, content: MessageContent.File): Boolean {
        val unavailableReason = fileDownloadUnavailableReason(content) ?: return false
        _statusText.value = unavailableReason
        return false
    }

    fun fileDownloadUnavailableReason(content: MessageContent.File): String? = when {
        content.invalidState != null && content.invalidState != 0 -> "文件不可用"
        LocalMediaResolver.resolveFile(content.path) != null -> null
        else -> RichMediaRepository.fileDownloadUnavailableReason()
    }

    private fun md5File(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { fis ->
            val buf = ByteArray(8192)
            var read: Int
            while (fis.read(buf).also { read = it } > 0) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Sends a voice file produced by QQ's IQQRecorder. The element construction follows the
     * official Watch AudioTouchViewNTProcessor path: PTT rich-media type 4/subtype 3, then a
     * PttElement with the recorder's format type.
     */
    fun sendVoice(recordedFile: File, durationMillis: Long, formatType: Int) {
        val c = contact ?: run {
            _statusText.value = "聊天不可用，无法发送语音"
            return
        }
        if (!chatRepository.isConnected()) {
            runWhenMessageServiceReady { sendVoice(recordedFile, durationMillis, formatType) }
            return
        }
        if (!recordedFile.isFile || recordedFile.length() <= 0L) {
            _statusText.value = "录音文件无效"
            return
        }

        _statusText.value = "正在发送语音..."
        Thread {
            runCatching {
                val sourceMd5 = md5File(recordedFile)
                val sendPath = chatRepository.getMobileQQSendPath(
                    RichMediaFilePathInfo(
                        4,
                        3,
                        sourceMd5,
                        recordedFile.name,
                        1,
                        0,
                        null,
                        "",
                        true,
                    ),
                ).orEmpty()
                val kernelFile = sendPath.takeIf(String::isNotBlank)?.let(::File)
                val sendFile = if (kernelFile != null) {
                    if (!kernelFile.isFile || kernelFile.length() <= 0L) {
                        kernelFile.parentFile?.mkdirs()
                        recordedFile.copyTo(kernelFile, overwrite = true)
                    }
                    kernelFile
                } else {
                    recordedFile
                }
                if (!sendFile.isFile || sendFile.length() <= 0L) {
                    throw IllegalStateException("内核语音文件不可用")
                }

                val ptt = PttElement().apply {
                    fileName = sendFile.name
                    filePath = sendFile.absolutePath
                    md5HexStr = md5File(sendFile)
                    fileSize = sendFile.length()
                    duration =
                        kotlin.math.max(1, kotlin.math.round(durationMillis / 1000.0).toInt())
                    this.formatType = formatType
                    voiceType = 2
                    voiceChangeType = 0
                    canConvert2Text = false
                    fileId = 0
                    fileUuid = ""
                    text = ""
                    waveAmplitudes = arrayListOf()
                }
                val element = MsgElement().apply {
                    elementType = 4
                    elementId = 0L
                    pttElement = ptt
                }
                val elements = arrayListOf(element)
                val sent = chatRepository.sendMessage(c, elements) { code, errMsg ->
                    Log.d(TAG, "sendVoice: code=$code, errMsg=$errMsg")
                    if (code == 0) {
                        val rec = MsgRecord().apply {
                            peerUid = c.peerUid
                            chatType = c.chatType
                            msgTime = System.currentTimeMillis() / 1000
                            senderUin = selfUin
                            sendNickName = ""
                            sendStatus = 2
                        }
                        runCatching {
                            val field = MsgRecord::class.java.getDeclaredField("elements")
                            field.isAccessible = true
                            field.set(rec, arrayListOf(element))
                        }
                        RecentMessageStore.put(peerId, rec)
                        _statusText.value = ""
                    } else {
                        _statusText.value = "发送语音失败: ${errMsg.orEmpty()}"
                    }
                }
                if (!sent) _statusText.value = "消息服务不可用，无法发送语音"
            }.onFailure {
                Log.w(TAG, "sendVoice failed", it)
                _statusText.value = "发送语音失败: ${it.message ?: "未知错误"}"
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun sendFile(context: Context, uri: Uri) {
        val target = contact ?: run {
            _statusText.value = "聊天不可用，无法发送文件"
            return
        }
        val appContext = context.applicationContext
        _statusText.value = "正在准备文件..."
        Thread {
            runCatching {
                val displayName = appContext.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }?.takeIf(String::isNotBlank) ?: "发送文件"
                val safeName = displayName.replace(Regex("[/\\\\]"), "_")
                val sendDir = File(appContext.cacheDir, "qmce-send-files").apply { mkdirs() }
                val localFile = File(sendDir, "${System.currentTimeMillis()}-$safeName")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    localFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("无法读取所选文件")
                if (!localFile.isFile || localFile.length() <= 0L) error("所选文件为空")

                val transfer = QRoute.api(IAIOFileTransfer::class.java)
                transfer.sendLocalFile(target, localFile.absolutePath) { code, errMsg ->
                    _statusText.value = if (code == 0) {
                        "文件已提交：$displayName"
                    } else {
                        "文件发送失败：${errMsg ?: code}"
                    }
                    if (code == 0) localFile.delete()
                }
            }.onFailure {
                Log.w(TAG, "sendFile failed", it)
                _statusText.value = "文件发送失败：${it.message ?: "未知错误"}"
            }
        }.apply { isDaemon = true; start() }
    }

    fun sendTextWithImage(context: Context, uri: Uri, text: String) {
        val c = contact ?: return
        if (!chatRepository.isConnected()) {
            runWhenMessageServiceReady { sendTextWithImage(context, uri, text) }
            return
        }

        val picEl = buildPicElement(context, uri)
        val elements = arrayListOf(picEl)

        if (text.isNotBlank()) {
            elements.add(MsgElement().apply {
                elementType = 1; elementId = 0
                textElement =
                    TextElement().apply { content = text; atType = 0; atUid = 0L; atNtUid = "" }
            })
        }

        val sent = chatRepository.sendMessage(c, elements) { code, errMsg ->
            Log.d(TAG, "sendTextWithImage: code=$code, errMsg=$errMsg")
            if (code == 0) {
                val now = System.currentTimeMillis() / 1000
                val rec = MsgRecord().apply {
                    peerUid = c.peerUid; chatType = c.chatType; msgTime = now
                    senderUin = selfUin; sendNickName = ""; sendStatus = 2
                }
                runCatching {
                    val f = MsgRecord::class.java.getDeclaredField("elements")
                    f.isAccessible = true; f.set(rec, elements)
                }
                RecentMessageStore.put(peerId, rec)
            } else {
                _statusText.value = "发送失败: $errMsg"
            }
        }
        if (!sent) _statusText.value = "消息服务不可用"
    }

    fun loadGroupMembers(
        groupCode: Long,
        forceRefresh: Boolean = false,
        updateStatus: Boolean = true,
    ) {
        if (groupCode <= 0L) {
            _groupMembersError.value = "群号无效"
            if (updateStatus) _statusText.value = "群号无效"
            return
        }
        val cached = GroupMemberRepository.cached(groupCode)
        if (cached.isNotEmpty() && !forceRefresh) {
            _groupMembers.value = cached
            _groupMembersLoading.value = false
            _groupMembersError.value = null
            return
        }
        _groupMembersLoading.value = true
        _groupMembersError.value = null
        if (updateStatus) _statusText.value = "正在加载群成员..."
        val requested = GroupMemberRepository.load(groupCode, forceRefresh) { members, error ->
            _groupMembersLoading.value = false
            if (members != null) {
                _groupMembers.value = members
                _groupMembersError.value = null
                if (updateStatus) _statusText.value = ""
            } else {
                _groupMembersError.value = error ?: "获取群成员失败"
                if (updateStatus) _statusText.value = error ?: "获取群成员失败"
            }
        }
        if (!requested) {
            _groupMembersLoading.value = false
            _groupMembersError.value = "群服务不可用"
            if (updateStatus) _statusText.value = "群服务不可用"
        }
    }

    /**
     * 混合发送：文本、图片和 @ 标记按顺序转换为 NT MsgElement。
     * 图片 token 为 img:id，@ token 为 at:id。
     */
    fun sendMixed(
        context: Context,
        mixedText: String,
        uriMap: Map<String, Uri>,
        atMap: Map<String, AtMention> = emptyMap(),
        replyTarget: ReplyTarget? = null,
    ) {
        val c = contact ?: return
        if (!chatRepository.isConnected()) {
            runWhenMessageServiceReady { sendMixed(context, mixedText, uriMap, atMap, replyTarget) }
            return
        }

        _statusText.value = "正在准备消息..."
        val appContext = context.applicationContext
        Thread {
            runCatching {
                val start = ''
                val endMarker = ''
                val elements = arrayListOf<MsgElement>()
                replyTarget?.let { target ->
                    val replyElement = createReplyElement(target)
                        ?: error("回复消息不可用")
                    elements.add(replyElement)
                }
                val textBuf = StringBuilder()
                var index = 0
                while (index < mixedText.length) {
                    val ch = mixedText[index]
                    if (ch == start) {
                        val end = mixedText.indexOf(endMarker, index + 1)
                        if (end < 0) error("图片标记不完整")
                        val token = mixedText.substring(index + 1, end)
                        val text = textBuf.toString()
                        if (text.isNotEmpty()) {
                            elements.add(MsgElement().apply {
                                elementType = 1
                                elementId = 0
                                textElement = TextElement().apply {
                                    content = text
                                    atType = 0
                                    atUid = 0L
                                    atNtUid = ""
                                }
                            })
                            textBuf.clear()
                        }
                        when {
                            token.startsWith("img:") -> {
                                val slotId = token.removePrefix("img:")
                                uriMap[slotId]?.let { uri ->
                                    elements.add(buildPicElement(appContext, uri))
                                } ?: error("图片内容已失效")
                            }

                            token.startsWith("at:") -> {
                                val slotId = token.removePrefix("at:")
                                val mention = atMap[slotId]
                                if (mention == null) {
                                    textBuf.append("@").append(slotId)
                                } else {
                                    val atElement = runCatching {
                                        QRoute.api(IMsgUtilApi::class.java)
                                            .createAtTextElement(
                                                "@${mention.nick}",
                                                mention.uid,
                                                mention.atType
                                            )
                                    }.getOrNull()
                                    if (atElement != null) elements.add(atElement)
                                    else textBuf.append("@").append(mention.nick)
                                }
                            }

                            else -> {
                                uriMap[token]?.let { uri ->
                                    elements.add(buildPicElement(appContext, uri))
                                } ?: textBuf.append(token)
                            }
                        }
                        index = end + 1
                    } else {
                        textBuf.append(ch)
                        index++
                    }
                }
                if (textBuf.isNotEmpty()) {
                    elements.add(MsgElement().apply {
                        elementType = 1
                        elementId = 0
                        textElement = TextElement().apply {
                            content = textBuf.toString()
                            atType = 0
                            atUid = 0L
                            atNtUid = ""
                        }
                    })
                }
                if (elements.isEmpty()) error("没有可发送的内容")

                val sent = chatRepository.sendMessage(c, elements) { code, errMsg ->
                    Log.d(TAG, "sendMixed: code=$code, errMsg=$errMsg, count=${elements.size}")
                    if (code == 0) {
                        val now = System.currentTimeMillis() / 1000
                        val rec = MsgRecord().apply {
                            peerUid = c.peerUid
                            chatType = c.chatType
                            msgTime = now
                            senderUin = selfUin
                            sendNickName = ""
                            sendStatus = 2
                        }
                        runCatching {
                            val field = MsgRecord::class.java.getDeclaredField("elements")
                            field.isAccessible = true
                            field.set(rec, elements)
                        }
                        RecentMessageStore.put(peerId, rec)
                        _statusText.value = ""
                    } else {
                        _statusText.value = "发送失败：${errMsg ?: code}"
                    }
                }
                if (!sent) _statusText.value = "消息服务不可用"
            }.onFailure {
                Log.w(TAG, "sendMixed failed", it)
                _statusText.value = "图片发送失败：${it.message ?: "未知错误"}"
            }
        }.apply { isDaemon = true; start() }
    }

    /** 从 Uri 构建 PicElement（拷贝到 kernel 路径 + 解码尺寸） */
    private fun buildPicElement(context: Context, uri: Uri): MsgElement {
        val tmpFile = File(context.cacheDir, "send_img_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tmpFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("读取图片失败")

        val md5 = md5File(tmpFile)
        val fileName = tmpFile.name
        val fileSize = tmpFile.length()

        val origPath = chatRepository.getMobileQQSendPath(
            RichMediaFilePathInfo(2, 0, md5, fileName, 1, 0, null, "", true),
        ) ?: tmpFile.absolutePath

        if (origPath != tmpFile.absolutePath) {
            runCatching { tmpFile.copyTo(File(origPath), overwrite = true) }
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(origPath, opts)
        val width = opts.outWidth.takeIf { it > 0 } ?: 800
        val height = opts.outHeight.takeIf { it > 0 } ?: 600

        val ext = fileName.substringAfterLast('.', "").lowercase()
        val picType = when (ext) {
            "jpg", "jpeg" -> 1000; "png" -> 1001; "webp" -> 1002; "gif" -> 2000; "bmp" -> 1005; else -> 1001
        }

        val picEl = PicElement().apply {
            sourcePath = origPath; this.fileName = fileName; this.fileSize = fileSize
            md5HexStr = md5; picWidth = width; picHeight = height; this.picType = picType
            picSubType = 0; original = true; storeID = 0
        }
        return MsgElement().apply { elementType = 2; elementId = 0; picElement = picEl }
    }

    fun sendText(text: String) {
        sendText(text, null)
    }

    fun sendText(text: String, replyTarget: ReplyTarget?) {
        val c = contact ?: return
        if (!chatRepository.isConnected()) {
            runWhenMessageServiceReady { sendText(text, replyTarget) }
            return
        }

        val element = MsgElement().apply {
            elementType = 1 // TEXT
            elementId = 0
            textElement = TextElement().apply {
                content = text
                atType = 0
                atUid = 0L
                atNtUid = ""
            }
        }
        val elements = arrayListOf<MsgElement>()
        replyTarget?.let { target ->
            val replyElement = createReplyElement(target)
            if (replyElement == null) {
                _statusText.value = "回复消息不可用"
                return
            }
            elements.add(replyElement)
        }
        elements.add(element)
        val sent = chatRepository.sendMessage(c, elements) { code, errMsg ->
            Log.d(TAG, "sendMsg: code=$code, errMsg=$errMsg")
            if (code == 0) {
                // 构造 MsgRecord 存入 RecentMessageStore
                val now = System.currentTimeMillis() / 1000
                val rec = MsgRecord().apply {
                    peerUid = c.peerUid
                    chatType = c.chatType
                    msgTime = now
                    senderUin = selfUin
                    sendNickName = ""
                    sendStatus = 2 // SENT
                }
                // elements 是 val，用反射写
                runCatching {
                    val f = MsgRecord::class.java.getDeclaredField("elements")
                    f.isAccessible = true
                    f.set(rec, elements)
                }
                RecentMessageStore.put(peerId, rec)
                Log.d(TAG, "sendMsg: put to RecentMessageStore id=$peerId")
            } else {
                _statusText.value = "发送失败: $errMsg"
            }
        }
        if (!sent) _statusText.value = "消息服务不可用"
    }

    private fun createReplyElement(target: ReplyTarget): MsgElement? = runCatching {
        QRoute.api(IMsgUtilApi::class.java)
            .createReplyElement(target.messageId)
            .also { element ->
                element.replyElement?.senderUid = target.senderUid.toLongOrNull() ?: 0L
            }
    }.onFailure {
        Log.w(TAG, "create reply element failed msg=${target.messageId}", it)
    }.getOrNull()

    fun prepareReply(message: UiMsg): Boolean {
        if (message.msgId <= 0L) {
            _statusText.value = "这条消息暂时无法回复"
            return false
        }
        val target = ReplyTarget(
            messageId = message.msgId,
            senderUid = message.senderUid,
            senderName = message.senderNick.ifBlank { "对方" },
            summary = message.text.replace('\n', ' ').take(80).ifBlank { "[消息]" },
            isGroupChat = message.chatType == 2,
        )
        if (createReplyElement(target) == null) {
            _statusText.value = "回复消息不可用"
            return false
        }
        _pendingReplyTarget.value = target
        return true
    }

    fun consumePendingReplyTarget() {
        _pendingReplyTarget.value = null
    }

    private fun emitMessages() {
        _messages.value = synchronized(messageLock) { msgList.map(::toUiMsg) }
    }

    private fun toUiMsg(rec: MsgRecord, forwardRootMessageId: Long? = null): UiMsg {
        val contents = extractContents(rec, forwardRootMessageId)
        return UiMsg(
            stableKey = messageIdentity(rec),
            msgId = rec.msgId,
            msgSeq = rec.msgSeq,
            senderUid = rec.senderUid,
            senderNick = rec.sendNickName ?: "",
            time = rec.msgTime,
            peerUid = rec.peerUid,
            chatType = rec.chatType,
            guildId = rec.guildId.orEmpty(),
            dmFlag = rec.directMsgFlag,
            contents = contents,
            text = contents.summary(),
            isSelf = selfUin > 0 && rec.senderUin == selfUin,
            status = rec.sendStatus,
        )
    }

    private fun isCurrentSession(session: Long): Boolean = chatSession.get() == session

    private fun belongsToContact(record: MsgRecord, expected: Contact): Boolean =
        record.chatType == expected.chatType && record.peerUid == expected.peerUid

    private fun mergeMessages(records: Collection<MsgRecord>, expected: Contact): Int =
        synchronized(messageLock) {
            val known = msgList.mapTo(HashSet<String>()) { record -> messageIdentity(record) }
            var added = 0
            records.asSequence()
                .filter { belongsToContact(it, expected) }
                .filter { known.add(messageIdentity(it)) }
                .forEach {
                    msgList.add(it)
                    added++
                }
            msgList.sortWith(compareBy<MsgRecord> { it.msgTime }.thenBy { it.msgSeq }
                .thenBy { it.clientSeq })
            added
        }

    private fun messageIdentity(record: MsgRecord): String {
        val commonIdentity = listOf(
            record.chatType,
            record.peerUid,
            record.msgId,
            record.msgSeq,
            record.msgRandom,
            record.senderUid,
            record.msgTime,
        ).joinToString(separator = ":")
        if (record.msgId != 0L) return "message:$commonIdentity"
        return "local:$commonIdentity:${record.clientSeq}:${localElementIdentity(record)}"
    }

    private fun localElementIdentity(record: MsgRecord): String =
        record.elements?.joinToString(separator = ",") { element ->
            when (element.elementType) {
                1 -> "${element.elementId}:1:${element.textElement?.content.orEmpty()}"
                2 -> "${element.elementId}:2:${element.picElement?.md5HexStr.orEmpty()}:${element.picElement?.fileName.orEmpty()}"
                else -> "${element.elementId}:${element.elementType}"
            }
        }.orEmpty()

    override fun onCleared() {
        chatSession.incrementAndGet()
        forwardDetailRequest.incrementAndGet()
        replySourceRequest.incrementAndGet()
        replyTimeoutHandler.removeCallbacksAndMessages(null)
        OfficialPttPlayer.stopAndRelease()
        chatRepository.close()
        RichMediaRepository.setInvalidationListener(null)
        LinkPreviewRepository.setInvalidationListener(null)
        super.onCleared()
    }

    private fun extractContents(
        rec: MsgRecord,
        forwardRootMessageId: Long? = null,
    ): List<MessageContent> = buildList {
        rec.elements?.forEach { element ->
            val content = runCatching {
                when {
                    element.grayTipElement != null -> MessageContent.SystemTip(
                        extractSystemTip(
                            element
                        )
                    )

                    element.avRecordElement != null -> element.avRecordElement?.let { record ->
                        MessageContent.CallRecord(
                            text = record.text?.takeIf { it.isNotBlank() } ?: "通话记录",
                            type = record.type,
                            time = record.time,
                            hasRead = record.hasRead,
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.prologueMsgElement != null -> MessageContent.Text(
                        element.prologueMsgElement?.text.orEmpty().ifBlank { "会话开始" },
                    )

                    element.taskTopMsgElement != null -> element.taskTopMsgElement?.let { task ->
                        MessageContent.Card(
                            title = task.msgTitle?.takeIf { it.isNotBlank() } ?: "任务消息",
                            description = task.msgSummary.orEmpty(),
                            tag = "任务",
                            previewUrl = task.iconUrl?.takeIf { it.isNotBlank() },
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.actionBarElement != null -> element.actionBarElement?.let { actionBar ->
                        MessageContent.InlineKeyboard(
                            actionBar.botAppid,
                            extractKeyboardRows(actionBar.rows)
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.recommendedMsgElement != null -> element.recommendedMsgElement?.let { recommended ->
                        MessageContent.InlineKeyboard(
                            recommended.botAppid,
                            extractKeyboardRows(recommended.rows)
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.yoloGameResultElement != null -> element.yoloGameResultElement?.let { result ->
                        MessageContent.Card(
                            title = "游戏结果",
                            description = result.userInfo.orEmpty().joinToString("\n") { user ->
                                "${user.uid.orEmpty()} · 排名 ${user.rank} · 结果 ${user.result}"
                            },
                            tag = "互动消息",
                            previewUrl = null,
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.tofuRecordElement != null -> element.tofuRecordElement?.let { tofu ->
                        MessageContent.Card(
                            title = tofu.descriptionContent?.title?.takeIf { it.isNotBlank() }
                                ?: "互动消息",
                            description = tofu.contentlist.orEmpty()
                                .mapNotNull { it.title?.takeIf(String::isNotBlank) }
                                .joinToString("\n"),
                            tag = "互动消息",
                            previewUrl = tofu.icon?.takeIf { it.isNotBlank() },
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.replyElement != null -> element.replyElement?.let { reply ->
                        MessageContent.Reply(
                            senderName = reply.anonymousNickName?.takeIf { it.isNotBlank() }
                                ?: reply.senderUidStr?.takeIf { it.isNotBlank() }
                                ?: "回复消息",
                            summary = reply.sourceMsgText?.takeIf { it.isNotBlank() }
                                ?: reply.sourceMsgTextElems
                                    ?.joinToString("") { it.textElemContent.orEmpty() }
                                    ?.takeIf { it.isNotBlank() }
                                ?: if (reply.sourceMsgIsIncPic) "[图片]" else "原消息",
                            targetMessageId = reply.replayMsgId,
                            targetSequence = reply.replayMsgSeq,
                            expired = reply.sourceMsgExpired,
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.picElement != null -> element.picElement?.let { picture ->
                        val requestState = RichMediaRepository.requestState(
                            RichMediaKey(rec.msgId, element.elementId),
                        )
                        MessageContent.Image(
                            sourcePath = picture.sourcePath?.takeIf { it.isNotBlank() },
                            elementId = element.elementId,
                            localPaths = (
                                    listOfNotNull(picture.sourcePath?.takeIf { it.isNotBlank() }) +
                                            RichMediaRepository.resolveLocalPicturePaths(element)
                                    ).distinct(),
                            thumbnailPaths = picture.thumbPath
                                ?.values
                                ?.filter { it.isNotBlank() }
                                ?.distinct()
                                .orEmpty(),
                            width = picture.picWidth,
                            height = picture.picHeight,
                            transferStatus = picture.transferStatus,
                            isLoading = requestState is RichMediaRequestState.Loading,
                            loadError = (requestState as? RichMediaRequestState.Failed)?.message,
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.marketFaceElement != null -> element.marketFaceElement?.let { face ->
                        MessageContent.MarketFace(
                            name = face.faceName?.takeIf { it.isNotBlank() } ?: "[动画表情]",
                            staticPath = face.staticFacePath?.takeIf { it.isNotBlank() },
                            dynamicPath = face.dynamicFacePath?.takeIf { it.isNotBlank() },
                            width = face.imageWidth,
                            height = face.imageHeight,
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.giphyElement != null -> element.giphyElement?.let { giphy ->
                        MessageContent.Giphy(
                            id = giphy.id?.takeIf { it.isNotBlank() }.orEmpty(),
                            mediaUrl = giphyMediaUrl(giphy.id),
                            width = giphy.width,
                            height = giphy.height,
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.faceElement != null -> element.faceElement?.let { face ->
                        MessageContent.Face(
                            text = face.faceText?.takeIf { it.isNotBlank() } ?: "[表情]",
                            packId = face.packId?.takeIf { it.isNotBlank() },
                            stickerId = face.stickerId?.takeIf { it.isNotBlank() },
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.fileElement != null -> element.fileElement?.let { file ->
                        val requestState = RichMediaRepository.requestState(
                            RichMediaKey(rec.msgId, element.elementId),
                        )
                        MessageContent.File(
                            elementId = element.elementId,
                            name = file.fileName?.takeIf { it.isNotBlank() } ?: "未命名文件",
                            path = file.filePath?.takeIf { it.isNotBlank() },
                            sizeBytes = file.fileSize,
                            progress = file.progress,
                            transferStatus = file.transferStatus,
                            fileUuid = file.fileUuid?.takeIf { it.isNotBlank() },
                            fileMd5 = file.fileMd5?.takeIf { it.isNotBlank() },
                            fileSha = file.fileSha?.takeIf { it.isNotBlank() },
                            fileBizId = file.fileBizId,
                            fileSubId = file.fileSubId?.takeIf { it.isNotBlank() },
                            fileTransType = file.fileTransType,
                            folderId = file.folderId?.takeIf { it.isNotBlank() },
                            expireTime = file.expireTime,
                            invalidState = file.invalidState,
                            pictureWidth = file.picWidth,
                            pictureHeight = file.picHeight,
                            thumbnailPaths = file.picThumbPath?.values?.filter { it.isNotBlank() }
                                .orEmpty(),
                            videoDurationSeconds = file.videoDuration,
                            isDownloading = requestState is RichMediaRequestState.Loading,
                            downloadError = (requestState as? RichMediaRequestState.Failed)?.message,
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.pttElement != null -> element.pttElement?.let { voice ->
                        MessageContent.Voice(
                            media = PttMediaRef(
                                messageId = rec.msgId,
                                elementId = element.elementId,
                                filePath = voice.filePath?.takeIf { it.isNotBlank() },
                                md5Hex = voice.md5HexStr?.takeIf { it.isNotBlank() },
                                fileName = voice.fileName?.takeIf { it.isNotBlank() },
                                importRichMediaContext = voice.importRichMediaContext,
                                fileUuid = voice.fileUuid?.takeIf { it.isNotBlank() },
                                durationSeconds = voice.duration,
                            ),
                            progress = voice.progress,
                            transferStatus = voice.transferStatus,
                            transcript = voice.text?.takeIf { it.isNotBlank() },
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.videoElement != null -> element.videoElement?.let { video ->
                        MessageContent.Video(
                            filePath = video.filePath?.takeIf { it.isNotBlank() },
                            thumbnailPaths = video.thumbPath?.values?.filter { it.isNotBlank() }
                                .orEmpty(),
                            width = video.thumbWidth,
                            height = video.thumbHeight,
                            durationSeconds = video.fileTime,
                            progress = video.progress,
                            transferStatus = video.transferStatus,
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.walletElement != null -> element.walletElement?.let { wallet ->
                        extractWalletCard(wallet, rec.senderUin == selfUin)
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.calendarElement != null -> element.calendarElement?.let(::extractCalendarCard)
                        ?: MessageContent.Unsupported(element.elementType)

                    element.textGiftElement != null -> element.textGiftElement?.let(::extractTextGiftCard)
                        ?: MessageContent.Unsupported(element.elementType)

                    element.liveGiftElement != null -> element.liveGiftElement?.let(::extractLiveGiftCard)
                        ?: MessageContent.Unsupported(element.elementType)

                    element.arkElement != null -> element.arkElement?.let(::extractArkCard)
                        ?: MessageContent.Unsupported(element.elementType)

                    element.structLongMsgElement != null -> element.structLongMsgElement?.let { struct ->
                        extractStructCard(struct.xmlContent.orEmpty())
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.structMsgElement != null -> element.structMsgElement?.let { struct ->
                        extractStructCard(struct.xmlContent.orEmpty())
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.multiForwardMsgElement != null -> element.multiForwardMsgElement?.let { forward ->
                        extractForwardCard(
                            xmlContent = forward.xmlContent.orEmpty(),
                            resourceId = forward.resId,
                            rootMessageId = forwardRootMessageId ?: rec.msgId,
                            rawMessageId = rec.msgId,
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.markdownElement != null -> MessageContent.Markdown(
                        element.markdownElement?.content.orEmpty(),
                    )

                    element.shareLocationElement != null -> element.shareLocationElement?.let { location ->
                        MessageContent.Location(
                            title = location.text?.takeIf { it.isNotBlank() } ?: "位置分享",
                            detail = location.ext?.takeIf { it.isNotBlank() }.orEmpty(),
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.inlineKeyboardElement != null -> element.inlineKeyboardElement?.let(::extractInlineKeyboard)
                        ?: MessageContent.Unsupported(element.elementType)

                    element.faceBubbleElement != null -> element.faceBubbleElement?.let { faceBubble ->
                        MessageContent.Text(
                            firstNonBlank(
                                faceBubble.faceSummary,
                                faceBubble.content,
                                faceBubble.oldVersionStr,
                            ) ?: "[表情]",
                        )
                    } ?: MessageContent.Unsupported(element.elementType)

                    element.textElement != null -> MessageContent.Text(element.textElement?.content.orEmpty())
                    else -> MessageContent.Unsupported(
                        element.elementType,
                        element.javaClass.simpleName
                    )
                }
            }.getOrElse { error ->
                Log.w(TAG, "chatDetail: render element failed type=${element.elementType}", error)
                MessageContent.Unsupported(element.elementType, "渲染失败，可重试")
            }
            if (content !is MessageContent.Text || content.value.isNotEmpty()) add(content)
        }
        val previewUrl = asSequence()
            .filterIsInstance<MessageContent.Text>()
            .mapNotNull { content -> LinkPreviewRepository.firstSupportedUrl(content.value) }
            .firstOrNull()
            ?: asSequence()
                .filterIsInstance<MessageContent.Markdown>()
                .mapNotNull { content -> LinkPreviewRepository.firstSupportedUrl(content.value) }
                .firstOrNull()
        if (previewUrl != null) {
            add(MessageContent.LinkPreview(previewUrl, LinkPreviewRepository.state(previewUrl)))
        }
        if (isEmpty()) add(MessageContent.Unsupported(0))
    }

    private fun List<MessageContent>.summary(): String = joinToString(separator = "") { content ->
        when (content) {
            is MessageContent.Text -> content.value
            is MessageContent.Image -> "[图片]"
            is MessageContent.Face -> content.text
            is MessageContent.MarketFace -> content.name
            is MessageContent.Giphy -> "[GIF]"
            is MessageContent.Voice -> "[语音]"
            is MessageContent.Video -> "[视频]"
            is MessageContent.File -> "[文件]"
            is MessageContent.Reply -> "[回复]"
            is MessageContent.Card -> content.title
            is MessageContent.StructCard -> content.title
            is MessageContent.Forward -> content.title
            is MessageContent.SystemTip -> content.text
            is MessageContent.Location -> content.title
            is MessageContent.Wallet -> content.title
            is MessageContent.Calendar -> content.title
            is MessageContent.InlineKeyboard -> "[机器人消息]"
            is MessageContent.Markdown -> content.value
            is MessageContent.LinkPreview -> ""
            is MessageContent.CallRecord -> content.text
            is MessageContent.Unsupported -> content.detail?.let { "[$it]" } ?: "[暂不支持的消息]"
        }
    }.ifEmpty { "..." }

    private fun extractArkCard(ark: com.tencent.qqnt.kernel.nativeinterface.ArkElement): MessageContent.Card {
        val metadata = RichMessageMetadataParser.parseArkCard(ark.bytesData)
        return MessageContent.Card(
            title = metadata.title,
            description = metadata.description,
            tag = metadata.tag,
            previewUrl = metadata.previewUrl,
            actionUrl = metadata.actionUrl,
        )
    }

    private fun extractStructCard(xmlContent: String): MessageContent.StructCard {
        val metadata = RichMessageMetadataParser.parseStructCard(xmlContent)
        return MessageContent.StructCard(metadata.title, metadata.description, metadata.groupCode)
    }

    private fun extractWalletCard(
        wallet: com.tencent.qqnt.kernel.nativeinterface.WalletElement,
        isSelf: Boolean,
    ): MessageContent.Wallet {
        val display = if (isSelf) wallet.sender else wallet.receiver
        val fallbackDisplay = if (isSelf) wallet.receiver else wallet.sender
        val title = firstNonBlank(
            display?.title,
            fallbackDisplay?.title,
            wallet.name,
        ) ?: "QQ红包"
        val description = firstNonBlank(
            display?.content,
            fallbackDisplay?.content,
            display?.subTitle,
            fallbackDisplay?.subTitle,
        ) ?: "红包消息"
        val notice = firstNonBlank(display?.notice, fallbackDisplay?.notice)
        val iconUrl = firstNonBlank(display?.iconUrl, fallbackDisplay?.iconUrl)
            ?.takeIf(::isHttpUrl)
        return MessageContent.Wallet(title, description, notice, iconUrl)
    }

    private fun extractCalendarCard(
        calendar: com.tencent.qqnt.kernel.nativeinterface.CalendarElement,
    ): MessageContent.Calendar = MessageContent.Calendar(
        title = firstNonBlank(calendar.summary, calendar.msg) ?: "日程提醒",
        description = calendar.msg?.takeIf { it.isNotBlank() }
            ?.takeUnless { it == calendar.summary }
            .orEmpty(),
        expired = calendar.expireTimeMs > 0 && calendar.expireTimeMs <= System.currentTimeMillis(),
    )

    private fun extractTextGiftCard(
        gift: com.tencent.qqnt.kernel.nativeinterface.TextGiftElement,
    ): MessageContent.Card {
        val sender = gift.senderNick?.takeIf { it.isNotBlank() }
        val receiver = gift.receiverNick?.takeIf { it.isNotBlank() }
        val description = when {
            sender != null && receiver != null -> "$sender 赠送给 $receiver"
            receiver != null -> "赠送给 $receiver"
            else -> "礼物消息"
        }
        return MessageContent.Card(
            title = gift.giftName?.takeIf { it.isNotBlank() } ?: "礼物",
            description = description,
            tag = "礼物",
            previewUrl = gift.bgImageUrl?.takeIf(::isHttpUrl),
        )
    }

    private fun extractLiveGiftCard(
        gift: com.tencent.qqnt.kernel.nativeinterface.LiveGiftElement,
    ): MessageContent.Card = MessageContent.Card(
        title = gift.kStrGiftName?.takeIf { it.isNotBlank() } ?: "直播礼物",
        description = gift.kUInt64GiftNum
            .takeIf { it > 1L }
            ?.let { "数量 × $it" }
            .orEmpty(),
        tag = "直播礼物",
        previewUrl = null,
    )

    private fun extractInlineKeyboard(
        keyboard: com.tencent.qqnt.kernel.nativeinterface.InlineKeyboardElement,
    ): MessageContent.InlineKeyboard = MessageContent.InlineKeyboard(
        botAppid = keyboard.botAppid,
        rows = extractKeyboardRows(keyboard.rows),
    )

    private fun extractKeyboardRows(
        rows: List<com.tencent.qqnt.kernel.nativeinterface.InlineKeyboardRow>?,
    ): List<List<MessageContent.InlineKeyboardButton>> = rows.orEmpty()
        .take(4)
        .mapIndexedNotNull { rowIndex, row ->
            row.buttons.orEmpty()
                .take(4)
                .mapIndexed { columnIndex, button ->
                    MessageContent.InlineKeyboardButton(
                        stableKey = "$rowIndex:$columnIndex:${button.id.orEmpty()}:${button.data.orEmpty()}",
                        id = button.id.orEmpty(),
                        data = button.data.orEmpty(),
                        label = firstNonBlank(button.label, button.visitedLabel) ?: "操作",
                        visitedLabel = firstNonBlank(button.visitedLabel),
                        type = button.type,
                        unsupportTips = firstNonBlank(button.unsupportTips),
                    )
                }
                .takeIf(List<MessageContent.InlineKeyboardButton>::isNotEmpty)
        }

    fun inlineKeyboardActionKey(
        message: UiMsg,
        button: MessageContent.InlineKeyboardButton,
    ): String = "${message.stableKey}:keyboard:${button.stableKey}"

    fun clickInlineKeyboardButton(
        message: UiMsg,
        keyboard: MessageContent.InlineKeyboard,
        button: MessageContent.InlineKeyboardButton,
    ): Boolean {
        val actionKey = inlineKeyboardActionKey(message, button)
        if (_inlineKeyboardActions.value[actionKey]?.phase == InlineKeyboardActionPhase.Pending) return true
        if (button.id.isBlank() && button.data.isBlank()) {
            _statusText.value = button.unsupportTips ?: "此按钮缺少操作参数"
            return false
        }
        _inlineKeyboardActions.value = _inlineKeyboardActions.value + (
                actionKey to InlineKeyboardActionState(InlineKeyboardActionPhase.Pending, "处理中…")
                )
        val info = InlineKeyboardClickInfo().apply {
            botAppid = keyboard.botAppid
            buttonId = button.id
            callbackData = button.data
            msgSeq = message.msgSeq
            peerId = message.peerUid
            guildId = message.guildId
            chatType = message.chatType
            dmFlag = message.dmFlag
        }
        val requested =
            chatRepository.clickInlineKeyboardButton(info) { errorCode, errorMessage, resultCode, resultMessage ->
                val success = errorCode == 0 && resultCode == 0
                if (success) {
                    val label = button.visitedLabel ?: button.label
                    _inlineKeyboardActions.value = _inlineKeyboardActions.value + (
                            actionKey to InlineKeyboardActionState(
                                InlineKeyboardActionPhase.Succeeded,
                                label
                            )
                            )
                    _statusText.value = "操作已发送"
                } else {
                    _inlineKeyboardActions.value = _inlineKeyboardActions.value - actionKey
                    _statusText.value = "操作失败：${
                        firstNonBlank(
                            resultMessage,
                            errorMessage
                        ) ?: "${errorCode}/${resultCode}"
                    }"
                }
            }
        if (!requested) {
            _inlineKeyboardActions.value = _inlineKeyboardActions.value - actionKey
            _statusText.value = "操作失败：消息服务不可用"
        }
        return requested
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://") || value.startsWith("http://")

    private fun giphyMediaUrl(id: String?): String? {
        val value = id?.trim().orEmpty()
        return when {
            value.startsWith("https://") || value.startsWith("http://") -> value
            value.matches(Regex("[A-Za-z0-9_-]{3,128}")) -> "https://i.giphy.com/$value.gif"
            else -> null
        }
    }

    private fun extractForwardCard(
        xmlContent: String,
        resourceId: String?,
        rootMessageId: Long,
        rawMessageId: Long,
    ): MessageContent.Forward {
        val metadata = RichMessageMetadataParser.parseForward(xmlContent)
        return MessageContent.Forward(
            title = metadata.title,
            preview = metadata.preview,
            resourceId = resourceId?.takeIf { it.isNotBlank() },
            rootMessageId = rootMessageId,
            rawMessageId = rawMessageId,
        )
    }

    private fun extractSystemTip(element: MsgElement): String {
        val gray = element.grayTipElement ?: return "系统消息"
        val direct = gray.jsonGrayTipElement?.recentAbstract
            ?: gray.jsonGrayTipElement?.jsonStr
            ?: gray.xmlElement?.content
            ?: gray.localGrayTipElement?.extraJson
        return RichMessageMetadataParser.parseSystemTip(direct)
    }

    fun loadOlderMessages(): Boolean {
        if (_isLoadingOlder.value || !_hasOlderMessages.value) return false
        val expected = contact ?: return false
        val session = chatSession.get()
        val oldest = synchronized(messageLock) { msgList.firstOrNull() } ?: return false

        _isLoadingOlder.value = true
        Log.d(
            TAG,
            "chatDetail: request older msg=${oldest.msgId}, time=${oldest.msgTime}, seq=${oldest.msgSeq}",
        )
        val requested = chatRepository.loadOlder(
            ChatRepository.HistoryRequest(
                contact = expected,
                anchorMessageId = oldest.msgId,
                anchorMessageTime = oldest.msgTime,
                count = HISTORY_PAGE_COUNT,
            ),
        ) { result, errMsg, records ->
            if (!isCurrentSession(session)) return@loadOlder
            val added = if (result == 0 && records != null) mergeMessages(records, expected) else 0
            if (added > 0) emitMessages()
            if (result == 0 && added == 0) {
                Log.d(TAG, "chatDetail: older page had no new records; keeping paging available")
            }
            _isLoadingOlder.value = false
            _olderPageVersion.value++
            Log.d(
                TAG,
                "chatDetail: load older result=$result, records=${records?.size}, added=$added, err=$errMsg",
            )
        }
        if (!requested) _isLoadingOlder.value = false
        return requested
    }

    fun ensureImageCached(message: UiMsg, image: MessageContent.Image) {
        val hasLocalFile =
            (image.localPaths + image.thumbnailPaths + listOfNotNull(image.sourcePath))
                .let(LocalMediaResolver::hasAvailableFile)
        if (hasLocalFile || image.elementId <= 0L) return
        RichMediaRepository.requestImageThumbnail(
            messageId = message.msgId,
            peerUid = message.peerUid,
            chatType = message.chatType,
            elementId = image.elementId,
        )
        emitMessages()
    }

    fun toggleVoicePlayback(voice: MessageContent.Voice) {
        OfficialPttPlayer.toggle(voice.media)
    }

    fun loadForwardDetail(forward: MessageContent.Forward) {
        val requestId = forwardDetailRequest.incrementAndGet()
        val previousReadyState = _forwardDetailState.value as? ForwardDetailState.Ready
        val expectedContact = contact
        if (expectedContact == null) {
            previousReadyState?.let(forwardDetailBackStack::addLast)
            _forwardDetailState.value = ForwardDetailState.Error(forward.title, "聊天记录暂不可用")
            return
        }
        if (forward.rootMessageId <= 0L || forward.rawMessageId <= 0L) {
            previousReadyState?.let(forwardDetailBackStack::addLast)
            _forwardDetailState.value = ForwardDetailState.Error(forward.title, "聊天记录标识无效")
            return
        }

        previousReadyState?.let(forwardDetailBackStack::addLast)
        _forwardDetailState.value = ForwardDetailState.Loading(forward.title)
        val requested = chatRepository.loadForwardDetail(
            contact = expectedContact,
            rootMessageId = forward.rootMessageId,
            rawMessageId = forward.rawMessageId,
        ) { errorCode, errMsg, records ->
            if (forwardDetailRequest.get() != requestId) return@loadForwardDetail
            if (errorCode != 0 || records.isNullOrEmpty()) {
                Log.w(
                    TAG,
                    "chatDetail: getMultiMsg code=$errorCode count=${records?.size} err=$errMsg",
                )
                _forwardDetailState.value = ForwardDetailState.Error(
                    forward.title,
                    errMsg?.takeIf { it.isNotBlank() } ?: "聊天记录暂不可用",
                )
                return@loadForwardDetail
            }
            val detailMessages = records
                .sortedWith(compareBy<MsgRecord> { it.msgTime }.thenBy { it.msgSeq }
                    .thenBy { it.clientSeq })
                .map { record -> toUiMsg(record, forward.rootMessageId) }
            _forwardDetailState.value = ForwardDetailState.Ready(forward.title, detailMessages)
        }
        if (!requested && forwardDetailRequest.get() == requestId) {
            _forwardDetailState.value = ForwardDetailState.Error(forward.title, "聊天记录暂不可用")
        }
    }

    fun dismissForwardDetail() {
        forwardDetailRequest.incrementAndGet()
        _forwardDetailState.value = forwardDetailBackStack.removeLastOrNull()
            ?: ForwardDetailState.Idle
    }

    fun loadReplySource(reply: MessageContent.Reply) {
        val currentState = _replySourceState.value
        if (
            currentState is ReplySourceState.Loading &&
            currentState.messageId == reply.targetMessageId &&
            currentState.sequence == reply.targetSequence
        ) {
            return
        }

        val requestId = replySourceRequest.incrementAndGet()
        replyTimeoutHandler.removeCallbacksAndMessages(null)
        val expectedContact = contact ?: run {
            _replySourceState.value = ReplySourceState.Error(
                reply.targetMessageId,
                reply.targetSequence,
                "原消息暂不可用",
            )
            return
        }
        if (reply.targetMessageId <= 0L) {
            _replySourceState.value = ReplySourceState.Error(
                reply.targetMessageId,
                reply.targetSequence,
                "原消息标识无效",
            )
            return
        }

        val session = chatSession.get()
        _replySourceState.value =
            ReplySourceState.Loading(reply.targetMessageId, reply.targetSequence)
        replyTimeoutHandler.postDelayed({
            if (isCurrentSession(session) && replySourceRequest.get() == requestId) {
                _replySourceState.value = ReplySourceState.Error(
                    reply.targetMessageId,
                    reply.targetSequence,
                    "原消息加载超时",
                )
            }
        }, REPLY_SOURCE_TIMEOUT_MS)
        val requested = chatRepository.loadReplySource(
            contact = expectedContact,
            messageId = reply.targetMessageId,
            messageSequence = reply.targetSequence ?: 0L,
        ) { errorCode, errMsg, records ->
            if (!isCurrentSession(session) || replySourceRequest.get() != requestId) return@loadReplySource
            replyTimeoutHandler.removeCallbacksAndMessages(null)
            val added = if (errorCode == 0 && records != null) {
                mergeMessages(records, expectedContact)
            } else {
                0
            }
            if (added > 0) emitMessages()
            if (errorCode == 0 && (added > 0 || records?.isNotEmpty() == true)) {
                _replySourceState.value =
                    ReplySourceState.Loaded(reply.targetMessageId, reply.targetSequence)
            } else {
                _replySourceState.value = ReplySourceState.Error(
                    reply.targetMessageId,
                    reply.targetSequence,
                    errMsg?.takeIf { it.isNotBlank() } ?: "未找到原消息",
                )
            }
        }
        if (!requested && isCurrentSession(session) && replySourceRequest.get() == requestId) {
            replyTimeoutHandler.removeCallbacksAndMessages(null)
            _replySourceState.value = ReplySourceState.Error(
                reply.targetMessageId,
                reply.targetSequence,
                "消息服务不可用",
            )
        }
    }

    fun recallMessage(msgId: Long) {
        val c = contact ?: return
        if (!chatRepository.isConnected()) {
            _statusText.value = "消息服务不可用"
            return
        }
        chatRepository.recallMessage(c, msgId) { code, errMsg ->
            Log.d(TAG, "recallMessage: code=$code, errMsg=$errMsg")
            if (code == 0) {
                synchronized(messageLock) { msgList.removeAll { it.msgId == msgId } }
                emitMessages()
            } else {
                _statusText.value = "撤回失败: ${errMsg ?: "未知错误"}"
            }
        }
    }

    fun deleteMessage(msgId: Long) {
        deleteMessages(listOf(msgId))
    }

    fun deleteMessages(msgIds: List<Long>) {
        val c = contact ?: return
        if (!chatRepository.isConnected()) {
            _statusText.value = "消息服务不可用"
            return
        }
        val idSet = msgIds.toHashSet()
        chatRepository.deleteMessages(c, ArrayList(msgIds)) { code, errMsg ->
            Log.d(TAG, "deleteMessages: code=$code, errMsg=$errMsg, count=${msgIds.size}")
            if (code == 0) {
                synchronized(messageLock) { msgList.removeAll { it.msgId in idSet } }
                emitMessages()
            } else {
                _statusText.value = "删除失败: ${errMsg ?: "未知错误"}"
            }
        }
    }

    fun resendMessage(msgId: Long) {
        val c = contact ?: return
        if (!chatRepository.isConnected()) {
            _statusText.value = "消息服务不可用"
            return
        }
        chatRepository.resendMessage(c, msgId) { code, errMsg ->
            Log.d(TAG, "resendMessage: code=$code, errMsg=$errMsg")
            if (code != 0) {
                _statusText.value = "重发失败: ${errMsg ?: "未知错误"}"
            }
        }
    }

    fun forwardMessage(targetChatType: Int, targetPeerUid: String, message: UiMsg) {
        if (!chatRepository.isConnected()) {
            _statusText.value = "消息服务不可用"
            return
        }
        val target = Contact(targetChatType, targetPeerUid, "")
        val elements = rebuildElementsForForward(message)
        if (elements.isEmpty()) {
            _statusText.value = "暂不支持转发此类型消息"
            return
        }
        Thread {
            val sent = chatRepository.sendMessage(target, elements) { code, errMsg ->
                Log.d(TAG, "forwardMessage: code=$code, errMsg=$errMsg")
                if (code == 0) {
                    _statusText.value = "已转发"
                } else {
                    _statusText.value = "转发失败: ${errMsg ?: "未知错误"}"
                }
            }
            if (!sent) _statusText.value = "消息服务不可用"
        }.apply { isDaemon = true; start() }
    }

    private fun rebuildElementsForForward(message: UiMsg): ArrayList<MsgElement> {
        val elements = ArrayList<MsgElement>()
        for (item in message.contents) {
            when (item) {
                is MessageContent.Text -> if (item.value.isNotBlank()) {
                    elements.add(MsgElement().apply {
                        elementType = 1; elementId = 0
                        textElement = TextElement().apply {
                            content = item.value; atType = 0; atUid = 0L; atNtUid = ""
                        }
                    })
                }

                is MessageContent.Image -> {
                    val file =
                        (item.localPaths + item.thumbnailPaths + listOfNotNull(item.sourcePath))
                            .asSequence()
                            .map { File(it.removePrefix("file://")) }
                            .firstOrNull(File::isFile)
                    if (file != null) {
                        runCatching {
                            elements.add(buildPicElementFromFile(file))
                        }.onFailure { Log.w(TAG, "forward: rebuild image failed", it) }
                    }
                }

                else -> {} // 其他类型暂不支持转发
            }
        }
        return elements
    }

    private fun buildPicElementFromFile(file: File): MsgElement {
        val md5 = md5File(file)
        val fileName = file.name
        val fileSize = file.length()

        val origPath = chatRepository.getMobileQQSendPath(
            RichMediaFilePathInfo(2, 0, md5, fileName, 1, 0, null, "", true),
        ) ?: file.absolutePath

        if (origPath != file.absolutePath) {
            runCatching { file.copyTo(File(origPath), overwrite = true) }
        }

        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(origPath, opts)
        val width = opts.outWidth.takeIf { it > 0 } ?: 800
        val height = opts.outHeight.takeIf { it > 0 } ?: 600

        val ext = fileName.substringAfterLast('.', "").lowercase()
        val picType = when (ext) {
            "jpg", "jpeg" -> 1000; "png" -> 1001; "webp" -> 1002; "gif" -> 2000; "bmp" -> 1005; else -> 1001
        }

        val picEl = PicElement().apply {
            sourcePath = origPath; this.fileName = fileName; this.fileSize = fileSize
            md5HexStr = md5; picWidth = width; picHeight = height; this.picType = picType
            picSubType = 0; original = true; storeID = 0
        }
        return MsgElement().apply { elementType = 2; elementId = 0; picElement = picEl }
    }

    fun clearReplySourceState() {
        replySourceRequest.incrementAndGet()
        replyTimeoutHandler.removeCallbacksAndMessages(null)
        _replySourceState.value = ReplySourceState.Idle
    }

    fun setPendingForward(message: UiMsg) {
        _pendingForwardMessage.value = message
    }

    fun consumePendingForward(targetChatType: Int, targetPeerUid: String) {
        val msg = _pendingForwardMessage.value ?: return
        _pendingForwardMessage.value = null
        forwardMessage(targetChatType, targetPeerUid, msg)
    }

    fun clearPendingForward() {
        _pendingForwardMessage.value = null
    }

    fun enterMultiSelect(seedMsgId: Long) {
        _multiSelectMode.value = true
        _selectedMsgIds.value = linkedSetOf(seedMsgId)
    }

    fun exitMultiSelect() {
        _multiSelectMode.value = false
        _selectedMsgIds.value = linkedSetOf()
    }

    fun toggleSelection(msgId: Long) {
        val current = _selectedMsgIds.value
        val updated = LinkedHashSet(current)
        if (msgId in updated) updated.remove(msgId) else updated.add(msgId)
        _selectedMsgIds.value = updated
    }

    fun batchCopySelected(): String {
        val ids = _selectedMsgIds.value
        val allMessages = _messages.value
        return ids.mapNotNull { id -> allMessages.firstOrNull { it.msgId == id } }
            .mapNotNull { msg ->
                val text = msg.contents
                    .filterIsInstance<MessageContent.Text>()
                    .joinToString("") { it.value }
                text.takeIf { it.isNotBlank() }
            }
            .joinToString("\n\n")
    }

    fun batchDeleteSelected() {
        val ids = _selectedMsgIds.value.toList()
        if (ids.isEmpty()) return
        deleteMessages(ids)
        exitMultiSelect()
    }

    fun beginEdit(msgId: Long, text: String) {
        _editingMsgId.value = msgId
        _editingText.value = text
    }

    fun cancelEdit() {
        _editingMsgId.value = 0L
        _editingText.value = ""
    }

    fun sendEditedText(newText: String) {
        val editId = _editingMsgId.value
        if (editId == 0L) {
            sendText(newText)
            return
        }
        cancelEdit()
        val c = contact ?: return
        if (!chatRepository.isConnected()) {
            _statusText.value = "消息服务不可用"
            return
        }
        // 先撤回原消息，再发送新内容
        chatRepository.recallMessage(c, editId) { code, errMsg ->
            Log.d(TAG, "editRecall: code=$code, errMsg=$errMsg")
            if (code == 0) {
                synchronized(messageLock) { msgList.removeAll { it.msgId == editId } }
                emitMessages()
            }
            // 无论撤回是否成功都发送新消息
            sendText(newText)
        }
    }

}
