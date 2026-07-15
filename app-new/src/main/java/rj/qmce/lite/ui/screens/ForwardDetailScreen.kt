package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

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

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ForwardDetailHeader(title, onDismiss)
        when (state) {
            ChatDetailViewModel.ForwardDetailState.Idle -> Unit
            is ChatDetailViewModel.ForwardDetailState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                }
            }
            is ChatDetailViewModel.ForwardDetailState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.message,
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
                ScreenScaffold(scrollState = listState) { contentPadding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = contentPadding,
                    ) {
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
                                    coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
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
}

@Composable
private fun ForwardDetailHeader(title: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.CenterStart),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 29.dp),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
