package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import rj.qmce.lite.viewmodel.QZoneViewModel

@Composable
fun QZoneFeedDetailScreen(
    feedId: String,
    initialFeed: QZoneViewModel.FeedItem,
    vm: QZoneViewModel,
    onOpenComment: (QZoneViewModel.FeedItem) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val feeds by vm.feeds.collectAsState()
    val feed = feeds.firstOrNull { it.feedId == feedId } ?: initialFeed
    val context = LocalContext.current
    val shareText = buildString {
        append(feed.nick.ifBlank { "QQ用户" })
        if (feed.content.isNotBlank()) append(": ").append(feed.content)
        feed.forward?.content?.takeIf { it.isNotBlank() }?.let {
            append("\n\n转发内容：").append(it)
        }
    }
    var gallery by remember(feed.feedId) { mutableStateOf<List<ViewerMedia>>(emptyList()) }
    var videoUrl by remember(feed.feedId) { mutableStateOf<String?>(null) }
    val listState = rememberTransformingLazyColumnState()
    val scheme = MaterialTheme.colorScheme

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = { onOpenComment(feed) },
                buttonSize = EdgeButtonSize.Small,
            ) { Text("评论") }
        },
        edgeButtonSpacing = 2.5.dp,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "feed-detail-author:${feed.feedId}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = vm.avatarUrl(feed.uin),
                            contentDescription = "${feed.nick}的头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            feed.nick,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            feed.displayTime.ifBlank { feedTimeText(feed.time) },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (feed.content.isNotBlank()) {
                item(key = "feed-detail-content:${feed.feedId}") {
                    Text(
                        feed.content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            feed.forward?.let { forward ->
                item(key = "feed-detail-forward:${feed.feedId}") {
                    ForwardFeedContent(
                        forward = forward,
                        hasMedia = feed.picUrls.isNotEmpty(),
                    )
                }
            }
            if (shareText.isNotBlank()) {
                item(key = "feed-detail-copy:${feed.feedId}") {
                    Button(
                        onClick = { copyMessageText(context, shareText) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                        icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    ) { Text("复制动态") }
                }
                item(key = "feed-detail-share:${feed.feedId}") {
                    Button(
                        onClick = { shareMessageText(context, shareText) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                        icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    ) { Text("系统分享") }
                }
            }
            if (feed.picUrls.isNotEmpty()) {
                item(key = "feed-detail-images:${feed.feedId}") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        feed.picUrls.chunked(2).forEachIndexed { rowIndex, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                row.forEachIndexed { columnIndex, url ->
                                    Card(
                                        onClick = {
                                            gallery = feed.picUrls.mapIndexed { index, imageUrl ->
                                                ViewerMedia(
                                                    "qzone:${feed.feedId}:$index",
                                                    imageUrl,
                                                    "动态图片"
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(96.dp),
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = "动态图片 ${rowIndex * 2 + columnIndex + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            feed.videoUrl?.let { url ->
                item(key = "feed-detail-video:${feed.feedId}") {
                    CompactButton(
                        onClick = { videoUrl = url },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledVariantButtonColors(),
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "播放视频") },
                    )
                }
            }
            item(key = "feed-detail-like:${feed.feedId}") {
                CompactButton(
                    onClick = { vm.toggleLike(feed.feedId) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    icon = {
                        Icon(
                            imageVector = if (feed.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (feed.isLiked) "取消点赞" else "点赞",
                        )
                    },
                    colors = if (feed.isLiked) {
                        ButtonDefaults.filledVariantButtonColors()
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                )
            }
            item(key = "feed-detail-comments-title:${feed.feedId}") {
                Text(
                    "评论 ${feed.commentCount}",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (feed.comments.isEmpty()) {
                item(key = "feed-detail-comments-empty:${feed.feedId}") {
                    Text(
                        "还没有评论",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            } else {
                feed.comments.forEachIndexed { index, comment ->
                    item(key = "feed-detail-comment:${feed.feedId}:$index:${comment.id}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(scheme.surfaceContainerHigh)
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                        ) {
                            Text(
                                comment.author,
                                color = scheme.primary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (comment.text.isNotBlank()) {
                                Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            comment.replies.forEach { reply ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 3.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = "回复",
                                        tint = scheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${reply.author}: ${reply.text}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (gallery.isNotEmpty()) {
        FullscreenMediaGallery(media = gallery, onDismiss = { gallery = emptyList() })
    }
    videoUrl?.let { url ->
        RemoteVideoPlayerScreen(url = url, title = "动态视频", onDismiss = { videoUrl = null })
    }
}
