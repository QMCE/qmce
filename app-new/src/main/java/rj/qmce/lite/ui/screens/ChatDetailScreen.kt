@file:OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)

package rj.qmce.lite.ui.screens

import android.content.pm.PackageManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.curvedRow
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.touchTargetAwareSize
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mqq.app.AppRuntime
import rj.qmce.lite.data.call.CallMode
import rj.qmce.lite.data.call.CallStartResult
import rj.qmce.lite.data.call.QmceCallController
import rj.qmce.lite.data.chat.PttPlaybackPhase
import rj.qmce.lite.data.chat.PttPlaybackState
import rj.qmce.lite.ui.components.CurvedCard
import rj.qmce.lite.ui.components.curvedCompactButton
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface ChatTimelineItem {
    val key: String

    data class DateDivider(
        val label: String,
        override val key: String,
    ) : ChatTimelineItem

    data class Message(
        val message: ChatDetailViewModel.UiMsg,
    ) : ChatTimelineItem {
        override val key: String = "message:${message.stableKey}"
    }
}

private data class HistoryAnchor(
    val messageKey: String,
    val viewportOffset: Int,
    val resultVersion: Long,
)

private data class TimelineScrollState(
    val isScrolling: Boolean,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemOffset: Int,
    val canScrollBackward: Boolean,
)

private data class FileDetailTarget(
    val message: ChatDetailViewModel.UiMsg,
    val content: ChatDetailViewModel.MessageContent.File,
)

internal data class VideoPlayback(
    val file: File,
    val title: String,
)

@Composable
fun ChatDetailScreen(
    runtime: AppRuntime?,
    peerUid: String,
    peerUin: String,
    chatType: Int,
    peerName: String,
    myUin: String = "",
    onBack: () -> Unit,
    onOpenInput: () -> Unit = {},
    onOpenVoiceRecorder: () -> Unit = {},
    onOpenContactPicker: () -> Unit = {},
    onOpenPacketTool: () -> Unit = {},
    onOpenMembers: () -> Unit = {},
    onOpenChatSettings: () -> Unit = {},
    avatarPath: String = "",
    avatarUrl: String = "",
    vm: ChatDetailViewModel = viewModel(),
) {
    val messages by vm.messages.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val name by vm.peerName.collectAsState()
    val isLoadingOlder by vm.isLoadingOlder.collectAsState()
    val olderPageVersion by vm.olderPageVersion.collectAsState()
    val forwardDetailState by vm.forwardDetailState.collectAsState()
    val replySourceState by vm.replySourceState.collectAsState()
    val pttPlaybackStates by vm.pttPlaybackStates.collectAsState()
    val inlineKeyboardActions by vm.inlineKeyboardActions.collectAsState()
    val multiSelectMode by vm.multiSelectMode.collectAsState()
    val selectedMsgIds by vm.selectedMsgIds.collectAsState()
    val timelineItems = remember(messages) { messages.toTimelineItems() }
    val currentIsLoadingOlder by rememberUpdatedState(isLoadingOlder)
    val currentTimelineItems by rememberUpdatedState(timelineItems)
    val currentOlderPageVersion by rememberUpdatedState(olderPageVersion)
    val listState = rememberLazyListState()
    var composerActionsVisible by remember(peerUid, chatType) { mutableStateOf(true) }
    var initialPositioned by remember(peerUid, chatType) { mutableStateOf(false) }
    var previousLastMessageKey by remember(peerUid, chatType) { mutableStateOf<String?>(null) }
    var isHistoryRequestPending by remember(peerUid, chatType) { mutableStateOf(false) }
    val currentHistoryRequestPending by rememberUpdatedState(isHistoryRequestPending)
    var pendingHistoryAnchor by remember(peerUid, chatType) { mutableStateOf<HistoryAnchor?>(null) }
    var viewerMedia by remember(peerUid, chatType) { mutableStateOf<ViewerMedia?>(null) }
    var videoPlayer by remember(peerUid, chatType) { mutableStateOf<VideoPlayback?>(null) }
    var selectedActionMessage by remember(peerUid, chatType) { mutableStateOf<ChatDetailViewModel.UiMsg?>(null) }
    var selectedFile by remember(peerUid, chatType) { mutableStateOf<FileDetailTarget?>(null) }
    var pendingReplyNavigation by remember(peerUid, chatType) {
        mutableStateOf<ChatDetailViewModel.MessageContent.Reply?>(null)
    }
    var showMessageSearch by remember(peerUid, chatType) { mutableStateOf(false) }
    var messageSearchQuery by remember(peerUid, chatType) { mutableStateOf("") }
    val messageSearchMatches = remember(messages, messageSearchQuery) {
        val query = messageSearchQuery.trim()
        if (query.isBlank()) emptyList() else messages.filter { message ->
            message.text.contains(query, ignoreCase = true) ||
                message.senderNick.contains(query, ignoreCase = true)
        }.takeLast(20).asReversed()
    }
    val searchScope = rememberCoroutineScope()

    if (showMessageSearch) {
        ChatMessageSearchScreen(
            query = messageSearchQuery,
            matches = messageSearchMatches,
            onQueryChange = { messageSearchQuery = it },
            onSelect = { target ->
                showMessageSearch = false
                val targetIndex = timelineItems.indexOfFirst { item ->
                    (item as? ChatTimelineItem.Message)?.message?.stableKey == target.stableKey
                }
                if (targetIndex >= 0) {
                    searchScope.launch { listState.animateScrollToItem(targetIndex) }
                }
            },
            onBack = { showMessageSearch = false },
        )
        return
    }
    val context = LocalContext.current
    var pendingCallMode by remember(peerUid, chatType) { mutableStateOf<CallMode?>(null) }
    val startCall: (CallMode) -> Unit = { mode ->
        when (
            val result = QmceCallController.startOutgoing(
                context = context,
                mode = mode,
                peerUid = peerUid,
                peerUin = peerUin,
                peerName = name.ifEmpty { peerName },
            )
        ) {
            CallStartResult.Requested -> Unit
            is CallStartResult.Rejected -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            is CallStartResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
        }
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val mode = pendingCallMode ?: return@rememberLauncherForActivityResult
        pendingCallMode = null
        val requiredPermissions = QmceCallController.requiredPermissions(mode)
        if (requiredPermissions.all { permission ->
                grants[permission] == true ||
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            startCall(mode)
        } else {
            Toast.makeText(context, "需要麦克风${if (mode == CallMode.Video) "和相机" else ""}权限", Toast.LENGTH_SHORT)
                .show()
        }
    }
    val requestCall: (CallMode) -> Unit = { mode ->
        val missingPermissions = QmceCallController.requiredPermissions(mode).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            startCall(mode)
        } else {
            pendingCallMode = mode
            callPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    val lastMessageKey = messages.lastOrNull()?.stableKey

    pendingHistoryAnchor?.let { anchor ->
        val anchorIndex = timelineItems.indexOfFirst { item -> item.key == anchor.messageKey }
        if (olderPageVersion >= anchor.resultVersion) {
            val shouldRestorePosition =
                olderPageVersion == anchor.resultVersion &&
                    !listState.isScrollInProgress &&
                    anchorIndex >= 0
            SideEffect {
                pendingHistoryAnchor = null
                isHistoryRequestPending = false
                if (shouldRestorePosition) {
                    listState.requestScrollToItem(anchorIndex, -anchor.viewportOffset)
                }
            }
        }
    }

    LaunchedEffect(peerUid) {
        vm.openChat(runtime, peerUid, peerUin, chatType, peerName, myUin)
    }
    LaunchedEffect(peerUid, chatType, statusText, lastMessageKey, timelineItems.size) {
        if (timelineItems.isEmpty() || statusText.isNotEmpty()) return@LaunchedEffect
        val lastIndex = timelineItems.lastIndex
        if (!initialPositioned) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { itemCount -> itemCount > lastIndex }
            withFrameNanos { }
            listState.scrollToItem(lastIndex)
            withFrameNanos { }
            listState.scrollToItem(timelineItems.lastIndex)
            initialPositioned = true
        } else if (lastMessageKey != previousLastMessageKey) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (lastVisibleIndex >= lastIndex - 1) listState.animateScrollToItem(lastIndex)
        }
        previousLastMessageKey = lastMessageKey
    }

    LaunchedEffect(peerUid, chatType, initialPositioned) {
        var previousState: TimelineScrollState? = null
        var reachedTopFromUpwardScroll = false
        snapshotFlow {
            TimelineScrollState(
                isScrolling = listState.isScrollInProgress,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemOffset = listState.firstVisibleItemScrollOffset,
                canScrollBackward = listState.canScrollBackward,
            )
        }.collect { state ->
                val movedTowardTop = previousState?.let { previous ->
                    state.firstVisibleItemIndex < previous.firstVisibleItemIndex ||
                        (
                            state.firstVisibleItemIndex == previous.firstVisibleItemIndex &&
                                state.firstVisibleItemOffset < previous.firstVisibleItemOffset
                            )
                } == true
                if (state.canScrollBackward) {
                    reachedTopFromUpwardScroll = false
                } else if (
                    state.isScrolling &&
                        (movedTowardTop || previousState?.canScrollBackward == true)
                ) {
                    reachedTopFromUpwardScroll = true
                }
                if (
                    initialPositioned &&
                    !state.isScrolling &&
                    !state.canScrollBackward &&
                    !currentIsLoadingOlder &&
                    !currentHistoryRequestPending &&
                    reachedTopFromUpwardScroll
                ) {
                    val anchor = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { item ->
                            currentTimelineItems.getOrNull(item.index) is ChatTimelineItem.Message
                        }
                        ?.let { item ->
                            HistoryAnchor(
                                messageKey = currentTimelineItems[item.index].key,
                                viewportOffset = item.offset,
                                resultVersion = currentOlderPageVersion + 1,
                            )
                        }
                    isHistoryRequestPending = vm.loadOlderMessages()
                    pendingHistoryAnchor = anchor?.takeIf { isHistoryRequestPending }
                    reachedTopFromUpwardScroll = false
                }
                if (state.isScrolling && pendingHistoryAnchor != null) {
                    pendingHistoryAnchor = null
                }
                previousState = state
            }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            composerActionsVisible = false
        } else {
            delay(2_000)
            if (!listState.isScrollInProgress) composerActionsVisible = true
        }
    }

    LaunchedEffect(peerUid, chatType, olderPageVersion) {
        val anchor = pendingHistoryAnchor
        if (anchor == null || olderPageVersion >= anchor.resultVersion) {
            isHistoryRequestPending = false
        }
    }

    LaunchedEffect(messages, pendingReplyNavigation, replySourceState) {
        val reply = pendingReplyNavigation ?: return@LaunchedEffect
        val targetIndex = timelineItems.indexOfFirst { item ->
            val target = (item as? ChatTimelineItem.Message)?.message ?: return@indexOfFirst false
            target.msgId == reply.targetMessageId ||
                (reply.targetSequence != null && target.msgSeq == reply.targetSequence)
        }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
            pendingReplyNavigation = null
            vm.clearReplySourceState()
        } else if (replySourceState is ChatDetailViewModel.ReplySourceState.Error) {
            Toast.makeText(
                context,
                (replySourceState as ChatDetailViewModel.ReplySourceState.Error).message,
                Toast.LENGTH_SHORT,
            ).show()
            pendingReplyNavigation = null
            vm.clearReplySourceState()
        }
    }

    val showCallPage = chatType == 1 && peerUid.isNotBlank()
    val pagerState = androidx.wear.compose.foundation.pager.rememberPagerState(
        pageCount = { if (showCallPage) 3 else 2 },
    )
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader(
                onSearch = { showMessageSearch = true },
            )
            androidx.wear.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (statusText.isNotEmpty() && statusText !in transientChatStatuses) {
                                Text(
                                    text = statusText,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outline,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                            ScreenScaffold(
                                scrollState = listState,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(
                                    start = 10.dp,
                                    end = 10.dp,
                                    top = 6.dp,
                                    bottom = 52.dp,
                                ),
                            ) { contentPadding ->
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    state = listState,
                                    contentPadding = contentPadding,
                                ) {
                                items(timelineItems, key = { item -> item.key }) { item ->
                                    when (item) {
                                        is ChatTimelineItem.DateDivider -> ChatDateDivider(item.label)
                                        is ChatTimelineItem.Message -> {
                                            val isSelected = multiSelectMode && item.message.msgId in selectedMsgIds
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                MessageBubble(
                                                    message = item.message,
                                                    ensureImageCached = vm::ensureImageCached,
                                                    onOpenMedia = { viewerMedia = it },
                                                    onOpenVideo = { videoPlayer = it },
                                                    onOpenForward = vm::loadForwardDetail,
                                                    onLongClick = {
                                                        if (multiSelectMode) vm.toggleSelection(item.message.msgId)
                                                        else selectedActionMessage = item.message
                                                    },
                                                    onTap = if (multiSelectMode) {
                                                        { vm.toggleSelection(item.message.msgId) }
                                                    } else null,
                                                    onOpenReply = { reply ->
                                                        if (multiSelectMode) return@MessageBubble
                                                        val isLoaded = messages.any { candidate ->
                                                            candidate.msgId == reply.targetMessageId ||
                                                                (reply.targetSequence != null && candidate.msgSeq == reply.targetSequence)
                                                        }
                                                        pendingReplyNavigation = reply
                                                        if (!isLoaded) vm.loadReplySource(reply)
                                                    },
                                                    onOpenFile = { message, file ->
                                                        if (!multiSelectMode) selectedFile = FileDetailTarget(message, file)
                                                    },
                                                    inlineKeyboardActions = inlineKeyboardActions,
                                                    onClickInlineKeyboard = { message, keyboard, button ->
                                                        vm.clickInlineKeyboardButton(message, keyboard, button)
                                                    },
                                                    voicePlaybackState = { voice ->
                                                        pttPlaybackStates[voice.media.messageId]
                                                    },
                                                    onToggleVoice = vm::toggleVoicePlayback,
                                                )
                                                if (multiSelectMode && isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(if (item.message.isSelf) Alignment.CenterEnd else Alignment.CenterStart)
                                                            .padding(start = if (!item.message.isSelf) 2.dp else 0.dp, end = if (item.message.isSelf) 2.dp else 0.dp)
                                                            .size(18.dp)
                                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = "已选择",
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(13.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                }
                            }
                        }
                    }
                    1 -> if (showCallPage) {
                        CallPage(
                            peerName = name.ifEmpty { peerName },
                            onRequestCall = requestCall,
                            onOpenPacketTool = onOpenPacketTool,
                        )
                    } else {
                        ChatInfoScreen(
                            peerUid = peerUid,
                            peerUin = peerUin.toLongOrNull() ?: 0L,
                            chatType = chatType,
                            peerName = name.ifEmpty { peerName },
                            avatarPath = avatarPath,
                            avatarUrl = avatarUrl,
                            vm = vm,
                            onOpenMembers = onOpenMembers,
                            onOpenSettings = onOpenChatSettings,
                        )
                    }
                    2 -> ChatInfoScreen(
                        peerUid = peerUid,
                        peerUin = peerUin.toLongOrNull() ?: 0L,
                        chatType = chatType,
                        peerName = name.ifEmpty { peerName },
                        avatarPath = avatarPath,
                        avatarUrl = avatarUrl,
                        vm = vm,
                        onOpenMembers = onOpenMembers,
                        onOpenSettings = onOpenChatSettings,
                    )
                }
            }
        }
        if (multiSelectMode) {
            MultiSelectBottomBar(
                selectedCount = selectedMsgIds.size,
                onExit = { vm.exitMultiSelect() },
                onCopy = {
                    val text = vm.batchCopySelected()
                    if (text.isNotBlank()) copyMessageText(context, text)
                    else Toast.makeText(context, "没有可复制的文字", Toast.LENGTH_SHORT).show()
                },
                onForward = Toast.makeText(context, "批量转发开发中", Toast.LENGTH_SHORT).show().let { {} },
                onDelete = {
                    if (selectedMsgIds.isNotEmpty()) vm.batchDeleteSelected()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else if (pagerState.currentPage == 0) {
            AnimatedVisibility(
                visible = composerActionsVisible,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it },
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideOutVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { it },
            ) {
                ChatComposerActions(
                    onOpenInput = onOpenInput,
                    onOpenVoiceRecorder = onOpenVoiceRecorder,
                )
            }
        }
        if (forwardDetailState !is ChatDetailViewModel.ForwardDetailState.Idle) {
            ForwardDetailScreen(
                state = forwardDetailState,
                ensureImageCached = vm::ensureImageCached,
                onOpenMedia = { viewerMedia = it },
                onOpenVideo = { videoPlayer = it },
                onOpenForward = vm::loadForwardDetail,
                onLongClick = { selectedActionMessage = it },
                onOpenFile = { message, file -> selectedFile = FileDetailTarget(message, file) },
                onDismiss = vm::dismissForwardDetail,
            )
        }
        viewerMedia?.let { media ->
            FullscreenMediaViewer(media = media, onDismiss = { viewerMedia = null })
        }
        videoPlayer?.let { playback ->
            LocalVideoPlayerScreen(
                file = playback.file,
                title = playback.title,
                onDismiss = { videoPlayer = null },
            )
        }
        selectedFile?.let { target ->
            val currentMessage = messages.firstOrNull { it.stableKey == target.message.stableKey } ?: target.message
            val currentFile = currentMessage.contents
                .filterIsInstance<ChatDetailViewModel.MessageContent.File>()
                .firstOrNull { it.elementId == target.content.elementId }
                ?: target.content
            FileDetailScreen(
                message = currentMessage,
                content = currentFile,
                onOpenLocalFile = { file -> openLocalFile(context, file) },
                onDownloadFile = { vm.requestFileDownload(currentMessage, currentFile) },
                onDismiss = { selectedFile = null },
            )
        }
        selectedActionMessage?.let { message ->
            val actionContext = remember(message, messages) {
                val lastMsg = messages.lastOrNull()
                val isLast = lastMsg?.stableKey == message.stableKey
                val prevMsg = if (messages.size >= 2) {
                    val idx = messages.indexOfLast { it.stableKey == message.stableKey }
                    if (idx > 0) messages[idx - 1] else null
                } else null
                MessageActionContext(isLastMessage = isLast, previousMessage = prevMsg)
            }
            MessageActionsScreen(
                message = message,
                context = actionContext,
                onBack = { selectedActionMessage = null },
                onAction = { action ->
                    selectedActionMessage = null
                    when (action.id) {
                        "copy" -> copyMessageText(context, MessageActionResolver.copyableText(message))
                        "view_media" -> message.firstLocalMediaFile()?.let { file ->
                            viewerMedia = ViewerMedia("${message.stableKey}:action", file, "图片")
                        } ?: Toast.makeText(context, "图片尚未缓存", Toast.LENGTH_SHORT).show()
                        "share_media" -> message.firstLocalMediaFile()?.let { file ->
                            shareLocalMedia(context, file)
                        }
                        "forward_detail" -> message.contents
                            .filterIsInstance<ChatDetailViewModel.MessageContent.Forward>()
                            .firstOrNull()
                            ?.let(vm::loadForwardDetail)
                        "recall" -> vm.recallMessage(message.msgId)
                        "delete" -> vm.deleteMessage(message.msgId)
                        "repeat" -> {
                            val text = MessageActionResolver.copyableText(message)
                            if (text.isNotBlank()) vm.sendText(text)
                        }
                        "forward" -> {
                            vm.setPendingForward(message)
                            onOpenContactPicker()
                        }
                        "multi_select" -> vm.enterMultiSelect(message.msgId)
                        "edit" -> {
                            val text = MessageActionResolver.copyableText(message)
                            if (text.isNotBlank()) {
                                vm.beginEdit(message.msgId, text)
                                onOpenInput()
                            }
                        }
                    }
                },
            )
        }
    }
}

private val transientChatStatuses = setOf("正在等待消息服务...", "加载中...")

private fun List<ChatDetailViewModel.UiMsg>.toTimelineItems(): List<ChatTimelineItem> = buildList {
    var previousDayKey: String? = null
    this@toTimelineItems.forEach { message ->
        val dayKey = message.time.toDayKey()
        if (dayKey != previousDayKey) {
            add(ChatTimelineItem.DateDivider(formatChatDate(message.time), "date:$dayKey"))
            previousDayKey = dayKey
        }
        add(ChatTimelineItem.Message(message))
    }
}

@Composable
private fun ChatHeader(onSearch: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 没做完 藏起来
        /*
        IconButton(
            onClick = onSearch,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .touchTargetAwareSize(androidx.wear.compose.material3.IconButtonDefaults.SmallButtonSize),
        ) {
            Icon(Icons.Default.Search, contentDescription = "搜索消息", modifier = Modifier.size(16.dp))
        }
         */
    }
}

@Composable
private fun CallPage(
    peerName: String,
    onRequestCall: (CallMode) -> Unit,
    onOpenPacketTool: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("发起通话", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        // Text(peerName, color = Color(0xFFD1CBD7), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onRequestCall(CallMode.Voice) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text("语音通话")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onRequestCall(CallMode.Video) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("视频通话")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onOpenPacketTool,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text("发包工具")
        }
    }
}

@Composable
private fun ChatComposerActions(
    onOpenInput: () -> Unit,
    onOpenVoiceRecorder: () -> Unit,
) {
    CurvedCard(
        modifier = Modifier.fillMaxSize(),
    ) {
        curvedRow {
            curvedCompactButton(
                onClick = {},
                icon = { Icon(Icons.Default.EmojiEmotions, contentDescription = "表情") },
            )
            curvedCompactButton(
                onClick = onOpenInput,
                icon = { Icon(Icons.Default.TextFields, contentDescription = "文本") },
            )
            curvedCompactButton(
                onClick = onOpenVoiceRecorder,
                icon = { Icon(Icons.Default.Mic, contentDescription = "语音") },
            )
        }
    }
}

@Composable
internal fun MessageBubble(
    message: ChatDetailViewModel.UiMsg,
    ensureImageCached: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.Image) -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    onOpenVideo: (VideoPlayback) -> Unit,
    onOpenForward: (ChatDetailViewModel.MessageContent.Forward) -> Unit,
    onLongClick: (ChatDetailViewModel.UiMsg) -> Unit,
    onTap: (() -> Unit)? = null,
    onOpenReply: (ChatDetailViewModel.MessageContent.Reply) -> Unit,
    onOpenFile: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.File) -> Unit,
    inlineKeyboardActions: Map<String, ChatDetailViewModel.InlineKeyboardActionState> = emptyMap(),
    onClickInlineKeyboard: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.InlineKeyboard, ChatDetailViewModel.MessageContent.InlineKeyboardButton) -> Unit = { _, _, _ -> },
    voicePlaybackState: (ChatDetailViewModel.MessageContent.Voice) -> PttPlaybackState? = { null },
    onToggleVoice: (ChatDetailViewModel.MessageContent.Voice) -> Unit = {},
) {
    val systemTip = message.contents.singleOrNull() as? ChatDetailViewModel.MessageContent.SystemTip
    if (systemTip != null) {
        SystemTipLine(systemTip.text)
        return
    }
    val containerColor = if (message.isSelf) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val messageContentColor = if (message.isSelf) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (message.isSelf) Arrangement.End else Arrangement.Start
    val bubbleShape = RoundedCornerShape(10.dp)
    val isSingleMedia = message.contents.singleOrNull().let {
        it is ChatDetailViewModel.MessageContent.Image ||
            it is ChatDetailViewModel.MessageContent.MarketFace ||
            it is ChatDetailViewModel.MessageContent.Giphy
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = alignment) {
        Column(
            modifier = Modifier.widthIn(max = 172.dp),
            horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start,
        ) {
            if (!message.isSelf && message.senderNick.isNotBlank()) {
                Text(
                    text = message.senderNick,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    maxLines = 1,
                )
            }
            MessageCard(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .height(IntrinsicSize.Max)
                    .widthIn(max = 172.dp),
                containerColor = containerColor,
                contentColor = messageContentColor,
                shape = bubbleShape,
                contentPadding = if (isSingleMedia) PaddingValues(0.dp) else PaddingValues(horizontal = 11.dp, vertical = 7.dp),
                onClick = { onTap?.invoke() },
                onLongClick = { onLongClick(message) },
            ) {
                message.contents.forEachIndexed { index, content ->
                    MessageContentItem(
                        message = message,
                        contentIndex = index,
                        content = content,
                        ensureImageCached = ensureImageCached,
                        onOpenMedia = onOpenMedia,
                        onOpenVideo = onOpenVideo,
                        onOpenForward = onOpenForward,
                        onLongClick = { onLongClick(message) },
                        onOpenReply = onOpenReply,
                        onOpenFile = onOpenFile,
                        inlineKeyboardActions = inlineKeyboardActions,
                        onClickInlineKeyboard = { keyboard, button ->
                            onClickInlineKeyboard(message, keyboard, button)
                        },
                        voicePlaybackState = voicePlaybackState,
                        onToggleVoice = onToggleVoice,
                    )
                }
            }
            Text(
                text = formatMsgTime(message.time),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun MessageContentItem(
    message: ChatDetailViewModel.UiMsg,
    contentIndex: Int,
    content: ChatDetailViewModel.MessageContent,
    ensureImageCached: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.Image) -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    onOpenVideo: (VideoPlayback) -> Unit,
    onOpenForward: (ChatDetailViewModel.MessageContent.Forward) -> Unit,
    onLongClick: () -> Unit,
    onOpenReply: (ChatDetailViewModel.MessageContent.Reply) -> Unit,
    onOpenFile: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.File) -> Unit,
    inlineKeyboardActions: Map<String, ChatDetailViewModel.InlineKeyboardActionState>,
    onClickInlineKeyboard: (ChatDetailViewModel.MessageContent.InlineKeyboard, ChatDetailViewModel.MessageContent.InlineKeyboardButton) -> Unit,
    voicePlaybackState: (ChatDetailViewModel.MessageContent.Voice) -> PttPlaybackState?,
    onToggleVoice: (ChatDetailViewModel.MessageContent.Voice) -> Unit,
) {
    when (content) {
        is ChatDetailViewModel.MessageContent.Text -> {
            RichMessageText(content.value, onLongClick)
        }
        is ChatDetailViewModel.MessageContent.Image -> LocalMessageImage(
            content = content,
            ensureCached = { ensureImageCached(message, content) },
            onOpen = { file ->
                onOpenMedia(ViewerMedia("${message.stableKey}:$contentIndex", file, "图片"))
            },
        )
        is ChatDetailViewModel.MessageContent.Face -> {
            RichMessageText(content.text, onLongClick)
        }
        is ChatDetailViewModel.MessageContent.MarketFace -> {
            LocalMarketFace(content) { file ->
                onOpenMedia(ViewerMedia("${message.stableKey}:$contentIndex", file, content.name))
            }
        }
        is ChatDetailViewModel.MessageContent.Giphy -> GiphyMessageContent(content) { mediaUrl ->
            onOpenMedia(ViewerMedia("${message.stableKey}:$contentIndex", mediaUrl, "GIF"))
        }
        is ChatDetailViewModel.MessageContent.Voice -> VoiceMessageContent(
            content = content,
            playbackState = voicePlaybackState(content),
            onTogglePlayback = { onToggleVoice(content) },
        )
        is ChatDetailViewModel.MessageContent.Video -> VideoMessageContent(content, onOpenVideo)
        is ChatDetailViewModel.MessageContent.File -> FileMessageContent(content) { onOpenFile(message, content) }
        is ChatDetailViewModel.MessageContent.Reply -> ReplyMessageContent(content, onOpenReply)
        is ChatDetailViewModel.MessageContent.Card -> StructuredCardContent(
            title = content.title,
            description = content.description,
            tag = content.tag,
            previewUrl = content.previewUrl,
        )
        is ChatDetailViewModel.MessageContent.StructCard -> StructuredCardContent(
            title = content.title,
            description = listOfNotNull(
                content.description.takeIf { it.isNotBlank() },
                content.groupCode?.let { "群号 $it" },
            ).joinToString("\n"),
            tag = "群邀请",
            previewUrl = null,
        )
        is ChatDetailViewModel.MessageContent.Forward -> ForwardMessageContent(content, onOpenForward)
        is ChatDetailViewModel.MessageContent.SystemTip -> SystemTipLine(content.text)
        is ChatDetailViewModel.MessageContent.Location -> LocationMessageContent(content)
        is ChatDetailViewModel.MessageContent.Wallet -> WalletMessageContent(content)
        is ChatDetailViewModel.MessageContent.Calendar -> CalendarMessageContent(content)
        is ChatDetailViewModel.MessageContent.InlineKeyboard -> InlineKeyboardMessageContent(
            message = message,
            content = content,
            actionStates = inlineKeyboardActions,
            onClick = onClickInlineKeyboard,
        )
        is ChatDetailViewModel.MessageContent.Markdown -> MarkdownMessageContent(content.value, onLongClick)
        is ChatDetailViewModel.MessageContent.CallRecord -> CallRecordMessageContent(content)
        is ChatDetailViewModel.MessageContent.Unsupported -> MessageFallback(
            content.detail?.let { "[$it · 类型 ${content.elementType}]" } ?: "[暂不支持的消息 · 类型 ${content.elementType}]",
        )
    }
}

@Composable
private fun LocalMessageImage(
    content: ChatDetailViewModel.MessageContent.Image,
    ensureCached: () -> Unit,
    onOpen: (File) -> Unit,
) {
    val localFile = remember(content.localPaths, content.thumbnailPaths, content.sourcePath) {
        (content.localPaths + content.thumbnailPaths + listOfNotNull(content.sourcePath)).asSequence()
            .filterNotNull()
            .map { File(it.removePrefix("file://")) }
            .firstOrNull(File::isFile)
    }
    val size = mediaSize(content.width, content.height)
    if (localFile == null) {
        LaunchedEffect(content.elementId) { ensureCached() }
        MediaPlaceholder(
            size = size,
            label = if (content.isLoading) "图片加载中" else "点击重试",
            onClick = ensureCached,
        )
    } else {
        Card(
            onClick = { onOpen(localFile) },
            modifier = Modifier.size(size.width, size.height),
            colors = androidx.wear.compose.material3.CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            AsyncImage(
                model = localFile,
                contentDescription = "图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun LocalMarketFace(content: ChatDetailViewModel.MessageContent.MarketFace, onOpen: (File) -> Unit) {
    val localFile = remember(content.staticPath, content.dynamicPath) {
        sequenceOf(content.dynamicPath, content.staticPath)
            .filterNotNull()
            .map { File(it.removePrefix("file://")) }
            .firstOrNull(File::isFile)
    }
    val size = mediaSize(content.width, content.height)
    if (localFile == null) {
        MessageFallback(content.name)
    } else {
        Card(
            onClick = { onOpen(localFile) },
            modifier = Modifier.size(size.width, size.height),
            colors = androidx.wear.compose.material3.CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            AsyncImage(
                model = localFile,
                contentDescription = content.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun GiphyMessageContent(
    content: ChatDetailViewModel.MessageContent.Giphy,
    onOpen: (String) -> Unit,
) {
    val mediaUrl = content.mediaUrl
    var failed by remember(mediaUrl) { mutableStateOf(false) }
    val size = mediaSize(content.width, content.height)
    if (mediaUrl == null || failed) {
        MediaPlaceholder(size, if (mediaUrl == null) "GIF不可用" else "GIF加载失败")
    } else {
        Card(
            onClick = { onOpen(mediaUrl) },
            modifier = Modifier.size(size.width, size.height),
            colors = androidx.wear.compose.material3.CardDefaults.cardColors(
                containerColor = Color.Transparent,
            ),
            contentPadding = PaddingValues(0.dp),
        ) {
            AsyncImage(
                model = mediaUrl,
                contentDescription = "GIF 动图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onError = { failed = true },
            )
        }
    }
}

@Composable
private fun MessageFallback(label: String) {
    Text(label, color = LocalContentColor.current, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun MarkdownMessageContent(value: String, onLongClick: () -> Unit) {
    val lines: List<Pair<Boolean, String>> = remember(value) {
        value.replace("\r\n", "\n")
            .split('\n')
            .map { line ->
                val code = line.trimStart().startsWith("```")
                val rendered = line
                    .replace(Regex("^\\s{0,3}#{1,6}\\s+"), "")
                    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                    .replace(Regex("__(.+?)__"), "$1")
                    .replace(Regex("`([^`]+)`"), "$1")
                    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "$1")
                    .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1 ($2)")
                code to rendered
            }
            .filterNot { (code, text) -> code && text.trimStart().startsWith("```") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { (code, text) ->
            if (code) {
                Text(
                    text = text,
                    color = LocalContentColor.current,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(5.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )
            } else if (text.isNotBlank()) {
                RichMessageText(text, onLongClick)
            }
        }
    }
}

@Composable
private fun CallRecordMessageContent(content: ChatDetailViewModel.MessageContent.CallRecord) {
    val isVideo = content.type == 2 || content.text.contains("视频")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
            contentDescription = if (isVideo) "视频通话" else "语音通话",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content.text,
                color = LocalContentColor.current,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (content.hasRead) "通话记录" else "未读通话记录",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RichMessageText(value: String, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(value, linkColor) { value.toLinkedAnnotatedString(linkColor) }
    var layoutResult by remember(value) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        modifier = Modifier.pointerInput(annotated) {
            detectTapGestures(
                onLongPress = { onLongClick() },
                onTap = { offset ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val characterOffset = layout.getOffsetForPosition(offset)
                    annotated.getStringAnnotations(
                        tag = URL_ANNOTATION_TAG,
                        start = characterOffset,
                        end = characterOffset,
                    ).firstOrNull()?.item?.let { url ->
                        openHttpLink(context, url)
                    }
                },
            )
        },
        color = LocalContentColor.current,
        style = MaterialTheme.typography.bodyLarge,
        onTextLayout = { layoutResult = it },
    )
}

@Composable
private fun ReplyMessageContent(
    content: ChatDetailViewModel.MessageContent.Reply,
    onOpenReply: (ChatDetailViewModel.MessageContent.Reply) -> Unit,
) {
    Card(
        onClick = { onOpenReply(content) },
        enabled = !content.expired,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = content.senderName,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = if (content.expired) "原消息已失效" else content.summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FileMessageContent(
    content: ChatDetailViewModel.MessageContent.File,
    onOpenFile: () -> Unit,
) {
    val context = LocalContext.current
    val localFile = remember(content.path) {
        content.path
            ?.removePrefix("file://")
            ?.let(::File)
            ?.takeIf(File::isFile)
    }
    Card(
        onClick = onOpenFile,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(content.name, color = LocalContentColor.current, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = buildList {
                    add(Formatter.formatShortFileSize(context, content.sizeBytes))
                    content.progress?.let { add("$it%") }
                }.joinToString(" · "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
            Text(
                text = when {
                    localFile != null -> "已缓存"
                    else -> "点击查看"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        }
    }
}

@Composable
private fun VoiceMessageContent(
    content: ChatDetailViewModel.MessageContent.Voice,
    playbackState: PttPlaybackState?,
    onTogglePlayback: () -> Unit,
) {
    val phase = playbackState?.phase ?: PttPlaybackPhase.Idle
    val durationSeconds = content.media.durationSeconds
    val positionSeconds = (playbackState?.positionMillis ?: 0) / 1_000
    val status = when (phase) {
        PttPlaybackPhase.Preparing -> "正在准备..."
        PttPlaybackPhase.Playing -> "${formatMediaDuration(positionSeconds)} / ${formatMediaDuration(durationSeconds)}"
        PttPlaybackPhase.Paused -> "${formatMediaDuration(positionSeconds)} / ${formatMediaDuration(durationSeconds)}"
        PttPlaybackPhase.Failed -> playbackState?.error ?: "无法播放此语音"
        PttPlaybackPhase.Idle -> content.progress?.let { "传输中 $it%" } ?: "点击播放"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.wear.compose.material3.FilledTonalIconButton(
                onClick = onTogglePlayback,
                enabled = phase != PttPlaybackPhase.Preparing,
                modifier = Modifier.touchTargetAwareSize(androidx.wear.compose.material3.IconButtonDefaults.SmallButtonSize),
                colors = androidx.wear.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = if (phase == PttPlaybackPhase.Playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (phase == PttPlaybackPhase.Playing) "暂停" else "播放",
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDuration(durationSeconds),
                    color = LocalContentColor.current,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                content.transcript?.let { transcript ->
                    Text(transcript, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "语音",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            text = status,
            color = if (phase == PttPlaybackPhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun VideoMessageContent(
    content: ChatDetailViewModel.MessageContent.Video,
    onOpenVideo: (VideoPlayback) -> Unit,
) {
    val localFile = remember(content.filePath) {
        content.filePath
            ?.removePrefix("file://")
            ?.let(::File)
            ?.takeIf(File::isFile)
    }
    val thumbnail = remember(content.thumbnailPaths) {
        content.thumbnailPaths.asSequence().map { File(it.removePrefix("file://")) }.firstOrNull(File::isFile)
    }
    Card(
        onClick = { localFile?.let { onOpenVideo(VideoPlayback(it, "视频")) } },
        enabled = localFile != null,
        modifier = Modifier.fillMaxWidth().height(132.dp),
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(9.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
        if (thumbnail != null) {
            AsyncImage(
                model = thumbnail,
                contentDescription = "视频封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "视频",
            tint = LocalContentColor.current,
            modifier = Modifier.size(34.dp),
        )
        Text(
            text = buildList {
                add(formatDuration(content.durationSeconds))
                content.progress?.let { add("$it%") }
            }.joinToString(" · "),
            modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
            color = LocalContentColor.current,
            style = MaterialTheme.typography.bodySmall,
        )
        if (localFile == null) {
            Text(
                text = "视频未缓存",
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                color = LocalContentColor.current,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        }
    }
}

@Composable
private fun StructuredCardContent(
    title: String,
    description: String,
    tag: String?,
    previewUrl: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(9.dp),
    ) {
        tag?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
        }
        Text(title, color = LocalContentColor.current, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        description.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(3.dp))
        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        previewUrl?.let { url ->
            Spacer(Modifier.height(7.dp))
            AsyncImage(
                model = url,
                contentDescription = "卡片预览",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(7.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ForwardMessageContent(
    content: ChatDetailViewModel.MessageContent.Forward,
    onOpenForward: (ChatDetailViewModel.MessageContent.Forward) -> Unit,
) {
    Card(
        onClick = { onOpenForward(content) },
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(9.dp),
    ) {
        Text(content.title, color = LocalContentColor.current, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
        content.preview.forEach { line ->
            Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private const val URL_ANNOTATION_TAG = "url"
private val httpUrlRegex = Regex("https?://[^\\s<>\\\"]+")

private fun String.toLinkedAnnotatedString(linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    httpUrlRegex.findAll(this@toLinkedAnnotatedString).forEach { match ->
        append(this@toLinkedAnnotatedString.substring(cursor, match.range.first))
        pushStringAnnotation(URL_ANNOTATION_TAG, match.value)
        withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
            append(match.value)
        }
        pop()
        cursor = match.range.last + 1
    }
    append(this@toLinkedAnnotatedString.substring(cursor))
}

@Composable
private fun LocationMessageContent(content: ChatDetailViewModel.MessageContent.Location) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(content.title, color = LocalContentColor.current, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            content.detail.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WalletMessageContent(content: ChatDetailViewModel.MessageContent.Wallet) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            content.iconUrl?.let { iconUrl ->
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(8.dp))
            } ?: Text(
                text = "¥",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = content.title,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = content.description,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content.notice?.let { notice ->
            Text(
                text = notice,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CalendarMessageContent(content: ChatDetailViewModel.MessageContent.Calendar) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "日程",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = content.title,
                color = LocalContentColor.current,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            content.description.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (content.expired) {
                Text(
                    text = "已过期",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun InlineKeyboardMessageContent(
    message: ChatDetailViewModel.UiMsg,
    content: ChatDetailViewModel.MessageContent.InlineKeyboard,
    actionStates: Map<String, ChatDetailViewModel.InlineKeyboardActionState>,
    onClick: (ChatDetailViewModel.MessageContent.InlineKeyboard, ChatDetailViewModel.MessageContent.InlineKeyboardButton) -> Unit,
) {
    if (content.rows.isEmpty()) {
        MessageFallback("[机器人消息]")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("机器人操作", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { button ->
                    val actionKey = "${message.stableKey}:keyboard:${button.stableKey}"
                    val action = actionStates[actionKey]
                    val isPending = action?.phase == ChatDetailViewModel.InlineKeyboardActionPhase.Pending
                    val label = action?.label ?: button.label
                    Button(
                        onClick = { onClick(content, button) },
                        enabled = !isPending,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun MultiSelectBottomBar(
    selectedCount: Int,
    onExit: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit, modifier = Modifier.touchTargetAwareSize(androidx.wear.compose.material3.IconButtonDefaults.ExtraSmallButtonSize)) {
            Icon(Icons.Default.Close, contentDescription = "退出多选")
        }
        Text(
            text = "已选 $selectedCount 条",
            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
        )
        IconButton(onClick = onCopy, modifier = Modifier.touchTargetAwareSize(androidx.wear.compose.material3.IconButtonDefaults.ExtraSmallButtonSize)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
        }
        IconButton(onClick = onDelete, modifier = Modifier.touchTargetAwareSize(androidx.wear.compose.material3.IconButtonDefaults.ExtraSmallButtonSize)) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SystemTipLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MessageCard(
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    shape: RoundedCornerShape,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "消息操作",
        modifier = modifier,
        shape = shape,
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        contentPadding = contentPadding,
        minHeight = 0.dp,
        content = content,
    )
}

@Composable
private fun MediaPlaceholder(size: DpSize, label: String, onClick: (() -> Unit)? = null) {
    val modifier = Modifier.size(size.width, size.height)
    if (onClick == null) {
        Card(
            modifier = modifier,
            colors = androidx.wear.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = androidx.wear.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
    }
}

private fun mediaSize(width: Int, height: Int): DpSize {
    val maxSize = 168.dp
    val fallbackSize = 80.dp
    if (width <= 0 || height <= 0) return DpSize(fallbackSize, fallbackSize)
    val ratio = width.toFloat() / height
    return if (ratio >= 1f) {
        DpSize(maxSize, (maxSize / ratio).coerceAtLeast(48.dp))
    } else {
        DpSize((maxSize * ratio).coerceAtLeast(48.dp), maxSize)
    }
}

private fun formatDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceAtLeast(0)
    return "%d:%02d".format(Locale.ROOT, safeSeconds / 60, safeSeconds % 60)
}

@Composable
private fun ChatDateDivider(date: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

private fun Long.toDayKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(this * 1000))

private fun formatChatDate(time: Long): String =
    SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(time * 1000))

private fun formatMsgTime(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time * 1000))
