package rj.qmce.lite.ui.screens

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check

private data class LocalGalleryVideo(
    val id: Long,
    val uri: Uri,
    val dateModified: Long,
    val sizeBytes: Long,
)

private sealed interface VideoGalleryLoadState {
    data object Loading : VideoGalleryLoadState
    data class Ready(val videos: List<LocalGalleryVideo>) : VideoGalleryLoadState
    data class Error(val message: String) : VideoGalleryLoadState
}

@Composable
fun LocalVideoPickerScreen(
    onDismiss: () -> Unit,
    onConfirm: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var mediaRevision by remember { mutableStateOf(0) }
    DisposableEffect(context) {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                mediaRevision++
            }
        }
        context.contentResolver.registerContentObserver(collection, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val galleryState by produceState<VideoGalleryLoadState>(
        initialValue = VideoGalleryLoadState.Loading,
        key1 = context,
        key2 = mediaRevision,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { VideoGalleryLoadState.Ready(loadRecentVideos(context)) }
                .getOrElse { VideoGalleryLoadState.Error(it.message ?: "无法读取本地视频") }
        }
    }
    var selected by remember { mutableStateOf<Uri?>(null) }
    val scheme = MaterialTheme.colorScheme
    val scaffoldState = rememberLazyListState()

    BackHandler(onBack = onDismiss)
    ScreenScaffold(
        scrollState = scaffoldState,
        scrollIndicator = null,
        edgeButton = {
            EdgeButton(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
                buttonSize = EdgeButtonSize.Small,
            ) { Text("发送") }
        },
        edgeButtonSpacing = 2.5.dp,
    ) { contentPadding ->
        when (val state = galleryState) {
            VideoGalleryLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            is VideoGalleryLoadState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = scheme.error, style = MaterialTheme.typography.bodySmall)
            }
            is VideoGalleryLoadState.Ready -> {
                if (state.videos.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有可用视频", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.videos, key = { video -> video.id }) { video ->
                            val isSelected = video.uri == selected
                            Card(
                                onClick = { selected = video.uri },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(116.dp),
                                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    SystemVideoThumbnail(video)
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0x66000000)),
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(scheme.primary),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "已选择",
                                                tint = scheme.onPrimary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadRecentVideos(context: Context): List<LocalGalleryVideo> {
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DATE_MODIFIED,
        MediaStore.Video.Media.SIZE,
    )
    val videos = ArrayList<LocalGalleryVideo>()
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        while (cursor.moveToNext() && videos.size < 240) {
            val id = cursor.getLong(idIndex)
            val sizeBytes = cursor.getLong(sizeIndex)
            if (sizeBytes <= 0L) continue
            videos += LocalGalleryVideo(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                dateModified = cursor.getLong(modifiedIndex),
                sizeBytes = sizeBytes,
            )
        }
    }
    return videos
}

@Composable
private fun SystemVideoThumbnail(video: LocalGalleryVideo) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, video.uri, video.dateModified, video.sizeBytes) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            loadVideoThumbnail(context, video)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

private fun loadVideoThumbnail(context: Context, video: LocalGalleryVideo): Bitmap? {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return runCatching { resolver.loadThumbnail(video.uri, Size(320, 320), null) }.getOrNull()
    }
    return runCatching {
        MediaStore.Video.Thumbnails.getThumbnail(
            resolver,
            video.id,
            MediaStore.Video.Thumbnails.MINI_KIND,
            null,
        )
    }.getOrNull() ?: runCatching {
        resolver.openFileDescriptor(video.uri, "r")?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor)
        }
    }.getOrNull()
}
