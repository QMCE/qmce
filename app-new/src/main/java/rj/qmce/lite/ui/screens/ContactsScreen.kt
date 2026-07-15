package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
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
        if (loading && categories.isEmpty()) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            return
        }

        if (visibleCategories.isEmpty()) {
            Text(
                if (categories.isEmpty()) "暂无联系人" else "没有匹配联系人",
                style = MaterialTheme.typography.bodySmall,
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

        androidx.wear.compose.foundation.lazy.TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 4.dp),
        ) {
            item {
                androidx.wear.compose.material3.Button(
                    onClick = { showSearch = true }
                ) {
                    Icon(Icons.Default.Search, contentDescription = "搜索联系人")
                    Text("搜索联系人")
                }
            }
            visibleCategories.forEach { category ->
                item {
                    // 分组标题
                    Text(
                        text = category.name + " (${category.buddies.size})",
                        style = MaterialTheme.typography.titleSmall,
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
                                secondaryContentColor = scheme.onSurfaceVariant,
                            ),
                            contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(ButtonDefaults.LargeIconSize)
                                        .background(scheme.surfaceContainer, CircleShape),
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
                            },
                            secondaryLabel = { Text(buddy.uin.toString(), maxLines = 1) },
                        ) { Text(buddy.remark.ifEmpty { buddy.nick }, maxLines = 1) }
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
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text("昵称、QQ号或UID", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
                        }
                        inner()
                    },
                )
            }
            item {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                ) { Text("完成") }
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
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
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
