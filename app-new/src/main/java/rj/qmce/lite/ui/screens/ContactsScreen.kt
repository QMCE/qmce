package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.wear.compose.material3.*
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import coil3.compose.AsyncImage
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.avatar.IAvatarLoaderApi
import com.tencent.qqnt.avatar.WatchAvatarView
import kotlinx.coroutines.GlobalScope
import rj.qmce.lite.viewmodel.ContactsViewModel
import java.io.File
import java.util.Locale

@Composable
fun ContactsScreen(
    vm: ContactsViewModel,
    onOpenChat: (String, String, String) -> Unit, // uid, uin, name
) {
    val categories by vm.categories.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val loading by vm.loading.collectAsState()
    val scheme = MaterialTheme.colorScheme
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val visibleCategories = remember(categories, normalizedQuery) {
        if (normalizedQuery.isBlank()) categories else categories.mapNotNull { category ->
            val buddies = category.buddies.filter { buddy ->
                listOf(buddy.nick, buddy.remark, buddy.uid, buddy.uin.toString())
                    .any { it.contains(normalizedQuery, ignoreCase = true) }
            }
            category.copy(buddies = buddies).takeIf { it.buddies.isNotEmpty() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (statusText.isNotEmpty()) {
                Text(statusText, fontSize = 10.sp, color = scheme.outline, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            IconButton(onClick = { showSearch = true }) {
                Icon(Icons.Default.Search, contentDescription = "搜索联系人")
            }
        }

        if (loading && categories.isEmpty()) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            return
        }

        if (visibleCategories.isEmpty()) {
            Text(
                if (categories.isEmpty()) "暂无联系人" else "没有匹配联系人",
                fontSize = 11.sp,
                color = scheme.outline,
                modifier = Modifier.padding(16.dp),
            )
            if (showSearch) {
                ContactSearchDialog(
                    query = query,
                    onQueryChange = { query = it },
                    onDismiss = { showSearch = false },
                )
            }
            return
        }

        if (showSearch) {
            ContactSearchDialog(
                query = query,
                onQueryChange = { query = it },
                onDismiss = { showSearch = false },
            )
        }

        androidx.wear.compose.foundation.lazy.ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                viewportVerticalOffsetResolver = { 0 },
            ),
        ) {
            visibleCategories.forEach { category ->
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
private fun ContactSearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = { Text("搜索联系人") },
        confirmButton = {},
        dismissButton = {},
        content = {
            item {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text("昵称、QQ号或UID", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                        }
                        inner()
                    },
                )
            }
            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                ) { Text("完成", fontSize = 11.sp) }
            }
        },
    )
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
