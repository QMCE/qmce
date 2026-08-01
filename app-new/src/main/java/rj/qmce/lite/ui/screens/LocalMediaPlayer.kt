package rj.qmce.lite.ui.screens

import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rj.qmce.lite.data.media.MediaStoreSaver
import rj.qmce.lite.ui.theme.LocalQmceAdaptive
import rj.qmce.lite.ui.wear.QmceListHeader
import java.io.File

@Composable
fun LocalVideoPlayerScreen(
    file: File?,
    title: String,
    onDismiss: () -> Unit,
) {
    VideoPlayerScreen(
        sourceKey = file?.absolutePath ?: "pending:$title",
        source = file?.absolutePath,
        title = title,
        localFile = file,
        onDismiss = onDismiss,
    )
}

@Composable
fun RemoteVideoPlayerScreen(
    url: String,
    title: String,
    onDismiss: () -> Unit,
) {
    VideoPlayerScreen(
        sourceKey = url,
        source = url,
        title = title,
        localFile = null,
        onDismiss = onDismiss,
    )
}

@Composable
private fun VideoPlayerScreen(
    sourceKey: String,
    source: String?,
    title: String,
    localFile: File?,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saver = remember { MediaStoreSaver() }
    var saveLabel by remember(sourceKey) { mutableStateOf("保存") }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val userScrollEnabled = pagerState.currentPage > 0

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = userScrollEnabled,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { page ->
        when (page) {
            0 -> {
                if (source.isNullOrBlank()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(30.dp))
                            Text(
                                "正在缓存视频…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                } else {
                    VideoPlayerPage(
                        sourceKey = sourceKey,
                        source = source,
                        title = title,
                        onDismiss = onDismiss,
                        onOpenMenu = {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                    )
                }
            }
            else -> VideoActionsPage(
                title = title,
                saveLabel = saveLabel,
                canSave = localFile?.isFile == true,
                canShare = localFile?.isFile == true,
                onSave = {
                    val file = localFile ?: return@VideoActionsPage
                    if (saveLabel == "正在保存…") return@VideoActionsPage
                    scope.launch {
                        saveLabel = "正在保存…"
                        val result = withContext(Dispatchers.IO) {
                            saver.saveVideo(context, file.absolutePath)
                        }
                        saveLabel = result.fold(
                            onSuccess = { "已保存" },
                            onFailure = { "保存失败" },
                        )
                    }
                },
                onShare = {
                    val file = localFile
                    if (file == null || !file.isFile) {
                        Toast.makeText(context, "视频尚未缓存", Toast.LENGTH_SHORT).show()
                    } else {
                        shareLocalMedia(context, file)
                    }
                },
            )
        }
    }
}

@Composable
private fun VideoPlayerPage(
    sourceKey: String,
    source: String,
    title: String,
    onDismiss: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    var player by remember(sourceKey) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(sourceKey) { mutableStateOf(false) }
    var playing by remember(sourceKey) { mutableStateOf(false) }
    var durationMs by remember(sourceKey) { mutableIntStateOf(0) }
    var currentMs by remember(sourceKey) { mutableIntStateOf(0) }
    var error by remember(sourceKey) { mutableStateOf<String?>(null) }
    var videoWidth by remember(sourceKey) { mutableIntStateOf(0) }
    var videoHeight by remember(sourceKey) { mutableIntStateOf(0) }
    var holder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var dismissDragX by remember(sourceKey) { mutableFloatStateOf(0f) }
    val adaptive = LocalQmceAdaptive.current

    DisposableEffect(sourceKey, holder) {
        val surfaceHolder = holder
        if (surfaceHolder == null) return@DisposableEffect onDispose { }
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        runCatching {
            mediaPlayer.setDataSource(source)
            mediaPlayer.setDisplay(surfaceHolder)
            mediaPlayer.setOnVideoSizeChangedListener { _, w, h ->
                videoWidth = w
                videoHeight = h
            }
            mediaPlayer.setOnPreparedListener {
                prepared = true
                durationMs = it.duration.coerceAtLeast(0)
                videoWidth = it.videoWidth
                videoHeight = it.videoHeight
                it.start()
                playing = true
            }
            mediaPlayer.setOnCompletionListener {
                playing = false
                currentMs = 0
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                error = "视频暂不支持播放"
                playing = false
                true
            }
            mediaPlayer.prepareAsync()
        }.onFailure { error = "视频暂不支持播放" }
        onDispose {
            player = null
            runCatching { mediaPlayer.release() }
        }
    }
    LaunchedEffect(playing) {
        while (playing) {
            currentMs = runCatching { player?.currentPosition ?: currentMs }.getOrDefault(currentMs)
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(sourceKey) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dismissDragX > 96f) onDismiss()
                        dismissDragX = 0f
                    },
                    onDragCancel = { dismissDragX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount > 0f) {
                            change.consume()
                            dismissDragX += dragAmount
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val aspectModifier = if (videoWidth > 0 && videoHeight > 0) {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = adaptive.screenContentPadding.calculateLeftPadding(
                    androidx.compose.ui.unit.LayoutDirection.Ltr,
                ))
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { view ->
                    view.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
                            holder = surfaceHolder
                        }

                        override fun surfaceChanged(
                            surfaceHolder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
                            holder = null
                        }
                    })
                }
            },
            modifier = aspectModifier,
            update = { view ->
                if (videoWidth > 0 && videoHeight > 0) {
                    view.holder.setFixedSize(videoWidth, videoHeight)
                }
            },
        )
        if (!prepared && error == null) {
            CircularProgressIndicator(modifier = Modifier.size(30.dp))
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(adaptive.screenContentPadding),
            )
        }
        FilledIconButton(
            onClick = onOpenMenu,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(adaptive.screenContentPadding),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "菜单")
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
                .padding(adaptive.screenContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = {
                        val mediaPlayer = player ?: return@FilledIconButton
                        if (playing) {
                            mediaPlayer.pause()
                            playing = false
                        } else {
                            mediaPlayer.start()
                            playing = true
                        }
                    },
                    enabled = prepared && error == null,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playing) "暂停" else "播放",
                    )
                }
                Text(
                    "${formatMediaDuration(currentMs / 1000)} / ${formatMediaDuration(durationMs / 1000)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun VideoActionsPage(
    title: String,
    saveLabel: String,
    canSave: Boolean,
    canShare: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background),
        ) {
            item(key = "video-actions-header") {
                QmceListHeader(
                    text = title.ifBlank { "视频" },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "video-action-save") {
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                ) { Text(saveLabel) }
            }
            item(key = "video-action-share") {
                Button(
                    onClick = onShare,
                    enabled = canShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                ) { Text("分享") }
            }
        }
    }
}

fun formatMediaDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / 60, safe % 60)
}
