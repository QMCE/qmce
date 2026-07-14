package rj.qmce.lite.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.material3.*
import coil3.compose.AsyncImage
import androidx.core.content.ContextCompat
import rj.qmce.lite.viewmodel.QZoneViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QZoneScreen(vm: QZoneViewModel) {
    val context = LocalContext.current
    val feeds by vm.feeds.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val loading by vm.loading.collectAsState()
    val isLoadingMore by vm.loadingMoreFlow.collectAsState()
    val operationStatus by vm.operationStatus.collectAsState()
    val scheme = MaterialTheme.colorScheme
    var gallery by remember { mutableStateOf<List<ViewerMedia>>(emptyList()) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var showComposer by remember { mutableStateOf(false) }
    var commentTarget by remember { mutableStateOf<QZoneViewModel.FeedItem?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }
    var publishDraft by remember { mutableStateOf("") }
    var publishUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var pickerNotice by remember { mutableStateOf<String?>(null) }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasQZoneGalleryAccess(context)) {
            pickerNotice = null
            showImagePicker = true
        } else {
            pickerNotice = "未获得图片访问权限"
        }
    }

    if (showImagePicker) {
        LocalImagePickerScreen(
            existingUris = publishUris.mapTo(linkedSetOf()) { it.toString() },
            onDismiss = {
                showImagePicker = false
                showComposer = true
            },
            onConfirm = { uris ->
                publishUris = (publishUris + uris).distinctBy(Uri::toString)
                showImagePicker = false
                showComposer = true
            },
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = { showComposer = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
            ),
        ) {
            Text("发表动态", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (statusText.isNotEmpty()) {
            Text(statusText, fontSize = 10.sp, color = scheme.outline, modifier = Modifier.padding(4.dp))
        }
        if (operationStatus.isNotEmpty()) {
            Text(operationStatus, fontSize = 10.sp, color = scheme.primary, modifier = Modifier.padding(2.dp))
        }
        pickerNotice?.let { notice ->
            Text(notice, fontSize = 10.sp, color = scheme.error, modifier = Modifier.padding(2.dp))
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
                    FeedCard(
                        feed = feed,
                        vm = vm,
                        onToggleLike = { vm.toggleLike(feed.feedId) },
                        onComment = { commentTarget = feed },
                        onOpenMedia = { urls ->
                            gallery = urls.mapIndexed { index, url ->
                                ViewerMedia("qzone:${feed.feedId}:$index", url, "动态图片")
                            }
                        },
                        onOpenVideo = { videoUrl = it },
                    )
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
    if (showComposer) {
        QZoneTextInputDialog(
            title = "发表动态",
            hint = "写点什么…",
            confirmLabel = "发表",
            initialValue = publishDraft,
            mediaCount = publishUris.size,
            onTextChanged = { publishDraft = it },
            onPickMedia = {
                showComposer = false
                if (hasQZoneGalleryAccess(context)) {
                    showImagePicker = true
                } else {
                    galleryPermissionLauncher.launch(qZoneGalleryPermissions())
                }
            },
            onDismiss = { showComposer = false },
            onSubmit = { text ->
                showComposer = false
                vm.publishImages(context, text, publishUris)
                publishDraft = ""
                publishUris = emptyList()
            },
        )
    }
    commentTarget?.let { feed ->
        QZoneTextInputDialog(
            title = "评论 ${feed.nick}",
            hint = "写下评论…",
            confirmLabel = "发送",
            comments = feed.comments,
            onDismiss = { commentTarget = null },
            onSubmit = { text ->
                commentTarget = null
                vm.comment(feed.feedId, text)
            },
        )
    }
}

@Composable
private fun QZoneTextInputDialog(
    title: String,
    hint: String,
    confirmLabel: String,
    initialValue: String = "",
    mediaCount: Int = 0,
    comments: List<QZoneViewModel.FeedComment> = emptyList(),
    onTextChanged: (String) -> Unit = {},
    onPickMedia: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = { Text(title, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        confirmButton = {
            Button(
                onClick = { onSubmit(value.trim()) },
                enabled = value.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                ),
            ) { Text(confirmLabel, fontSize = 11.sp) }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                ),
            ) { Text("取消", fontSize = 11.sp) }
        },
        content = {
            if (comments.isEmpty()) {
                item {
                    Text(
                        "暂无评论",
                        color = scheme.outline,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            } else {
                comments.takeLast(12).forEach { comment ->
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                .background(scheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(comment.author, color = scheme.primary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            if (comment.text.isNotBlank()) {
                                Text(comment.text, color = scheme.onSurface, fontSize = 11.sp)
                            }
                            comment.replies.takeLast(4).forEach { reply ->
                                Text(
                                    "↳ ${reply.author}: ${reply.text}",
                                    color = scheme.onSurfaceVariant,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            item {
                BasicTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        onTextChanged(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    textStyle = TextStyle(
                        color = scheme.onSurface,
                        fontSize = 12.sp,
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isBlank()) {
                                Text(hint, color = scheme.outline, fontSize = 12.sp)
                            }
                            innerTextField()
                        }
                    },
                )
            }
            onPickMedia?.let { pickMedia ->
                item {
                    Button(
                        onClick = pickMedia,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface,
                        ),
                    ) {
                        Text(
                            if (mediaCount == 0) "添加图片" else "已选 $mediaCount 张图片 · 继续添加",
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        },
    )
}

private fun qZoneGalleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasQZoneGalleryAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    }
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    else -> ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun FeedCard(
    feed: QZoneViewModel.FeedItem,
    vm: QZoneViewModel,
    onToggleLike: () -> Unit,
    onComment: () -> Unit,
    onOpenMedia: (List<String>) -> Unit,
    onOpenVideo: (String) -> Unit,
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
                                modifier = Modifier.fillMaxSize().clickable { onOpenMedia(feed.picUrls) },
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

            feed.videoUrl?.let { url ->
                Spacer(Modifier.height(5.dp))
                Button(
                    onClick = { onOpenVideo(url) },
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                    ),
                ) {
                    Text("播放视频", fontSize = 10.sp)
                }
            }

            // 底部：赞/评论数
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onToggleLike,
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (feed.isLiked) scheme.errorContainer else scheme.surfaceContainerHigh,
                        contentColor = if (feed.isLiked) scheme.onErrorContainer else scheme.onSurface,
                    ),
                ) {
                    Text(
                        text = if (feed.isLiked) "♥ ${feed.likeCount}" else "♡ ${feed.likeCount}",
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onComment,
                    modifier = Modifier.height(30.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.surfaceContainerHigh,
                        contentColor = scheme.onSurface,
                    ),
                ) {
                    Text("评论 ${feed.commentCount}", fontSize = 10.sp)
                }
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
