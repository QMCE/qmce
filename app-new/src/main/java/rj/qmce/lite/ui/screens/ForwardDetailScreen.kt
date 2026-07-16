package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import rj.qmce.lite.viewmodel.ChatDetailViewModel

@Composable
internal fun ForwardDetailScreen(
    state: ChatDetailViewModel.ForwardDetailState,
    ensureImageCached: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.Image) -> Unit,
    onOpenMedia: (ViewerMedia) -> Unit,
    onOpenVideo: (VideoPlayback) -> Unit,
    onOpenForward: (ChatDetailViewModel.MessageContent.Forward) -> Unit,
    onLongClick: (ChatDetailViewModel.UiMsg) -> Unit,
    onOpenFile: (ChatDetailViewModel.UiMsg, ChatDetailViewModel.MessageContent.File) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val title = when (state) {
        ChatDetailViewModel.ForwardDetailState.Idle -> "聊天记录"
        is ChatDetailViewModel.ForwardDetailState.Loading -> state.title
        is ChatDetailViewModel.ForwardDetailState.Ready -> state.title
        is ChatDetailViewModel.ForwardDetailState.Error -> state.title
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        when (state) {
            ChatDetailViewModel.ForwardDetailState.Idle -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
            }

            is ChatDetailViewModel.ForwardDetailState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .size(32.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }

            is ChatDetailViewModel.ForwardDetailState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = state.message,
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Text("关闭")
                    }
                }
            }

            is ChatDetailViewModel.ForwardDetailState.Ready -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                ) {
                    item(key = "forward-detail-title") {
                        Text(
                            text = title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    itemsIndexed(
                        items = state.messages,
                        key = { index, message -> "forward:${message.stableKey}:$index" },
                    ) { _, message ->
                        MessageBubble(
                            message = message,
                            ensureImageCached = ensureImageCached,
                            onOpenMedia = onOpenMedia,
                            onOpenVideo = onOpenVideo,
                            onOpenForward = onOpenForward,
                            onLongClick = onLongClick,
                            onOpenReply = { reply ->
                                val targetIndex = state.messages.indexOfFirst { message ->
                                    message.msgId == reply.targetMessageId ||
                                            (reply.targetSequence != null && message.msgSeq == reply.targetSequence)
                                }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(
                                            targetIndex
                                        )
                                    }
                                }
                            },
                            onOpenFile = onOpenFile,
                        )
                    }
                }
            }
        }
    }
}
