package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil3.compose.AsyncImage
import rj.qmce.lite.viewmodel.QZoneViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QZoneScreen(
    vm: QZoneViewModel,
    onOpenComposer: () -> Unit,
    onOpenComment: (QZoneViewModel.FeedItem) -> Unit,
) {
    val feeds by vm.feeds.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val loading by vm.loading.collectAsState()
    val isLoadingMore by vm.loadingMoreFlow.collectAsState()
    val operationStatus by vm.operationStatus.collectAsState()
    val scheme = MaterialTheme.colorScheme
    var gallery by remember { mutableStateOf<List<ViewerMedia>>(emptyList()) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LaunchedEffect(listState.canScrollForward, feeds.isNotEmpty(), loading, isLoadingMore) {
        if (!listState.canScrollForward && feeds.isNotEmpty() && !loading && !isLoadingMore && vm.hasMoreData()) {
            vm.loadMore()
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        androidx.wear.compose.foundation.lazy.TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "qzone-compose") {
                Button(
                    onClick = onOpenComposer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                ) { Text("发表动态") }
            }
            if (statusText.isNotEmpty()) {
                item(key = "qzone-status") {
                    QZoneListNotice(statusText, scheme.outline, transformationSpec)
                }
            }
            if (operationStatus.isNotEmpty()) {
                item(key = "qzone-operation") {
                    QZoneListNotice(operationStatus, scheme.primary, transformationSpec)
                }
            }
            if (loading && feeds.isEmpty()) {
                item(key = "qzone-initial-loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContainerTransformation()
                                    applyContentTransformation()
                                }
                            }
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (feeds.isEmpty()) {
                item(key = "qzone-empty") {
                    QZoneListNotice("暂无动态", scheme.outline, transformationSpec, verticalPadding = 16.dp)
                }
            } else {
            feeds.forEach { feed ->
                item(key = "feed:${feed.feedId}") {
                    FeedCard(
                        feed = feed,
                        vm = vm,
                        onToggleLike = { vm.toggleLike(feed.feedId) },
                        onComment = { onOpenComment(feed) },
                        onOpenMedia = { urls ->
                            gallery = urls.mapIndexed { index, url ->
                                ViewerMedia("qzone:${feed.feedId}:$index", url, "动态图片")
                            }
                        },
                        onOpenVideo = { videoUrl = it },
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            // 底部加载指示器
            if (isLoadingMore) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContainerTransformation()
                                    applyContentTransformation()
                                }
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("加载中…", style = MaterialTheme.typography.bodySmall, color = scheme.outline)
                    }
                }
            }
            if (!vm.hasMoreData() && !isLoadingMore) {
                item {
                    Text(
                        "— 已经到底了 —",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.outline,
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContainerTransformation()
                                    applyContentTransformation()
                                }
                            }
                            .padding(vertical = 8.dp),
                    )
                }
            }
            }
        }
    }

    if (gallery.isNotEmpty()) {
        FullscreenMediaGallery(
            media = gallery,
            onDismiss = { gallery = emptyList() },
        )
    }
    videoUrl?.let { url ->
        RemoteVideoPlayerScreen(
            url = url,
            title = "动态视频",
            onDismiss = { videoUrl = null },
        )
    }
}

@Composable
private fun TransformingLazyColumnItemScope.QZoneListNotice(
    text: String,
    color: Color,
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
    verticalPadding: androidx.compose.ui.unit.Dp = 4.dp,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, transformationSpec)
            .graphicsLayer {
                with(SurfaceTransformation(transformationSpec)) {
                    applyContainerTransformation()
                    applyContentTransformation()
                }
            }
            .padding(horizontal = 12.dp, vertical = verticalPadding),
    )
}


@Composable
private fun FeedCard(
    feed: QZoneViewModel.FeedItem,
    vm: QZoneViewModel,
    onToggleLike: () -> Unit,
    onComment: () -> Unit,
    onOpenMedia: (List<String>) -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val scheme = MaterialTheme.colorScheme
    val timeText = remember(feed.displayTime, feed.time) {
        feed.displayTime.ifEmpty {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(feed.time * 1000))
        }
    }

    Card(
        onClick = {
            when {
                feed.videoUrl != null -> onOpenVideo(feed.videoUrl)
                feed.picUrls.isNotEmpty() -> onOpenMedia(feed.picUrls)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        transformation = transformation,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // 头部：头像 + 昵称 + 时间
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = vm.avatarUrl(feed.uin),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(feed.nick, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(timeText, style = MaterialTheme.typography.bodySmall, color = scheme.outline)
                }
            }

            if (feed.content.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(feed.content, style = MaterialTheme.typography.bodyLarge, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }

            feed.forward?.let { forward ->
                Spacer(Modifier.height(6.dp))
                ForwardFeedContent(
                    forward = forward,
                    hasMedia = feed.picUrls.isNotEmpty(),
                )
            }

            // 图片网格（最多显示 3 张缩略图）
            if (feed.picUrls.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    feed.picUrls.take(3).forEach { url ->
                        Card(
                            onClick = { onOpenMedia(feed.picUrls) },
                            modifier = Modifier
                                .size(48.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = scheme.surfaceContainerHigh,
                            ),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    if (feed.picUrls.size > 3) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+${feed.picUrls.size - 3}", style = MaterialTheme.typography.bodySmall, color = scheme.outline)
                        }
                    }
                }
            }

            feed.videoUrl?.let { url ->
                Spacer(Modifier.height(5.dp))
                CompactButton(
                    onClick = { onOpenVideo(url) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                    ),
                    label = { Text("播放视频") },
                )
            }

            // 底部：赞/评论数
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactButton(
                    onClick = onToggleLike,
                    icon = {
                        Icon(
                            imageVector = if (feed.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (feed.isLiked) "取消点赞" else "点赞",
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (feed.isLiked) scheme.errorContainer else scheme.surfaceContainerHigh,
                        contentColor = if (feed.isLiked) scheme.onErrorContainer else scheme.onSurface,
                    ),
                    label = {
                        Text(
                        text = feed.likeCount.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
                Spacer(Modifier.width(12.dp))
                CompactButton(
                    onClick = onComment,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
                    label = { Text("评论 ${feed.commentCount}") },
                )
            }
        }
    }
}

@Composable
private fun ForwardFeedContent(
    forward: QZoneViewModel.ForwardInfo,
    hasMedia: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val author = forward.author.ifBlank { "原作者" }
    val body = when {
        forward.content.isNotBlank() -> forward.content
        forward.isUnavailable -> "原动态暂不可见"
        hasMedia -> "图片动态"
        else -> "该动态没有文字内容"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = "@$author",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
