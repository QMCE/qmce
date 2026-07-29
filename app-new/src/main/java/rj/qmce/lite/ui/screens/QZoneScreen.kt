package rj.qmce.lite.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppCard
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil3.compose.AsyncImage
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox
import rj.qmce.lite.ui.theme.LocalQmceAdaptive
import rj.qmce.lite.viewmodel.QZoneViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QZoneScreen(
    vm: QZoneViewModel,
    onOpenComposer: () -> Unit,
    onOpenDetail: (QZoneViewModel.FeedItem) -> Unit,
) {
    val feeds by vm.feeds.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val loading by vm.loading.collectAsState()
    val isLoadingMore by vm.loadingMoreFlow.collectAsState()
    val operationStatus by vm.operationStatus.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isFeedError = feeds.isEmpty() && !loading && statusText.isNotBlank() &&
        statusText != "暂无动态" &&
        statusText != "加载空间动态..."

    LaunchedEffect(listState.canScrollForward, feeds.isNotEmpty(), loading, isLoadingMore) {
        if (!listState.canScrollForward && feeds.isNotEmpty() && !loading && !isLoadingMore && vm.hasMoreData()) {
            vm.loadMore()
        }
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButtonSpacing = LocalQmceAdaptive.current.edgeButtonSpacing,
        edgeButton = {
            if (isFeedError) {
                EdgeButton(
                    onClick = { vm.loadFeeds(forceRefresh = true) },
                    modifier = Modifier.fillMaxWidth(),
                    buttonSize = EdgeButtonSize.Small,
                    enabled = !loading,
                ) {
                    Text(if (loading) "重试中…" else "重试")
                }
            } else {
                OfficialReportTargetBox(
                    key = "qzone-compose",
                    modifier = Modifier.fillMaxWidth(),
                    elementId = OfficialReportBridge.ElementIds.RELEASE_DYNAMIC,
                ) { reportTarget ->
                    EdgeButton(
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.RELEASE_DYNAMIC,
                            )
                            onOpenComposer()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        buttonSize = EdgeButtonSize.Small,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "发表动态")
                    }
                }
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            if (statusText.isNotEmpty() && statusText != "暂无动态") {
                item(key = "qzone-status") {
                    QZoneListNotice(
                        statusText,
                        if (isFeedError) scheme.error else scheme.outline,
                        transformationSpec,
                    )
                }
            }
            if (operationStatus.isNotEmpty()) {
                item(key = "qzone-operation") {
                    QZoneListNotice(operationStatus, scheme.primary, transformationSpec)
                }
            }
            when {
                loading && feeds.isEmpty() -> {
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
                }

                feeds.isEmpty() && !isFeedError -> {
                    item(key = "qzone-empty") {
                        QZoneListNotice(
                            "暂无动态",
                            scheme.outline,
                            transformationSpec,
                            verticalPadding = 16.dp
                        )
                    }
                }

                feeds.isNotEmpty() -> {
                    feeds.forEach { feed ->
                        item(key = "feed:${feed.feedId}") {
                            OfficialReportTargetBox(
                                key = "qzone-feed:${feed.feedId}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec),
                                elementId = OfficialReportBridge.ElementIds.DYNAMIC_ENTRY,
                                params = mapOf(
                                    "dynamic_id" to feed.feedId,
                                    "touin" to feed.uin,
                                ),
                                reuseIdentifier = OfficialReportBridge.ElementIds.dynamicEntryReuse(
                                    feed.feedId,
                                ),
                                reportAllExposures = true,
                            ) { reportTarget ->
                                FeedPreviewCard(
                                    feed = feed,
                                    avatarUrl = vm.avatarUrl(feed.uin),
                                    onOpenDetail = {
                                        OfficialReportBridge.reportElementClick(
                                            target = reportTarget,
                                            elementId = OfficialReportBridge.ElementIds.DYNAMIC_ENTRY,
                                            params = mapOf(
                                                "dynamic_id" to feed.feedId,
                                                "touin" to feed.uin,
                                            ),
                                            reuseIdentifier = OfficialReportBridge.ElementIds.dynamicEntryReuse(
                                                feed.feedId,
                                            ),
                                        )
                                        onOpenDetail(feed)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    transformation = SurfaceTransformation(transformationSpec),
                                )
                            }
                        }
                    }
                }
            }
            if (isLoadingMore) {
                item(key = "qzone-loading-more") {
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
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "加载中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.outline
                        )
                    }
                }
            } else if (feeds.isNotEmpty() && !vm.hasMoreData()) {
                item(key = "qzone-end") {
                    QZoneListNotice(
                        "— 已经到底了 —",
                        scheme.outline,
                        transformationSpec,
                        verticalPadding = 8.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.QZoneListNotice(
    text: String,
    color: Color,
    transformationSpec: TransformationSpec,
    verticalPadding: Dp = 4.dp,
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
private fun FeedPreviewCard(
    feed: QZoneViewModel.FeedItem,
    avatarUrl: String,
    onOpenDetail: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val scheme = MaterialTheme.colorScheme
    val timeText = remember(
        feed.displayTime,
        feed.time
    ) { feed.displayTime.ifBlank { feedTimeText(feed.time) } }
    AppCard(
        onClick = onOpenDetail,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        transformation = transformation,
        appImage = {
            Box(
                modifier = Modifier
                    .size(CardDefaults.AppImageSize)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        },
        appName = { Text(feed.nick, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        time = { Text(timeText) },
        title = {
            Text(
                feed.content.ifBlank { "该动态没有文字内容" },
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            feed.forward?.let {
                Spacer(Modifier.height(6.dp))
                ForwardFeedContent(it, hasMedia = feed.picUrls.isNotEmpty())
            }
            if (feed.picUrls.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    feed.picUrls.take(3).forEach { url ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(scheme.surfaceContainerHigh)
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "动态图片",
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
                            Text(
                                "+${feed.picUrls.size - 3}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.outline
                            )
                        }
                    }
                }
            }
            if (feed.videoUrl != null) {
                Spacer(Modifier.height(6.dp))
                Text("视频动态", style = MaterialTheme.typography.bodySmall, color = scheme.primary)
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "赞 ${feed.likeCount} · 评论 ${feed.commentCount}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

internal fun feedTimeText(time: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(time * 1000))

@Composable
internal fun ForwardFeedContent(
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
