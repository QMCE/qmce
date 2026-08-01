package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil3.compose.AsyncImage
import kotlin.math.abs
import rj.qmce.lite.ui.wear.QmceListHeader

@Composable
fun FullscreenMediaViewer(
    media: ViewerMedia,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    saveLabel: String = "保存",
) {
    BackHandler(onBack = onDismiss)
    val hasActions = onSave != null || onShare != null
    if (!hasActions) {
        MediaViewerPage(media = media, onDismiss = onDismiss)
        return
    }
    var scale by remember(media.key) { mutableFloatStateOf(1f) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val userScrollEnabled = scale > 1.05f || pagerState.currentPage > 0

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 0) {
            scale = 1f
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled = userScrollEnabled,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) { page ->
        if (page == 0) {
            MediaViewerPage(
                media = media,
                onDismiss = onDismiss,
                scale = scale,
                onScaleChange = { scale = it },
                enableHorizontalDismiss = scale <= 1.05f,
            )
        } else {
            MediaActionsPage(
                title = media.description.ifBlank { "媒体" },
                saveLabel = saveLabel,
                showSave = onSave != null,
                showShare = onShare != null,
                canSave = onSave != null && media.model != null,
                canShare = onShare != null && media.model != null,
                onSave = { onSave?.invoke() },
                onShare = { onShare?.invoke() },
            )
        }
    }
}

@Composable
private fun MediaViewerPage(
    media: ViewerMedia,
    onDismiss: () -> Unit,
    scale: Float? = null,
    onScaleChange: ((Float) -> Unit)? = null,
    enableHorizontalDismiss: Boolean = true,
) {
    var localScale by remember(media.key) { mutableFloatStateOf(1f) }
    val currentScale = scale ?: localScale
    val setScale: (Float) -> Unit = onScaleChange ?: { localScale = it }

    var offsetX by remember(media.key) { mutableFloatStateOf(0f) }
    var offsetY by remember(media.key) { mutableFloatStateOf(0f) }
    var loaded by remember(media.key) { mutableStateOf(media.model == null) }
    var dismissDrag by remember(media.key) { mutableFloatStateOf(0f) }
    var dismissDragX by remember(media.key) { mutableFloatStateOf(0f) }

    LaunchedEffect(currentScale) {
        if (currentScale <= 1.01f) {
            offsetX = 0f
            offsetY = 0f
        }
    }

    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        var nextScale = (currentScale * zoomChange).coerceIn(1f, 4f)
        if (nextScale <= 1.01f) {
            nextScale = 1f
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
        setScale(nextScale)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(media.key, currentScale) {
                detectTapGestures(onTap = { onDismiss() })
            }
            .pointerInput(media.key, currentScale) {
                if (currentScale > 1.05f) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (abs(dismissDrag) > 96f) onDismiss()
                        dismissDrag = 0f
                    },
                    onDragCancel = { dismissDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dismissDrag += dragAmount
                    },
                )
            }
            .pointerInput(media.key, currentScale, enableHorizontalDismiss) {
                if (!enableHorizontalDismiss || currentScale > 1.05f) return@pointerInput
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
        if (media.model == null) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        } else {
            AsyncImage(
                model = media.model,
                contentDescription = media.description,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        translationX = offsetX
                        translationY = offsetY + (if (currentScale <= 1.01f) dismissDrag * 0.35f else 0f)
                    }
                    .transformable(transformState)
                    .pointerInput(media.key) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (currentScale > 1f) {
                                    setScale(1f)
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    setScale(2f)
                                }
                            },
                            onTap = { },
                        )
                    },
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = true },
            )
        }
        if (!loaded && media.model != null) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun MediaActionsPage(
    title: String,
    saveLabel: String,
    showSave: Boolean,
    showShare: Boolean,
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
            item(key = "media-actions-header") {
                QmceListHeader(
                    text = title,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            if (showSave) {
                item(key = "media-action-save") {
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
            }
            if (showShare) {
                item(key = "media-action-share") {
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
}

data class ViewerMedia(
    val key: String,
    val model: Any?,
    val description: String,
)
