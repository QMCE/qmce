package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage

@Composable
fun FullscreenMediaViewer(
    media: ViewerMedia,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveLabel: String = "保存",
) {
    BackHandler(onBack = onDismiss)
    var scale by remember(media.key) { mutableFloatStateOf(1f) }
    var offsetX by remember(media.key) { mutableFloatStateOf(0f) }
    var offsetY by remember(media.key) { mutableFloatStateOf(0f) }
    var loaded by remember(media.key) { mutableStateOf(false) }
    val transformState = rememberTransformableState { _, zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale == 1f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(media.key) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = media.model,
            contentDescription = media.description,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .transformable(transformState)
                .pointerInput(media.key) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2f
                            }
                        },
                        onTap = { },
                    )
                },
            contentScale = ContentScale.Fit,
            onSuccess = { loaded = true },
            onError = { loaded = true },
        )
        if (!loaded) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        }
        if (onSave != null) {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) { Text(saveLabel) }
        }
    }
}

data class ViewerMedia(
    val key: String,
    val model: Any,
    val description: String,
)
