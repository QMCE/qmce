package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.material3.*
import coil3.compose.AsyncImage
import rj.qmce.lite.viewmodel.QZoneViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QZoneScreen(vm: QZoneViewModel) {
    val feeds by vm.feeds.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val loading by vm.loading.collectAsState()
    val isLoadingMore by vm.loadingMoreFlow.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (statusText.isNotEmpty()) {
            Text(statusText, fontSize = 10.sp, color = scheme.outline, modifier = Modifier.padding(4.dp))
        }

        if (loading && feeds.isEmpty()) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            return
        }

        if (feeds.isEmpty()) {
            Text("暂无动态", fontSize = 11.sp, color = scheme.outline, modifier = Modifier.padding(16.dp))
            return
        }

        val listState = rememberScalingLazyListState()

        // 到底过滑时触发加载更多
        LaunchedEffect(listState.canScrollForward) {
            if (!listState.canScrollForward && feeds.isNotEmpty() && !loading && !isLoadingMore && vm.hasMoreData()) {
                vm.loadMore()
            }
        }

        androidx.wear.compose.foundation.lazy.ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                viewportVerticalOffsetResolver = { 0 },
            ),
        ) {
            feeds.forEach { feed ->
                item {
                    FeedCard(feed, vm)
                }
            }
            // 底部加载指示器
            if (isLoadingMore) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("加载中…", fontSize = 10.sp, color = scheme.outline)
                    }
                }
            }
            if (!vm.hasMoreData() && !isLoadingMore) {
                item {
                    Text(
                        "— 已经到底了 —",
                        fontSize = 10.sp,
                        color = scheme.outline,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedCard(feed: QZoneViewModel.FeedItem, vm: QZoneViewModel) {
    val scheme = MaterialTheme.colorScheme
    val timeText = remember(feed.displayTime, feed.time) {
        feed.displayTime.ifEmpty {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(feed.time * 1000))
        }
    }

    Card(
        onClick = { /* TODO: 详情/评论 */ },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
                    Text(feed.nick, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(timeText, fontSize = 9.sp, color = scheme.outline)
                }
            }

            if (feed.content.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(feed.content, fontSize = 11.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
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
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.surfaceContainerHigh),
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
                            Text("+${feed.picUrls.size - 3}", fontSize = 10.sp, color = scheme.outline)
                        }
                    }
                }
            }

            // 底部：赞/评论数
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (feed.isLiked) "❤ ${feed.likeCount}" else "♡ ${feed.likeCount}",
                    fontSize = 10.sp,
                    color = if (feed.isLiked) Color(0xFFE57373) else scheme.outline,
                )
                Spacer(Modifier.width(12.dp))
                Text("💬 ${feed.commentCount}", fontSize = 10.sp, color = scheme.outline)
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
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = body,
            fontSize = 10.sp,
            color = scheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
