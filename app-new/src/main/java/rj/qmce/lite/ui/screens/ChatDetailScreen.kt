@file:OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)

package rj.qmce.lite.ui.screens

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.text.format.Formatter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.touchTargetAwareSize
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mqq.app.AppRuntime
import rj.qmce.lite.data.call.CallMode
import rj.qmce.lite.data.call.CallStartResult
import rj.qmce.lite.data.call.QmceCallController
import rj.qmce.lite.data.chat.LinkPreviewRepository
import rj.qmce.lite.data.chat.LinkPreviewState
import rj.qmce.lite.data.chat.LocalMediaResolver
import rj.qmce.lite.data.chat.MessageNavigationSnapshot
import rj.qmce.lite.data.chat.PttPlaybackPhase
import rj.qmce.lite.data.chat.PttPlaybackState
import rj.qmce.lite.data.chat.PttTranslationPhase
import rj.qmce.lite.data.chat.PttTranslationState
import rj.qmce.lite.data.emotion.EmotionRepository
import rj.qmce.lite.data.media.MediaStoreSaver
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox
import rj.qmce.lite.ui.theme.LocalQmceAdaptive
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
private val MessageBubbleCornerRadius = 16.dp
private val MessageBubbleLeadingCornerRadius = 6.dp
private val MessageBubbleMaxWidth = 186.dp
private const val ConsecutiveMessageWindowMillis = 5 * 60 * 1000L

private fun messageBubbleShape(isSelf: Boolean) = RoundedCornerShape(
    topStart = if (isSelf) MessageBubbleCornerRadius else MessageBubbleLeadingCornerRadius,
    topEnd = if (isSelf) MessageBubbleLeadingCornerRadius else MessageBubbleCornerRadius,
    bottomEnd = MessageBubbleCornerRadius,
    bottomStart = MessageBubbleCornerRadius,
)

private sealed interface ChatTimelineItem {
    val key: String

    data class DateDivider(
        val label: String,
        override val key: String,
    ) : ChatTimelineItem

    data class TimeDivider(
        val label: String,
        override val key: String,
    ) : ChatTimelineItem

    data class Message(
        val message: ChatDetailViewModel.UiMsg,
        val isContinuation: Boolean,
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
    val firstVisibleMessageSequence: Long?,
    val atTop: Boolean,
    val atBottom: Boolean,
)

private data class FileDetailTarget(
    val message: ChatDetailViewModel.UiMsg,
    val content: ChatDetailViewModel.MessageContent.File,
)

internal data class VideoPlayback(
    val file: File?,
    val title: String,
    val messageKey: String? = null,
    val elementId: Long = 0L,
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
    onOpenComposerMenu: () -> Unit = {},
    onOpenSingleEmotion: () -> Unit = {},
    onOpenVoiceRecorder: () -> Unit = {},
    onOpenContactPicker: () -> Unit = {},
    onOpenPacketTool: () -> Unit = {},
    onOpenMembers: () -> Unit = {},
    onOpenGroupInfo: () -> Unit = {},
    onOpenChatSettings: () -> Unit = {},
    onReportingPageChanged: (String?) -> Unit = {},
    avatarPath: String = "",
    avatarUrl: String = "",
    messageNavigation: MessageNavigationSnapshot = MessageNavigationSnapshot(),
    vm: ChatDetailViewModel = viewModel(),
) {
    val messages by vm.messages.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val name by vm.peerName.collectAsState()
    val isLoadingOlder by vm.isLoadingOlder.collectAsState()
    val hasOlderMessages by vm.hasOlderMessages.collectAsState()
    val olderPageVersion by vm.olderPageVersion.collectAsState()
    val forwardDetailState by vm.forwardDetailState.collectAsState()
    val replySourceState by vm.replySourceState.collectAsState()
    val messageNavigationState by vm.messageNavigationState.collectAsState()
    val pttPlaybackStates by vm.pttPlaybackStates.collectAsState()
    val pttTranslationStates by vm.pttTranslationStates.collectAsState()
    val groupMemberLevels by vm.groupMemberLevels.collectAsState()
    val groupMemberTitles by vm.groupMemberTitles.collectAsState()
    val inlineKeyboardActions by vm.inlineKeyboardActions.collectAsState()
    val multiSelectMode by vm.multiSelectMode.collectAsState()
    val selectedMsgIds by vm.selectedMsgIds.collectAsState()
    val messageSummaryState by vm.messageSummaryState.collectAsState()
    val timelineItems = remember(messages) { messages.toTimelineItems() }
    val currentTimelineItems = rememberUpdatedState(timelineItems)
    LaunchedEffect(chatType, peerUin, messages) {
        if (chatType == 2) {
            vm.loadGroupMemberLevels(
                peerUin.toLongOrNull() ?: return@LaunchedEffect,
                messages.asSequence()
                    .filterNot { it.isSelf }
                    .map { it.senderUid }
                    .filter(String::isNotBlank)
                    .distinct()
                    .toList(),
            )
        }
    }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    var showMultiSelectActions by remember(peerUid, chatType) { mutableStateOf(false) }
    LaunchedEffect(multiSelectMode) {
        if (!multiSelectMode) showMultiSelectActions = false
    }
    var initialPositioned by remember(peerUid, chatType) { mutableStateOf(false) }
    var previousLastMessageKey by remember(peerUid, chatType) { mutableStateOf<String?>(null) }
    var followNewMessages by remember(peerUid, chatType) { mutableStateOf(true) }
    var atBottom by remember(peerUid, chatType) { mutableStateOf(true) }
    var showJumpBottom by remember(peerUid, chatType) { mutableStateOf(false) }
    val listScrollScope = rememberCoroutineScope()
    var isHistoryRequestPending by remember(peerUid, chatType) { mutableStateOf(false) }
    var pendingHistoryAnchor by remember(peerUid, chatType) { mutableStateOf<HistoryAnchor?>(null) }
    var viewerMedia by remember(peerUid, chatType) { mutableStateOf<ViewerMedia?>(null) }
    var videoPlayer by remember(peerUid, chatType) { mutableStateOf<VideoPlayback?>(null) }
    var selectedActionMessage by remember(
        peerUid,
        chatType
    ) { mutableStateOf<ChatDetailViewModel.UiMsg?>(null) }
    var selectedFile by remember(peerUid, chatType) { mutableStateOf<FileDetailTarget?>(null) }
    var selectedTextContent by remember(peerUid, chatType) { mutableStateOf<String?>(null) }
    var pendingCallRecordMode by remember(peerUid, chatType) { mutableStateOf<CallMode?>(null) }
    var pendingReplyNavigation by remember(peerUid, chatType) {
        mutableStateOf<ChatDetailViewModel.MessageContent.Reply?>(null)
    }
    var showMessageSearch by remember(peerUid, chatType) { mutableStateOf(false) }
    var messageSearchQuery by remember(peerUid, chatType) { mutableStateOf("") }
    var highlightedMessageKey by remember(peerUid, chatType) { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        onDispose { onReportingPageChanged(null) }
    }
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
            isLoadingOlder = isLoadingOlder,
            canLoadOlder = hasOlderMessages,
            onQueryChange = { messageSearchQuery = it },
            onLoadOlder = { vm.loadOlderMessages() },
            onSelect = { target ->
                showMessageSearch = false
                highlightedMessageKey = target.stableKey
                val targetIndex = timelineItems.indexOfFirst { item ->
                    (item as? ChatTimelineItem.Message)?.message?.stableKey == target.stableKey
                }
                if (targetIndex >= 0) {
                    searchScope.launch {
                        listState.animateScrollToItem(targetIndex)
                        delay(1_500)
                        if (highlightedMessageKey == target.stableKey) highlightedMessageKey = null
                    }
                }
            },
            onBack = { showMessageSearch = false },
        )
        return
    }
    val context = LocalContext.current
    val mediaSaveScope = rememberCoroutineScope()
    val mediaStoreSaver = remember { MediaStoreSaver() }
    var mediaSaveLabel by remember(peerUid, chatType) { mutableStateOf("保存") }
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
            is CallStartResult.Rejected -> Toast.makeText(
                context,
                result.message,
                Toast.LENGTH_SHORT
            ).show()

            is CallStartResult.Failed -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT)
                .show()
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
                        ContextCompat.checkSelfPermission(
                            context,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            startCall(mode)
        } else {
            Toast.makeText(
                context,
                "需要麦克风${if (mode == CallMode.Video) "和相机" else ""}权限",
                Toast.LENGTH_SHORT
            )
                .show()
        }
    }
    val requestCall: (CallMode) -> Unit = { mode ->
        val missingPermissions = QmceCallController.requiredPermissions(mode).filter { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            startCall(mode)
        } else {
            pendingCallMode = mode
            callPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    val lastMessageKey = messages.lastOrNull()?.stableKey
    val requestOlderAtTop: () -> Unit = {
        val atTop = !listState.canScrollBackward
        if (
            initialPositioned &&
            atTop &&
            hasOlderMessages &&
            !isLoadingOlder &&
            !isHistoryRequestPending
        ) {
            val visibleItems = listState.layoutInfo.visibleItems
            val anchorItem = visibleItems.firstOrNull { item ->
                timelineItems.getOrNull(item.index) is ChatTimelineItem.Message
            } ?: visibleItems.firstOrNull()
            val anchor = anchorItem?.let { item ->
                timelineItems.getOrNull(item.index)?.let { timelineItem ->
                    HistoryAnchor(
                        messageKey = timelineItem.key,
                        viewportOffset = item.offset,
                        resultVersion = olderPageVersion + 1,
                    )
                }
            }
            val requested = vm.loadOlderMessages()
            isHistoryRequestPending = requested
            pendingHistoryAnchor = anchor?.takeIf { requested }
        }
    }
    val currentRequestOlderAtTop = rememberUpdatedState(requestOlderAtTop)
    val topHistoryNestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    !listState.canScrollBackward
                ) {
                    currentRequestOlderAtTop.value()
                }
                return Offset.Zero
            }
        }
    }

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
                    listState.requestScrollToItem(anchorIndex, anchor.viewportOffset)
                }
            }
        }
    }

    LaunchedEffect(peerUid, chatType) {
        vm.openChat(
            runtime = runtime,
            peerUid = peerUid,
            peerUin = peerUin,
            chatType = chatType,
            name = peerName,
            myUin = myUin,
            messageNavigation = messageNavigation,
        )
    }
    LaunchedEffect(peerUid, chatType, lastMessageKey, timelineItems.size) {
        if (timelineItems.isEmpty()) return@LaunchedEffect
        val lastIndex = timelineItems.lastIndex
        if (!initialPositioned) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { itemCount -> itemCount > lastIndex }
            withFrameNanos { }
            listState.scrollToItem(lastIndex)
            withFrameNanos { }
            listState.scrollToItem(timelineItems.lastIndex)
            initialPositioned = true
        } else if (lastMessageKey != previousLastMessageKey && followNewMessages) {
            withFrameNanos { }
            listState.scrollToItem(lastIndex)
        }
        previousLastMessageKey = lastMessageKey
    }

    LaunchedEffect(peerUid, chatType, initialPositioned) {
        var previousState: TimelineScrollState? = null
        var reachedTopFromUpwardScroll = false
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItems
            val firstVisible = visible.minByOrNull { it.index }
            TimelineScrollState(
                isScrolling = listState.isScrollInProgress,
                firstVisibleItemIndex = firstVisible?.index ?: 0,
                firstVisibleItemOffset = firstVisible?.offset ?: 0,
                firstVisibleMessageSequence = visible
                    .asSequence()
                    .sortedBy { it.index }
                    .mapNotNull { visibleItem ->
                        (currentTimelineItems.value.getOrNull(visibleItem.index) as? ChatTimelineItem.Message)
                            ?.message
                            ?.msgSeq
                            ?.takeIf { it > 0L }
                    }
                    .firstOrNull(),
                atTop = !listState.canScrollBackward,
                atBottom = !listState.canScrollForward,
            )
        }.collect { state ->
            val movedTowardTop = previousState?.let { previous ->
                state.firstVisibleItemIndex < previous.firstVisibleItemIndex ||
                        (
                                state.firstVisibleItemIndex == previous.firstVisibleItemIndex &&
                                        state.firstVisibleItemOffset < previous.firstVisibleItemOffset
                                )
            } == true
            val movedTowardBottom = previousState?.let { previous ->
                state.firstVisibleItemIndex > previous.firstVisibleItemIndex ||
                        (
                                state.firstVisibleItemIndex == previous.firstVisibleItemIndex &&
                                        state.firstVisibleItemOffset > previous.firstVisibleItemOffset
                                )
            } == true
            atBottom = state.atBottom
            if (state.atBottom) {
                showJumpBottom = false
            } else if (state.isScrolling) {
                when {
                    movedTowardBottom -> showJumpBottom = true
                    movedTowardTop -> showJumpBottom = false
                }
            } else {
                showJumpBottom = false
            }
            if (state.isScrolling) {
                followNewMessages = state.atBottom && !movedTowardTop
            }
            if (!state.atTop) {
                reachedTopFromUpwardScroll = false
            } else if (movedTowardTop || previousState?.atTop == false) {
                reachedTopFromUpwardScroll = true
            }
            if (
                initialPositioned &&
                state.atTop &&
                reachedTopFromUpwardScroll
            ) {
                currentRequestOlderAtTop.value()
                reachedTopFromUpwardScroll = false
            }
            if (initialPositioned) {
                state.firstVisibleMessageSequence?.let(vm::onFirstVisibleMessageSequence)
            }
            previousState = state
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

    LaunchedEffect(messageNavigationState, timelineItems) {
        when (val navigation = messageNavigationState) {
            is ChatDetailViewModel.MessageNavigationState.Located -> {
                val targetIndex = timelineItems.indexOfFirst { item ->
                    (item as? ChatTimelineItem.Message)?.message?.stableKey == navigation.messageKey
                }
                if (targetIndex < 0) return@LaunchedEffect
                snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .first { itemCount -> itemCount > targetIndex }
                highlightedMessageKey = navigation.messageKey
                try {
                    listState.animateScrollToItem(targetIndex)
                    vm.completeMessageNavigation()
                    delay(1_500)
                    if (highlightedMessageKey == navigation.messageKey) {
                        highlightedMessageKey = null
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    throw kotlinx.coroutines.CancellationException()
                }
            }

            is ChatDetailViewModel.MessageNavigationState.Error -> {
                Toast.makeText(context, navigation.message, Toast.LENGTH_SHORT).show()
            }

            else -> Unit
        }
    }

    val showCallPage = chatType == 1 && peerUid.isNotBlank()
    val pagerState = androidx.wear.compose.foundation.pager.rememberPagerState(
        pageCount = { if (showCallPage) 3 else 2 },
    )
    LaunchedEffect(pagerState.currentPage, showCallPage, selectedActionMessage) {
        onReportingPageChanged(
            when {
                selectedActionMessage != null -> OfficialReportBridge.PageIds.LONG_PRESS_MENU
                showCallPage && pagerState.currentPage == 1 ->
                    OfficialReportBridge.PageIds.DIAL_INTERFACE
                else -> null
            },
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        androidx.wear.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (statusText.isNotEmpty() && statusText !in transientChatStatuses) {
                            Text(
                                text = statusText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .nestedScroll(topHistoryNestedScrollConnection),
                        ) {
                            QmceScreenScaffold(
                                scrollState = listState,
                                modifier = Modifier.fillMaxSize(),
                                edgeButtonSpacing = LocalQmceAdaptive.current.edgeButtonSpacing,
                                edgeButton = {
                                    if (multiSelectMode) {
                                        EdgeButton(
                                            onClick = { showMultiSelectActions = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = selectedMsgIds.isNotEmpty(),
                                        ) {
                                            Text("操作 (${selectedMsgIds.size})")
                                        }
                                    } else {
                                        EdgeButton(
                                            onClick = onOpenComposerMenu,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Keyboard,
                                                contentDescription = "输入",
                                            )
                                        }
                                    }
                                },
                            ) { contentPadding ->
                            TransformingLazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = contentPadding,
                            ) {
                                items(
                                    items = timelineItems,
                                    key = { item -> item.key },
                                ) { item ->
                                    when (item) {
                                        is ChatTimelineItem.DateDivider -> {
                                            ChatDateDivider(
                                                item.label,
                                                modifier = Modifier
                                                    .transformedHeight(this, transformationSpec)
                                                    .graphicsLayer {
                                                        with(SurfaceTransformation(transformationSpec)) {
                                                            applyContainerTransformation()
                                                            applyContentTransformation()
                                                        }
                                                    },
                                            )
                                        }
                                        is ChatTimelineItem.TimeDivider -> {
                                            ChatDateDivider(
                                                item.label,
                                                modifier = Modifier
                                                    .transformedHeight(this, transformationSpec)
                                                    .graphicsLayer {
                                                        with(SurfaceTransformation(transformationSpec)) {
                                                            applyContainerTransformation()
                                                            applyContentTransformation()
                                                        }
                                                    },
                                            )
                                        }
                                        is ChatTimelineItem.Message -> {
                                            val isSelected =
                                                multiSelectMode && item.message.msgId in selectedMsgIds
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .transformedHeight(this, transformationSpec)
                                                    .graphicsLayer {
                                                        with(SurfaceTransformation(transformationSpec)) {
                                                            applyContainerTransformation()
                                                            applyContentTransformation()
                                                        }
                                                    },
                                            ) {
                                                MessageBubble(
                                                    message = item.message,
                                                    isContinuation = item.isContinuation,
                                                    memberLevel = groupMemberLevels[item.message.senderUid],
                                                    memberTitle = groupMemberTitles[item.message.senderUid],
                                                    ensureImageCached = vm::ensureImageCached,
                                                    ensureVideoCached = vm::ensureVideoCached,
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
                                                        if (!multiSelectMode) selectedFile =
                                                            FileDetailTarget(message, file)
                                                    },
                                                    inlineKeyboardActions = inlineKeyboardActions,
                                                    onClickInlineKeyboard = { message, keyboard, button ->
                                                        vm.clickInlineKeyboardButton(
                                                            message,
                                                            keyboard,
                                                            button
                                                        )
                                                    },
                                                    voicePlaybackState = { voice ->
                                                        pttPlaybackStates[voice.media.messageId]
                                                    },
                                                    voiceTranslationState = { voice ->
                                                        pttTranslationStates[voice.media.messageId]
                                                    },
                                                    onToggleVoice = vm::toggleVoicePlayback,
                                                    onRequestCall = if (chatType == 1) {
                                                        { mode -> pendingCallRecordMode = mode }
                                                    } else {
                                                        null
                                                    },
                                                    isHighlighted = item.message.stableKey == highlightedMessageKey,
                                                )
                                                if (multiSelectMode && isSelected) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(if (item.message.isSelf) Alignment.CenterEnd else Alignment.CenterStart)
                                                            .padding(
                                                                start = if (!item.message.isSelf) 2.dp else 0.dp,
                                                                end = if (item.message.isSelf) 2.dp else 0.dp
                                                            )
                                                            .size(18.dp)
                                                            .background(
                                                                MaterialTheme.colorScheme.primary,
                                                                CircleShape
                                                            ),
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
                            val showScrollToBottom =
                                initialPositioned && !multiSelectMode && showJumpBottom
                            val showMessageNav =
                                initialPositioned &&
                                    !multiSelectMode &&
                                    atBottom &&
                                    messageNavigationState !is ChatDetailViewModel.MessageNavigationState.Idle
                            if (showScrollToBottom || showMessageNav) {
                                val loading =
                                    showMessageNav &&
                                        (
                                            messageNavigationState is ChatDetailViewModel.MessageNavigationState.Loading ||
                                                messageNavigationState is ChatDetailViewModel.MessageNavigationState.Located
                                            )
                                FilledTonalIconButton(
                                    onClick = {
                                        if (showScrollToBottom) {
                                            followNewMessages = true
                                            val lastIndex = timelineItems.lastIndex
                                            if (lastIndex >= 0) {
                                                listScrollScope.launch {
                                                    listState.animateScrollToItem(lastIndex)
                                                }
                                            }
                                        } else {
                                            vm.requestMessageNavigation()
                                        }
                                    },
                                    enabled = !loading,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = LocalQmceAdaptive.current.composerClearance)
                                        .touchTargetAwareSize(IconButtonDefaults.SmallButtonSize),
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else if (showScrollToBottom) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            contentDescription = "回到底部",
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = "跳转新消息",
                                        )
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
                        onOpenGroupInfo = onOpenGroupInfo,
                        onOpenMessageSearch = { showMessageSearch = true },
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
                    onOpenGroupInfo = onOpenGroupInfo,
                    onOpenMessageSearch = { showMessageSearch = true },
                    onOpenSettings = onOpenChatSettings,
                )
            }
        }
        if (showMultiSelectActions && multiSelectMode) {
            MultiSelectActionsScreen(
                selectedCount = selectedMsgIds.size,
                onDismiss = { showMultiSelectActions = false },
                onExit = {
                    showMultiSelectActions = false
                    vm.exitMultiSelect()
                },
                onSummary = {
                    showMultiSelectActions = false
                    vm.summarizeSelected()
                },
                onCopy = {
                    showMultiSelectActions = false
                    val text = vm.batchCopySelected()
                    if (text.isNotBlank()) copyMessageText(context, text)
                    else Toast.makeText(context, "没有可复制的文字", Toast.LENGTH_SHORT).show()
                },
                onForward = {
                    showMultiSelectActions = false
                    if (vm.prepareBatchForward()) onOpenContactPicker()
                },
                onShare = {
                    showMultiSelectActions = false
                    val selectedMessages = messages.filter { it.msgId in selectedMsgIds }
                    val text = vm.batchCopySelected()
                    val mediaFiles = selectedMessages.flatMap { it.localMediaFiles() }
                    if (text.isNotBlank() || mediaFiles.isNotEmpty()) {
                        shareMessageBatch(context, text, mediaFiles)
                    } else {
                        Toast.makeText(context, "没有可分享的内容", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = {
                    showMultiSelectActions = false
                    if (selectedMsgIds.isNotEmpty()) vm.batchDeleteSelected()
                },
            )
        }
        if (messageSummaryState !is ChatDetailViewModel.MessageSummaryState.Idle) {
            MessageSummaryScreen(
                state = messageSummaryState,
                onBack = vm::dismissMessageSummary,
                onRetry = vm::retryMessageSummary,
            )
        }
        if (forwardDetailState !is ChatDetailViewModel.ForwardDetailState.Idle) {
            ForwardDetailScreen(
                state = forwardDetailState,
                ensureImageCached = vm::ensureImageCached,
                ensureVideoCached = vm::ensureVideoCached,
                onOpenMedia = { viewerMedia = it },
                onOpenVideo = { videoPlayer = it },
                onOpenForward = vm::loadForwardDetail,
                onLongClick = { selectedActionMessage = it },
                onOpenFile = { message, file -> selectedFile = FileDetailTarget(message, file) },
                onDismiss = vm::dismissForwardDetail,
            )
        }
        viewerMedia?.let { media ->
            val resolvedModel = remember(messages, media.key, media.model) {
                resolveViewerModel(messages, media.key) ?: media.model
            }
            FullscreenMediaViewer(
                media = media.copy(model = resolvedModel),
                onDismiss = {
                    viewerMedia = null
                    mediaSaveLabel = "保存"
                },
                onSave = resolvedModel?.let { model ->
                    {
                        if (mediaSaveLabel == "正在保存…") return@let
                        mediaSaveScope.launch {
                            mediaSaveLabel = "正在保存…"
                            val source = when (model) {
                                is File -> model.absolutePath
                                is String -> model
                                else -> model.toString()
                            }
                            val result = withContext(Dispatchers.IO) {
                                mediaStoreSaver.saveImage(context, source)
                            }
                            mediaSaveLabel = result.fold(
                                onSuccess = { "已保存" },
                                onFailure = { "保存失败" },
                            )
                        }
                    }
                },
                onShare = resolvedModel?.let { model ->
                    {
                        val file = when (model) {
                            is File -> model.takeIf(File::isFile)
                            is String -> LocalMediaResolver.resolveFile(model)
                            else -> null
                        }
                        if (file == null) {
                            Toast.makeText(
                                context,
                                "图片尚未缓存",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            shareLocalMedia(context, file)
                        }
                    }
                },
                saveLabel = mediaSaveLabel,
            )
        }
        videoPlayer?.let { playback ->
            val resolvedFile = remember(messages, playback.messageKey, playback.elementId, playback.file) {
                resolveVideoFile(messages, playback) ?: playback.file
            }
            LaunchedEffect(playback.messageKey, playback.elementId, resolvedFile) {
                if (resolvedFile != null) return@LaunchedEffect
                val message = playback.messageKey?.let { key ->
                    messages.firstOrNull { it.stableKey == key }
                } ?: return@LaunchedEffect
                val video = message.contents
                    .filterIsInstance<ChatDetailViewModel.MessageContent.Video>()
                    .firstOrNull { playback.elementId <= 0L || it.elementId == playback.elementId }
                    ?: return@LaunchedEffect
                vm.ensureVideoCached(message, video)
            }
            LocalVideoPlayerScreen(
                file = resolvedFile,
                title = playback.title,
                onDismiss = { videoPlayer = null },
            )
        }
        selectedFile?.let { target ->
            val currentMessage =
                messages.firstOrNull { it.stableKey == target.message.stableKey } ?: target.message
            val currentFile = currentMessage.contents
                .filterIsInstance<ChatDetailViewModel.MessageContent.File>()
                .firstOrNull { it.elementId == target.content.elementId }
                ?: target.content
            FileDetailScreen(
                message = currentMessage,
                content = currentFile,
                onOpenLocalFile = { file -> openLocalFile(context, file) },
                onDownloadFile = { vm.requestFileDownload(currentMessage, currentFile) },
                downloadUnavailableReason = vm.fileDownloadUnavailableReason(currentFile),
                onDismiss = { selectedFile = null },
            )
        }
        pendingCallRecordMode?.let { mode ->
            CallRecordConfirmationScreen(
                mode = mode,
                onConfirm = {
                    pendingCallRecordMode = null
                    requestCall(mode)
                },
                onDismiss = { pendingCallRecordMode = null },
            )
        }
        selectedTextContent?.let { text ->
            MessageTextReaderScreen(
                text = text,
                onDismiss = { selectedTextContent = null },
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
                onAction = { action, actionTarget ->
                    selectedActionMessage = null
                    val reportEvent = when (action.id) {
                        "delete" -> OfficialReportBridge.ElementIds.DELETED
                        "recall" -> OfficialReportBridge.ElementIds.REVOCATION
                        "read_text" -> OfficialReportBridge.ElementIds.TO_TEXT
                        "translate_text" -> OfficialReportBridge.ElementIds.TO_TEXT
                        else -> null
                    }
                    if (reportEvent != null) {
                        OfficialReportBridge.reportElementClick(
                            target = actionTarget,
                            elementId = reportEvent,
                            params = mapOf("msg_id" to message.msgId.toString()),
                            reuseIdentifier = message.stableKey,
                        )
                    }
                    when (action.id) {
                        "copy" -> copyMessageText(
                            context,
                            MessageActionResolver.copyableText(message)
                        )

                        "read_text" -> selectedTextContent =
                            MessageActionResolver.copyableText(message)

                        "translate_text" -> {
                            if (!vm.translateVoiceToText(message)) {
                                Toast.makeText(context, "语音转文字暂不可用", Toast.LENGTH_SHORT).show()
                            }
                        }

                        "share_text" -> shareMessageText(
                            context,
                            MessageActionResolver.copyableText(message)
                        )

                        "view_media" -> message.firstLocalMediaFile()?.let { file ->
                            viewerMedia = ViewerMedia("${message.stableKey}:action", file, "图片")
                        } ?: Toast.makeText(context, "图片尚未缓存", Toast.LENGTH_SHORT).show()

                        "share_media" -> message.firstLocalMediaFile()?.let { file ->
                            shareLocalMedia(context, file)
                        }

                        "save_media" -> message.firstLocalMediaFile()?.let { file ->
                            mediaSaveScope.launch {
                                val isVideo = message.contents.any {
                                    it is ChatDetailViewModel.MessageContent.Video
                                }
                                val result = withContext(Dispatchers.IO) {
                                    if (isVideo) mediaStoreSaver.saveVideo(context, file.absolutePath)
                                    else mediaStoreSaver.saveImage(context, file.absolutePath)
                                }
                                Toast.makeText(
                                    context,
                                    result.fold(
                                        onSuccess = { "已保存" },
                                        onFailure = { "保存失败" },
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } ?: Toast.makeText(context, "媒体尚未缓存", Toast.LENGTH_SHORT).show()

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

                        "quote", "reply" -> {
                            if (vm.prepareReply(message)) onOpenInput()
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

private val TimeDividerWindowMillis = 5 * 60 * 1000L // 5 分钟

private fun List<ChatDetailViewModel.UiMsg>.toTimelineItems(): List<ChatTimelineItem> = buildList {
    var previousDayKey: String? = null
    var previousMessage: ChatDetailViewModel.UiMsg? = null
    this@toTimelineItems.forEach { message ->
        val dayKey = message.time.toDayKey()
        if (dayKey != previousDayKey) {
            add(ChatTimelineItem.DateDivider(formatChatDate(message.time), "date:$dayKey"))
            previousDayKey = dayKey
            previousMessage = null
        } else {
            val prev = previousMessage
            if (prev != null && (message.time - prev.time) >= TimeDividerWindowMillis) {
                val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(java.util.Date(message.time * 1000))
                add(ChatTimelineItem.TimeDivider(timeLabel, "time:${message.time}"))
            }
        }
        val isContinuation = previousMessage?.let { previous ->
            val sameSender = if (message.isSelf && previous.isSelf) {
                true
            } else {
                message.senderUid.isNotBlank() && message.senderUid == previous.senderUid
            }
            val elapsed = message.time - previous.time
            sameSender && message.isSelf == previous.isSelf &&
                elapsed in 0..ConsecutiveMessageWindowMillis
        } == true
        add(ChatTimelineItem.Message(message, isContinuation))
        previousMessage = message
    }
}

@Composable
private fun CallPage(
    peerName: String,
    onRequestCall: (CallMode) -> Unit,
    onOpenPacketTool: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    QmceScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "call-title") {
                Text(
                    "发起通话",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            item(key = "call-peer") {
                Text(
                    peerName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
            item(key = "call-voice") {
                Button(
                    onClick = { onRequestCall(CallMode.Voice) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("语音通话")
                }
            }
            item(key = "call-video") {
                Button(
                    onClick = { onRequestCall(CallMode.Video) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("视频通话")
                }
            }
            // 发包工具入口已隐藏；PacketToolScreen 代码保留
        }
    }
}

@Composable
private fun MultiSelectActionsScreen(
    selectedCount: Int,
    onDismiss: () -> Unit,
    onExit: () -> Unit,
    onSummary: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme
    QmceScreenScaffold(
        scrollState = listState,
        edgeButtonSpacing = LocalQmceAdaptive.current.edgeButtonSpacing,
        edgeButton = {
            EdgeButton(
                onClick = onForward,
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedCount > 0,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Forward,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text("转发 ($selectedCount)")
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "ms-title") {
                Text(
                    "已选 $selectedCount 条",
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            item(key = "ms-summary") {
                Button(
                    onClick = onSummary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("AI 总结")
                }
            }
            item(key = "ms-copy") {
                Button(
                    onClick = onCopy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("复制")
                }
            }
            item(key = "ms-share") {
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("系统分享")
                }
            }
            item(key = "ms-delete") {
                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError,
                    ),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("删除")
                }
            }
            item(key = "ms-exit") {
                Button(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("退出多选")
                }
            }
        }
    }
}

@Composable
internal fun MessageBubble(
    message: ChatDetailViewModel.UiMsg,
    isContinuation: Boolean = false,
    ensureImageCached: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.Image) -> Unit,
    ensureVideoCached: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.Video) -> Unit = { _, _ -> },
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
    voiceTranslationState: (ChatDetailViewModel.MessageContent.Voice) -> PttTranslationState? = { null },
    onToggleVoice: (ChatDetailViewModel.MessageContent.Voice) -> Unit = {},
    onRequestCall: ((CallMode) -> Unit)? = null,
    isHighlighted: Boolean = false,
    memberLevel: Int? = null,
    memberTitle: String? = null,
) {
    val systemTip = message.contents.singleOrNull() as? ChatDetailViewModel.MessageContent.SystemTip
    if (systemTip != null) {
        SystemTipLine(systemTip.text)
        return
    }
    val containerColor = when {
        hasMediaBubbleBackground(message) -> Color.Transparent
        message.isSelf -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val messageContentColor = if (message.isSelf) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (message.isSelf) Arrangement.End else Arrangement.Start
    val bubbleShape = messageBubbleShape(message.isSelf)
    val isSingleMedia = message.contents.singleOrNull().let {
        it is ChatDetailViewModel.MessageContent.Image ||
                it is ChatDetailViewModel.MessageContent.Giphy ||
                it is ChatDetailViewModel.MessageContent.Video
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isContinuation) 3.dp else 8.dp, bottom = 4.dp),
        horizontalArrangement = alignment
    ) {
        Column(
            modifier = Modifier.widthIn(max = MessageBubbleMaxWidth),
            horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start,
        ) {
            if (!message.isSelf && !isContinuation && message.senderNick.isNotBlank()) {
                Column(
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = message.senderNick,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        memberLevel?.takeIf { it > 0 }?.let { level ->
                            Text(
                                text = "LV$level",
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                            )
                        }
                        memberTitle?.takeIf { it.isNotBlank() }?.let { title ->
                            Text(
                                text = title,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        RoundedCornerShape(MessageBubbleCornerRadius),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            MessageCard(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .height(IntrinsicSize.Max)
                    .widthIn(max = MessageBubbleMaxWidth)
                    .then(
                        if (isHighlighted) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, bubbleShape)
                        } else {
                            Modifier
                        },
                    ),
                containerColor = containerColor,
                contentColor = messageContentColor,
                shape = bubbleShape,
                contentPadding = if (isSingleMedia) PaddingValues(0.dp) else PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
                onClick = { onTap?.invoke() },
                onLongClick = { onLongClick(message) },
            ) {
                message.contents.forEachIndexed { index, content ->
                    MessageContentItem(
                        message = message,
                        contentIndex = index,
                        content = content,
                        messageShape = bubbleShape,
                        ensureImageCached = ensureImageCached,
                        ensureVideoCached = ensureVideoCached,
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
                        voiceTranslationState = voiceTranslationState,
                        onToggleVoice = onToggleVoice,
                        onRequestCall = onRequestCall,
                    )
                }
            }
            // TODO: 换一个时间分割线 现在的很丑
            /*
            Text(
                text = formatMsgTime(message.time),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, end = 4.dp),
            )
             */
        }
    }
}

@Composable
private fun MessageContentItem(
    message: ChatDetailViewModel.UiMsg,
    contentIndex: Int,
    content: ChatDetailViewModel.MessageContent,
    messageShape: RoundedCornerShape,
    ensureImageCached: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.Image) -> Unit,
    ensureVideoCached: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.Video) -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    onOpenVideo: (VideoPlayback) -> Unit,
    onOpenForward: (ChatDetailViewModel.MessageContent.Forward) -> Unit,
    onLongClick: () -> Unit,
    onOpenReply: (ChatDetailViewModel.MessageContent.Reply) -> Unit,
    onOpenFile: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.File) -> Unit,
    inlineKeyboardActions: Map<String, ChatDetailViewModel.InlineKeyboardActionState>,
    onClickInlineKeyboard: (ChatDetailViewModel.MessageContent.InlineKeyboard, ChatDetailViewModel.MessageContent.InlineKeyboardButton) -> Unit,
    voicePlaybackState: (ChatDetailViewModel.MessageContent.Voice) -> PttPlaybackState?,
    voiceTranslationState: (ChatDetailViewModel.MessageContent.Voice) -> PttTranslationState?,
    onToggleVoice: (ChatDetailViewModel.MessageContent.Voice) -> Unit,
    onRequestCall: ((CallMode) -> Unit)?,
) {
    when (content) {
        is ChatDetailViewModel.MessageContent.Text -> {
            RichMessageText(content.value, onLongClick)
        }

        is ChatDetailViewModel.MessageContent.Image -> LocalMessageImage(
            content = content,
            ensureCached = { ensureImageCached(message, content) },
            onOpen = { file ->
                onOpenMedia(
                    ViewerMedia(
                        key = "${message.stableKey}:$contentIndex",
                        model = file,
                        description = "图片",
                    ),
                )
            },
        )

        is ChatDetailViewModel.MessageContent.Face -> {
            FaceMessageContent(content)
        }

        is ChatDetailViewModel.MessageContent.FaceBubble -> {
            Text(
                content.summary,
                color = LocalContentColor.current,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is ChatDetailViewModel.MessageContent.MarketFace -> {
            LocalMarketFace(content)
        }

        is ChatDetailViewModel.MessageContent.Giphy -> GiphyMessageContent(content) { mediaUrl ->
            onOpenMedia(ViewerMedia("${message.stableKey}:$contentIndex", mediaUrl, "GIF"))
        }

        is ChatDetailViewModel.MessageContent.Voice -> VoiceMessageContent(
            content = content,
            playbackState = voicePlaybackState(content),
            translationState = voiceTranslationState(content),
            onTogglePlayback = { onToggleVoice(content) },
        )

        is ChatDetailViewModel.MessageContent.Video -> VideoMessageContent(
            message = message,
            content = content,
            ensureCached = { ensureVideoCached(message, content) },
            onOpenVideo = onOpenVideo,
        )
        is ChatDetailViewModel.MessageContent.File -> FileMessageContent(content) {
            onOpenFile(
                message,
                content
            )
        }

        is ChatDetailViewModel.MessageContent.Reply -> ReplyMessageContent(
            content = content,
            messageShape = messageShape,
            onOpenReply = onOpenReply,
        )
        is ChatDetailViewModel.MessageContent.Card -> StructuredCardContent(
            title = content.title,
            description = content.description,
            tag = content.tag,
            previewUrl = content.previewUrl,
            actionUrl = content.actionUrl,
        )

        is ChatDetailViewModel.MessageContent.StructCard -> StructuredCardContent(
            title = content.title,
            description = listOfNotNull(
                content.description.takeIf { it.isNotBlank() },
                content.groupCode?.let { "群号 $it" },
            ).joinToString("\n"),
            tag = "群邀请",
            previewUrl = null,
            actionUrl = null,
        )

        is ChatDetailViewModel.MessageContent.Forward -> ForwardMessageContent(
            content,
            onOpenForward
        )

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

        is ChatDetailViewModel.MessageContent.Markdown -> MarkdownMessageContent(
            content.value,
            onLongClick
        )

        is ChatDetailViewModel.MessageContent.LinkPreview -> LinkPreviewMessageContent(content)
        is ChatDetailViewModel.MessageContent.CallRecord -> CallRecordMessageContent(
            content,
            onRequestCall
        )

        is ChatDetailViewModel.MessageContent.Unsupported -> MessageFallback("不支持的消息类型")
    }
}

private fun hasMediaBubbleBackground(message: ChatDetailViewModel.UiMsg): Boolean =
    message.contents.any { content ->
        content is ChatDetailViewModel.MessageContent.Image ||
                content is ChatDetailViewModel.MessageContent.Video ||
                content is ChatDetailViewModel.MessageContent.Giphy
    }

@Composable
private fun LocalMessageImage(
    content: ChatDetailViewModel.MessageContent.Image,
    ensureCached: () -> Unit,
    onOpen: (File?) -> Unit,
) {
    val localFile = remember(content.localPaths, content.thumbnailPaths, content.sourcePath) {
        LocalMediaResolver.firstAvailable(
            content.localPaths + content.thumbnailPaths + listOfNotNull(
                content.sourcePath
            )
        )
    }
    val size = mediaSize(content.width, content.height)
    LaunchedEffect(content.elementId, content.isLoading, content.loadError, localFile) {
        if (localFile == null && !content.isLoading) ensureCached()
    }
    Box(
        modifier = Modifier
            .size(size.width, size.height)
            .clip(RoundedCornerShape(MessageBubbleCornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            onClick = {
                ensureCached()
                onOpen(localFile)
            },
            modifier = Modifier.fillMaxSize(),
            colors = androidx.wear.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(MessageBubbleCornerRadius),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (localFile != null) {
                    AsyncImage(
                        model = localFile,
                        contentDescription = "图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    when {
                        content.isLoading -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                        content.loadError != null -> Text(
                            "图片加载失败",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        else -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalMarketFace(
    content: ChatDetailViewModel.MessageContent.MarketFace,
) {
    val context = LocalContext.current
    val cachedPaths = remember(content.element) {
        EmotionRepository.cachedMarketFacePaths(context, content.element)
    }
    val candidatePaths = remember(content.staticPath, content.dynamicPath, cachedPaths) {
        (listOf(content.staticPath, content.dynamicPath) + cachedPaths)
            .filterNotNull()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }
    val size = mediaSize(content.width, content.height)
    val marketFaceKey = remember(content.element) {
        content.element?.let { "${it.emojiPackageId}:${it.emojiId}" } ?: content.name
    }
    var failedPaths by remember(marketFaceKey) { mutableStateOf(emptySet<String>()) }
    var localPath by remember(marketFaceKey, candidatePaths) {
        mutableStateOf(
            candidatePaths.firstOrNull { path ->
                path !in failedPaths && LocalMediaResolver.resolveFile(path) != null
            },
        )
    }
    val localFile = localPath?.let(LocalMediaResolver::resolveFile)
    var officialDrawable by remember(marketFaceKey) { mutableStateOf<Drawable?>(null) }
    var officialDrawableVersion by remember(marketFaceKey) { mutableStateOf(0) }
    var officialRetry by remember(marketFaceKey) { mutableStateOf(0) }
    LaunchedEffect(marketFaceKey, candidatePaths, failedPaths, localPath) {
        if (localPath != null && localPath !in failedPaths) return@LaunchedEffect
        val deadline = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < deadline) {
            val nextPath = candidatePaths.firstOrNull { path ->
                path !in failedPaths && LocalMediaResolver.resolveFile(path) != null
            }
            if (nextPath != null) {
                localPath = nextPath
                return@LaunchedEffect
            }
            delay(150L)
        }
    }
    LaunchedEffect(marketFaceKey, localPath, content.element, officialRetry) {
        val element = content.element
        if (localPath == null && element != null && officialDrawable == null && officialRetry <= 2) {
            if (officialRetry > 0) delay(400L)
            EmotionRepository.loadMarketFaceDrawable(element) { drawable ->
                officialDrawable = drawable
                officialDrawableVersion++
                if (drawable == null && officialRetry < 2) officialRetry++
            }
        }
    }
    if (localFile != null && localPath !in failedPaths) {
        Box(
            modifier = Modifier
                .size(size.width, size.height)
                .clip(RoundedCornerShape(MessageBubbleCornerRadius)),
        ) {
            AsyncImage(
                model = localFile,
                contentDescription = content.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onError = {
                    localPath?.let { path -> failedPaths = failedPaths + path }
                    localPath = null
                },
            )
        }
    } else if (officialDrawable != null) {
        val currentDrawableVersion = officialDrawableVersion
        AndroidView(
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                imageView.tag = currentDrawableVersion
                imageView.setImageDrawable(officialDrawable)
            },
            modifier = Modifier.size(size.width, size.height),
        )
    } else {
        MessageFallback(content.name)
    }
}

@Composable
private fun FaceMessageContent(content: ChatDetailViewModel.MessageContent.Face) {
    val face = remember(
        content.faceType,
        content.faceIndex,
        content.text,
        content.packId,
        content.stickerId,
        content.stickerType,
        content.imageType,
        content.resultId,
        content.surpriseId,
    ) {
        EmotionRepository.systemFaceForMessage(
            faceType = content.faceType,
            ntFaceIndex = content.faceIndex,
            label = content.text,
            packId = content.packId,
            imageType = content.imageType,
            stickerId = content.stickerId,
            stickerType = content.stickerType,
            resultId = content.resultId,
            surpriseId = content.surpriseId,
        )
    }
    val isAnimated = remember(face) {
        face.faceType == 3 || (face.stickerType ?: 0) > 0
    }
    var drawable by remember(face) { mutableStateOf<Drawable?>(null) }
    val loadGeneration = remember { AtomicLong(0L) }
    LaunchedEffect(face) {
        val generation = loadGeneration.incrementAndGet()
        drawable = null
        EmotionRepository.loadSystemFaceDrawable(face) { loaded ->
            if (loadGeneration.get() == generation && (loaded != null || drawable == null)) {
                drawable = loaded
            }
        }
    }
    val fallbackText = remember(face, content.text) {
        EmotionRepository.systemFaceText(face).ifBlank { content.text }
    }
    if (drawable == null) {
        MessageFallback(fallbackText)
    } else {
        val currentDrawable = drawable
        val size = if (isAnimated) 120.dp else 34.dp
        AndroidView(
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                if (imageView.drawable !== currentDrawable) {
                    imageView.setImageDrawable(currentDrawable)
                    currentDrawable?.setVisible(true, true)
                }
                imageView.invalidate()
            },
            modifier = Modifier.size(size),
        )
        DisposableEffect(currentDrawable) {
            onDispose {
                currentDrawable?.setVisible(false, false)
            }
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
    val blocks = remember(value) { parseMarkdownBlocks(value) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> RichMessageText(
                    value = block.value,
                    onLongClick = onLongClick,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(5.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                )

                is MarkdownBlock.Heading -> RichMessageText(
                    value = block.value,
                    onLongClick = onLongClick,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                )

                is MarkdownBlock.Paragraph -> RichMessageText(block.value, onLongClick)
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val value: String, val level: Int) : MarkdownBlock
    data class Paragraph(val value: String) : MarkdownBlock
    data class Code(val value: String) : MarkdownBlock
}

private val markdownHeadingRegex = Regex("^\\s{0,3}(#{1,6})\\s+(.+)$")
private val markdownImageRegex = Regex("!\\[([^]]*)]\\([^)]*\\)")
private val markdownLinkRegex = Regex("\\[([^]]+)]\\((https?://[^\\s)]+)\\)")
private val markdownBulletRegex = Regex("^\\s*[-*+]\\s+")

private fun parseMarkdownBlocks(value: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val codeLines = mutableListOf<String>()
    var inCodeBlock = false
    value.replace("\r\n", "\n").lineSequence().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (inCodeBlock) {
                blocks += MarkdownBlock.Code(codeLines.joinToString("\n"))
                codeLines.clear()
            }
            inCodeBlock = !inCodeBlock
            return@forEach
        }
        if (inCodeBlock) {
            codeLines += line
            return@forEach
        }

        if (line.isBlank()) return@forEach
        val heading = markdownHeadingRegex.matchEntire(line)
        if (heading != null) {
            blocks += MarkdownBlock.Heading(
                value = heading.groupValues[2].toMarkdownDisplayText(),
                level = heading.groupValues[1].length,
            )
        } else {
            blocks += MarkdownBlock.Paragraph(line.toMarkdownDisplayText())
        }
    }
    if (inCodeBlock) blocks += MarkdownBlock.Code(codeLines.joinToString("\n"))
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(value)) }
}

private fun String.toMarkdownDisplayText(): String = this
    .replace(markdownImageRegex, "$1")
    .replace(markdownLinkRegex, "$1 ($2)")
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("__(.+?)__"), "$1")
    .replace(Regex("`([^`]+)`"), "$1")
    .replace(markdownBulletRegex, "• ")

@Composable
private fun LinkPreviewMessageContent(content: ChatDetailViewModel.MessageContent.LinkPreview) {
    val context = LocalContext.current
    LaunchedEffect(content.url, content.state) {
        if (content.state is LinkPreviewState.Idle) LinkPreviewRepository.request(content.url)
    }
    when (val state = content.state) {
        LinkPreviewState.Idle,
        LinkPreviewState.Loading,
            -> Text(
            text = "正在解析链接…",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        is LinkPreviewState.Ready -> {
            val preview = state.preview
            Card(
                onClick = { openHttpLink(context, preview.url) },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.wear.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(MessageBubbleCornerRadius),
                contentPadding = PaddingValues(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (preview.imageUrl != null) {
                        AsyncImage(
                            model = preview.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            preview.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        preview.description?.takeIf(String::isNotBlank)?.let { description ->
                            Spacer(Modifier.height(2.dp))
                            Text(
                                description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        LinkPreviewState.Failed -> Unit
    }
}

@Composable
private fun CallRecordMessageContent(
    content: ChatDetailViewModel.MessageContent.CallRecord,
    onRequestCall: ((CallMode) -> Unit)?,
) {
    val isVideo = content.type == 2 || content.text.contains("视频")
    Card(
        onClick = { onRequestCall?.invoke(if (isVideo) CallMode.Video else CallMode.Voice) },
        enabled = onRequestCall != null,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(MessageBubbleCornerRadius),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = if (isVideo) "视频通话" else "语音通话",
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
                    text = when {
                        onRequestCall != null -> "点击回拨"
                        content.hasRead -> "通话记录"
                        else -> "未读通话记录"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CallRecordConfirmationScreen(
    mode: CallMode,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (mode == CallMode.Video) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = if (mode == CallMode.Video) "发起视频通话？" else "发起语音通话？",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "将使用 QMCE 的通话服务",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onConfirm, modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Text("继续")
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun RichMessageText(
    value: String,
    onLongClick: () -> Unit,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(value, linkColor) { value.toLinkedAnnotatedString(linkColor) }
    var layoutResult by remember(value) { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = annotated,
        modifier = modifier.pointerInput(annotated) {
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
        style = style,
        onTextLayout = { layoutResult = it },
    )
}

@Composable
private fun ReplyMessageContent(
    content: ChatDetailViewModel.MessageContent.Reply,
    messageShape: RoundedCornerShape,
    onOpenReply: (ChatDetailViewModel.MessageContent.Reply) -> Unit,
) {
    Card(
        onClick = { onOpenReply(content) },
        enabled = !content.expired,
        modifier = Modifier
            .padding(bottom = 6.dp),
        shape = messageShape,
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = content.senderName,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        LocalMediaResolver.resolveFile(content.path)
    }
    Card(
        onClick = onOpenFile,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(MessageBubbleCornerRadius),
        contentPadding = PaddingValues(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(23.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    content.name,
                    color = LocalContentColor.current,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
    translationState: PttTranslationState?,
    onTogglePlayback: () -> Unit,
) {
    val phase = playbackState?.phase ?: PttPlaybackPhase.Idle
    val durationSeconds = content.media.durationSeconds
    val positionSeconds = (playbackState?.positionMillis ?: 0) / 1_000
    val status = when (phase) {
        PttPlaybackPhase.Preparing -> "正在准备..."
        PttPlaybackPhase.Playing -> "${formatMediaDuration(positionSeconds)} / ${
            formatMediaDuration(
                durationSeconds
            )
        }"

        PttPlaybackPhase.Paused -> "${formatMediaDuration(positionSeconds)} / ${
            formatMediaDuration(
                durationSeconds
            )
        }"

        PttPlaybackPhase.Failed -> playbackState?.error ?: "无法播放此语音"
        PttPlaybackPhase.Idle -> playbackState?.error
            ?: content.progress?.let { "传输中 $it%" }
            ?: "点击播放"
    }
    val translationLabel = when (translationState?.phase) {
        PttTranslationPhase.Loading -> "正在转换文字..."
        PttTranslationPhase.Failed -> translationState.error ?: "转文字失败，可从菜单重试"
        PttTranslationPhase.Success -> if (content.transcript.isNullOrBlank()) {
            "转换完成，等待消息更新..."
        } else {
            null
        }

        PttTranslationPhase.Idle, null -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(MessageBubbleCornerRadius))
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
                    Text(
                        transcript,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (content.transcript.isNullOrBlank() && translationLabel != null) {
                    Text(
                        text = translationLabel,
                        color = if (translationState?.phase == PttTranslationPhase.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
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
    message: ChatDetailViewModel.UiMsg,
    content: ChatDetailViewModel.MessageContent.Video,
    ensureCached: () -> Unit,
    onOpenVideo: (VideoPlayback) -> Unit,
) {
    val localFile = remember(content.filePath) {
        LocalMediaResolver.resolveFile(content.filePath)
    }
    val thumbnail = remember(content.thumbnailPaths) {
        LocalMediaResolver.firstAvailable(content.thumbnailPaths)
    }
    val size = mediaSize(content.width, content.height)
    LaunchedEffect(content.elementId, content.isLoading, content.loadError, localFile) {
        if (localFile == null && !content.isLoading) ensureCached()
    }
    Card(
        onClick = {
            ensureCached()
            onOpenVideo(
                VideoPlayback(
                    file = localFile,
                    title = "视频",
                    messageKey = message.stableKey,
                    elementId = content.elementId,
                ),
            )
        },
        enabled = true,
        modifier = Modifier
            .size(size.width, size.height)
            .clip(RoundedCornerShape(MessageBubbleCornerRadius)),
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(MessageBubbleCornerRadius),
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
                    contentScale = ContentScale.Fit,
                )
            }
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "播放视频",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = buildList {
                    add(formatDuration(content.durationSeconds))
                    content.progress?.let { add("$it%") }
                    when {
                        localFile != null -> Unit
                        content.loadError != null -> add("失败")
                        else -> add("缓存中")
                    }
                }.joinToString(" · "),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StructuredCardContent(
    title: String,
    description: String,
    tag: String?,
    previewUrl: String?,
    actionUrl: String?,
) {
    val context = LocalContext.current
    Card(
        onClick = { actionUrl?.let { openHttpLink(context, it) } },
        enabled = !actionUrl.isNullOrBlank(),
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.wear.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(MessageBubbleCornerRadius),
        contentPadding = PaddingValues(9.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            tag?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
            }
            if (title.isNotBlank()) {
                Text(
                    title,
                    color = LocalContentColor.current,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            description.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(3.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
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
                    contentScale = ContentScale.Fit,
                )
            }
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
        shape = RoundedCornerShape(MessageBubbleCornerRadius),
        contentPadding = PaddingValues(9.dp),
    ) {
        Text(
            content.title,
            color = LocalContentColor.current,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
        content.preview.forEach { line ->
            Text(
                line,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val URL_ANNOTATION_TAG = "url"
private val httpUrlRegex = Regex("https?://[^\\s<>\\\"]+")

private fun String.toLinkedAnnotatedString(linkColor: Color): AnnotatedString =
    buildAnnotatedString {
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(MessageBubbleCornerRadius))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                content.title,
                color = LocalContentColor.current,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            content.detail.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WalletMessageContent(content: ChatDetailViewModel.MessageContent.Wallet) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(MessageBubbleCornerRadius))
            .padding(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            content.iconUrl?.let { iconUrl ->
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp)),
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(MessageBubbleCornerRadius))
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(MessageBubbleCornerRadius))
            .padding(7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            "机器人操作",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        content.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { button ->
                    val actionKey = "${message.stableKey}:keyboard:${button.stableKey}"
                    val action = actionStates[actionKey]
                    val isPending =
                        action?.phase == ChatDetailViewModel.InlineKeyboardActionPhase.Pending
                    val label = action?.label ?: button.label
                    Button(
                        onClick = { onClick(content, button) },
                        enabled = !isPending,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun SystemTipLine(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 7.dp),
        color = MaterialTheme.colorScheme.outline,
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
            shape = RoundedCornerShape(MessageBubbleCornerRadius),
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = androidx.wear.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(MessageBubbleCornerRadius),
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun resolveViewerModel(
    messages: List<ChatDetailViewModel.UiMsg>,
    key: String,
): Any? {
    val sep = key.lastIndexOf(':')
    if (sep <= 0) return null
    val stableKey = key.substring(0, sep)
    val contentIndex = key.substring(sep + 1).toIntOrNull() ?: return null
    val message = messages.firstOrNull { it.stableKey == stableKey } ?: return null
    val content = message.contents.getOrNull(contentIndex) ?: return null
    return when (content) {
        is ChatDetailViewModel.MessageContent.Image -> LocalMediaResolver.firstAvailable(
            content.localPaths + content.thumbnailPaths + listOfNotNull(content.sourcePath),
        )
        is ChatDetailViewModel.MessageContent.Giphy -> content.mediaUrl
        else -> null
    }
}

private fun resolveVideoFile(
    messages: List<ChatDetailViewModel.UiMsg>,
    playback: VideoPlayback,
): File? {
    val message = playback.messageKey?.let { key ->
        messages.firstOrNull { it.stableKey == key }
    } ?: return playback.file
    val video = message.contents
        .filterIsInstance<ChatDetailViewModel.MessageContent.Video>()
        .firstOrNull { playback.elementId <= 0L || it.elementId == playback.elementId }
        ?: return playback.file
    return LocalMediaResolver.resolveFile(video.filePath) ?: playback.file
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
private fun ChatDateDivider(
    date: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            date,
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

private fun Long.toDayKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(this * 1000))

private fun formatChatDate(time: Long): String =
    SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(time * 1000))

private fun formatMsgTime(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time * 1000))
