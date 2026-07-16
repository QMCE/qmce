package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
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
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
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

    if (showSearch) {
        ContactSearchScreen(
            categories = categories,
            query = query,
            onQueryChange = { query = it },
            onOpenChat = { buddy ->
                showSearch = false
                query = ""
                onOpenChat(buddy.uid, buddy.uin.toString(), buddy.nick)
            },
            onBack = {
                showSearch = false
                query = ""
            },
        )
        return
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        when {
            loading && categories.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            visibleCategories.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (categories.isEmpty()) "暂无联系人" else "没有匹配联系人",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.outline,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            else -> {
                TransformingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    item(key = "search") {
                        CompactButton(
                            onClick = { showSearch = true },
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "搜索联系人")
                            // Text("搜索联系人")
                        }
                    }
                    visibleCategories.forEach { category ->
                        item(key = "category:${category.id}") {
                            // 分组标题
                            val transformation = SurfaceTransformation(transformationSpec)
                            Text(
                                text = category.name + " (${category.buddies.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .graphicsLayer {
                                        with(transformation) {
                                            applyContainerTransformation()
                                            applyContentTransformation()
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                        category.buddies.forEach { buddy ->
                            item(key = "buddy:${buddy.categoryId}:${buddy.uid}") {
                                val avatarModel = buddy.avatarPath
                                    .removePrefix("file://")
                                    .takeIf { it.isNotBlank() }
                                    ?.let(::File)
                                    ?.takeIf(File::isFile)
                                Button(
                                    onClick = {
                                        onOpenChat(
                                            buddy.uid,
                                            buddy.uin.toString(),
                                            buddy.nick
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, transformationSpec)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                                    transformation = SurfaceTransformation(transformationSpec),
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
                                                        WatchAvatarView(
                                                            context,
                                                            null
                                                        ).also { avatarView ->
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
    }

}

private data class ContactSearchResult(
    val key: String,
    val buddy: ContactsViewModel.UiBuddy,
)

@Composable
private fun ContactSearchScreen(
    categories: List<ContactsViewModel.UiCategory>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenChat: (ContactsViewModel.UiBuddy) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val scheme = MaterialTheme.colorScheme
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val matches = remember(categories, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            categories.flatMapIndexed { categoryIndex, category ->
                category.buddies.mapIndexedNotNull { buddyIndex, buddy ->
                    val matches = listOf(buddy.nick, buddy.remark, buddy.uid, buddy.uin.toString())
                        .any { it.contains(normalizedQuery, ignoreCase = true) }
                    if (matches) {
                        ContactSearchResult(
                            key = "search:$categoryIndex:${category.id}:$buddyIndex:${buddy.uid}",
                            buddy = buddy,
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "search-input") {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .background(scheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                "昵称、备注、QQ号或UID",
                                color = scheme.outline,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        inner()
                    },
                )
            }
            when {
                normalizedQuery.isBlank() -> item(key = "search-hint") {
                    SearchPageHint(
                        text = "输入关键词搜索联系人",
                        transformationSpec = transformationSpec,
                    )
                }

                matches.isEmpty() -> item(key = "search-empty") {
                    SearchPageHint(
                        text = "没有匹配联系人",
                        transformationSpec = transformationSpec,
                    )
                }

                else -> items(matches, key = { it.key }) { result ->
                    val buddy = result.buddy
                    val avatarModel = buddy.avatarPath
                        .removePrefix("file://")
                        .takeIf { it.isNotBlank() }
                        ?.let(::File)
                        ?.takeIf(File::isFile)
                    Button(
                        onClick = { onOpenChat(buddy) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                        transformation = SurfaceTransformation(transformationSpec),
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
                            }
                        },
                        secondaryLabel = {
                            Text(
                                buddy.categoryName.ifBlank { buddy.uin.toString() },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    ) {
                        Text(
                            buddy.remark.ifEmpty { buddy.nick },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.SearchPageHint(
    text: String,
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        color = scheme.outline,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, transformationSpec)
            .graphicsLayer {
                with(SurfaceTransformation(transformationSpec)) {
                    applyContainerTransformation()
                    applyContentTransformation()
                }
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
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
