package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import coil3.compose.AsyncImage
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.avatar.IAvatarLoaderApi
import com.tencent.qqnt.avatar.WatchAvatarView
import kotlinx.coroutines.GlobalScope
import rj.qmce.lite.viewmodel.ContactsViewModel
import java.io.File

@Composable
fun ContactsScreen(
    vm: ContactsViewModel,
    onOpenChat: (String, String, String) -> Unit, // uid, uin, name
) {
    val categories by vm.categories.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val loading by vm.loading.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (statusText.isNotEmpty()) {
            Text(statusText, fontSize = 10.sp, color = scheme.outline, modifier = Modifier.padding(4.dp))
        }

        if (loading && categories.isEmpty()) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            return
        }

        if (categories.isEmpty()) {
            Text("暂无联系人", fontSize = 11.sp, color = scheme.outline, modifier = Modifier.padding(16.dp))
            return
        }

        androidx.wear.compose.foundation.lazy.ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                viewportVerticalOffsetResolver = { 0 },
            ),
        ) {
            categories.forEach { category ->
                item {
                    // 分组标题
                    Text(
                        text = category.name + " (${category.buddies.size})",
                        fontSize = 10.sp,
                        color = scheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
                category.buddies.forEach { buddy ->
                    item {
                        val avatarModel = buddy.avatarPath
                            .removePrefix("file://")
                            .takeIf { it.isNotBlank() }
                            ?.let(::File)
                            ?.takeIf(File::isFile)
                        Button(
                            onClick = { onOpenChat(buddy.uid, buddy.uin.toString(), buddy.nick) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurface,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(scheme.surfaceContainerHigh, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ContactAvatar(
                                        localAvatar = avatarModel,
                                        remoteAvatarUrls = buddy.avatarUrls,
                                        fallbackText = buddy.nick.take(1).ifEmpty { "?" },
                                    )
                                    if (buddy.uin > 0L) {
                                        AndroidView(
                                            factory = { context ->
                                                WatchAvatarView(context, null).also { avatarView ->
                                                    runCatching {
                                                        QRoute.api(IAvatarLoaderApi::class.java)
                                                            .build(context)
                                                            .b(avatarView)
                                                            .e(buddy.uin, GlobalScope)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(1.dp)
                                                .alpha(0f),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = buddy.remark.ifEmpty { buddy.nick },
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactAvatar(
    localAvatar: File?,
    remoteAvatarUrls: List<String>,
    fallbackText: String,
) {
    val scheme = MaterialTheme.colorScheme
    var remoteIndex by remember(localAvatar, remoteAvatarUrls) { mutableIntStateOf(0) }
    val model = localAvatar ?: remoteAvatarUrls.getOrNull(remoteIndex)
    Text(
        text = fallbackText,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = scheme.primary,
    )
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            onError = {
                if (localAvatar == null && remoteIndex < remoteAvatarUrls.lastIndex) remoteIndex++
            },
        )
    }
}
