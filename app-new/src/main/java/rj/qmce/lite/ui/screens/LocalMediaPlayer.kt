package rj.qmce.lite.ui.screens

import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun LocalVideoPlayerScreen(
    file: File,
    title: String,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(file) { mutableStateOf(false) }
    var playing by remember(file) { mutableStateOf(false) }
    var durationMs by remember(file) { mutableIntStateOf(0) }
    var currentMs by remember(file) { mutableIntStateOf(0) }
    var error by remember(file) { mutableStateOf<String?>(null) }
    var holder by remember { mutableStateOf<SurfaceHolder?>(null) }

    DisposableEffect(file, holder) {
        val surfaceHolder = holder
        if (surfaceHolder == null) return@DisposableEffect onDispose { }
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        runCatching {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setDisplay(surfaceHolder)
            mediaPlayer.setOnPreparedListener {
                prepared = true
                durationMs = it.duration.coerceAtLeast(0)
                it.start()
                playing = true
            }
            mediaPlayer.setOnCompletionListener { playing = false; currentMs = 0 }
            mediaPlayer.setOnErrorListener { _, _, _ -> error = "无法播放此视频"; playing = false; true }
            mediaPlayer.prepareAsync()
        }.onFailure { error = "无法播放此视频" }
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

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1)
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp).background(Color(0xFF121212)),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).also { view ->
                        view.holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(surfaceHolder: SurfaceHolder) { holder = surfaceHolder }
                            override fun surfaceChanged(surfaceHolder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                            override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) { holder = null }
                        })
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!prepared && error == null) CircularProgressIndicator(modifier = Modifier.size(30.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val mediaPlayer = player ?: return@Button
                    if (playing) { mediaPlayer.pause(); playing = false }
                    else { mediaPlayer.start(); playing = true }
                },
                enabled = prepared && error == null,
                modifier = Modifier.height(38.dp),
            ) { Text(if (playing) "暂停" else "播放", fontSize = 11.sp) }
            Text(
                "${formatMediaDuration(currentMs / 1000)} / ${formatMediaDuration(durationMs / 1000)}",
                color = Color.White,
                fontSize = 10.sp,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text("关闭", fontSize = 11.sp) }
        }
    }
}

fun formatMediaDuration(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "%d:%02d".format(safe / 60, safe % 60)
}
