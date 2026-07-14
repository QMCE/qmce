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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

private data class LocalGalleryImage(
    val id: Long,
    val uri: Uri,
    val dateModified: Long,
    val sizeBytes: Long,
)

private sealed interface GalleryLoadState {
    data object Loading : GalleryLoadState
    data class Ready(val images: List<LocalGalleryImage>) : GalleryLoadState
    data class Error(val message: String) : GalleryLoadState
}

@Composable
fun LocalImagePickerScreen(
    existingUris: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    var mediaRevision by remember { mutableStateOf(0) }
    DisposableEffect(context) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                mediaRevision++
            }
        }
        context.contentResolver.registerContentObserver(collection, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val galleryState by produceState<GalleryLoadState>(GalleryLoadState.Loading, context, mediaRevision) {
        value = withContext(Dispatchers.IO) {
            runCatching { GalleryLoadState.Ready(loadRecentImages(context)) }
                .getOrElse { GalleryLoadState.Error(it.message ?: "无法读取本地图片") }
        }
    }
    val selected = remember { mutableStateListOf<String>() }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(38.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("选择图片", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "已选 ${selected.size} 张",
                    color = scheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
            Button(
                onClick = {
                    onConfirm(selected.map(Uri::parse))
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                ),
            ) {
                Text("添加", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(6.dp))
        when (val state = galleryState) {
            GalleryLoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
            is GalleryLoadState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = scheme.error, fontSize = 11.sp)
            }
            is GalleryLoadState.Ready -> {
                if (state.images.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("没有可用图片", color = scheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.images, key = { image -> image.id }) { image ->
                            val uriString = image.uri.toString()
                            val isAdded = uriString in existingUris
                            val isSelected = uriString in selected
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(116.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(scheme.surfaceContainer)
                                    .clickable(enabled = !isAdded) {
                                        if (isSelected) selected.remove(uriString)
                                        else selected.add(uriString)
                                    },
                            ) {
                                SystemMediaThumbnail(image)
                                if (isSelected || isAdded) {
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
                                        Text(
                                            if (isAdded) "✓" else "${selected.indexOf(uriString) + 1}",
                                            color = scheme.onPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
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

private fun loadRecentImages(context: Context): List<LocalGalleryImage> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.SIZE,
    )
    val images = ArrayList<LocalGalleryImage>()
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        while (cursor.moveToNext() && images.size < 240) {
            val id = cursor.getLong(idIndex)
            val sizeBytes = cursor.getLong(sizeIndex)
            if (sizeBytes <= 0L) continue
            images += LocalGalleryImage(
                id = id,
                uri = ContentUris.withAppendedId(collection, id),
                dateModified = cursor.getLong(modifiedIndex),
                sizeBytes = sizeBytes,
            )
        }
    }
    return images
}

@Composable
private fun SystemMediaThumbnail(image: LocalGalleryImage) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, image.uri, image.dateModified, image.sizeBytes) {
        value = withContext(Dispatchers.IO) { loadSystemThumbnail(context, image) }
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

private fun loadSystemThumbnail(context: Context, image: LocalGalleryImage): Bitmap? {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return runCatching { resolver.loadThumbnail(image.uri, Size(320, 320), null) }.getOrNull()
    }
    return runCatching {
        MediaStore.Images.Thumbnails.getThumbnail(
            resolver,
            image.id,
            MediaStore.Images.Thumbnails.MINI_KIND,
            null,
        )
    }.getOrNull() ?: runCatching {
        resolver.openFileDescriptor(image.uri, "r")?.use { descriptor ->
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor)
        }
    }.getOrNull()
}
