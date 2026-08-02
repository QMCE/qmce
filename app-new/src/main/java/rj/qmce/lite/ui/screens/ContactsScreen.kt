package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil3.compose.AsyncImage
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qqnt.avatar.IAvatarLoaderApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox
import rj.qmce.lite.fix.WatchAvatarViews
import rj.qmce.lite.ui.settingsVm
import rj.qmce.lite.ui.wear.QmceEmptyOrErrorState
import rj.qmce.lite.ui.wear.QmceListHeader
import rj.qmce.lite.ui.wear.QmceLoadingState
import rj.qmce.lite.viewmodel.ContactsViewModel
import java.io.File
import java.util.Locale

@OptIn(DelicateCoroutinesApi::class)
@Composable
fun ContactsScreen(
    vm: ContactsViewModel,
    onOpenChat: (String, String, String) -> Unit, // uid, uin, name
    onOpenGroup: (ContactsViewModel.UiGroup) -> Unit,
    onOpenProfile: (ContactsViewModel.UiBuddy) -> Unit,
    onRetryKernel: () -> Unit,
) {
    val categories by vm.categories.collectAsState()
    val groups by vm.groups.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val loading by vm.loading.collectAsState()
    val settings by settingsVm.settings.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val listState = rememberTransformingLazyColumnState()
    val scope = rememberCoroutineScope()
    val transformationSpec = rememberTransformationSpec()
    var showSearch by remember { mutableStateOf(false) }
    var showSortPicker by remember { mutableStateOf(false) }
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
    val firstVisibleItemIndex =
        listState.layoutInfo.visibleItems.firstOrNull()?.index ?: 0
    val showScrollToTop = firstVisibleItemIndex > 0

    LaunchedEffect(settings.contactsSortMode) {
        vm.setSortMode(settings.contactsSortMode)
    }

    if (showSortPicker) {
        ContactsSortPickerScreen(
            currentMode = settings.contactsSortMode,
            onSelect = { mode ->
                settingsVm.setContactsSortMode(mode)
                showSortPicker = false
            },
            onBack = { showSortPicker = false },
        )
        return
    }

    if (showSearch) {
        ContactSearchScreen(
            categories = categories,
            groups = groups,
            query = query,
            onQueryChange = { query = it },
            onOpenChat = { buddy ->
                showSearch = false
                query = ""
                onOpenChat(buddy.uid, buddy.uin.toString(), buddy.nick)
            },
            onOpenGroup = { group ->
                showSearch = false
                query = ""
                onOpenGroup(group)
            },
            onOpenProfile = onOpenProfile,
            onBack = {
                showSearch = false
                query = ""
            },
        )
        return
    }

    when {
        loading && categories.isEmpty() && groups.isEmpty() -> {
            QmceLoadingState(message = statusText.ifBlank { "加载联系人…" })
        }

        visibleCategories.isEmpty() && groups.isEmpty() -> {
            val isKernelIssue = !loading && statusText.contains("内核")
            QmceEmptyOrErrorState(
                message = statusText.ifBlank {
                    if (categories.isEmpty()) "暂无联系人" else "没有匹配联系人"
                },
                actionLabel = if (isKernelIssue) "重试" else null,
                onAction = if (isKernelIssue) onRetryKernel else null,
                isError = isKernelIssue,
            )
        }

        else -> {
            val listBody: @Composable BoxScope.(androidx.compose.foundation.layout.PaddingValues) -> Unit =
                { contentPadding ->
                    TransformingLazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                    ) {
                        if (showScrollToTop) {
                            item(key = "scroll-to-top") {
                                CompactButton(
                                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                                    modifier = Modifier.transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec),
                                    icon = {
                                        Icon(
                                            Icons.Default.ExpandLess,
                                            contentDescription = "滚动到顶部",
                                        )
                                    },
                                    label = { Text("回顶") },
                                )
                            }
                        }
                        item(key = "toolbar") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .graphicsLayer {
                                        with(SurfaceTransformation(transformationSpec)) {
                                            applyContainerTransformation()
                                            applyContentTransformation()
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                CompactButton(
                                    onClick = { showSearch = true },
                                    icon = {
                                        Icon(Icons.Default.Search, contentDescription = "搜索联系人")
                                    },
                                    label = { Text("搜索") },
                                )
                                CompactButton(
                                    onClick = { showSortPicker = true },
                                    icon = {
                                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                                    },
                                    label = { Text("排序") },
                                )
                            }
                        }
                        visibleCategories.forEach { category ->
                            item(key = "category:${category.id}") {
                                QmceListHeader(
                                    text = categoryHeaderText(category),
                                    modifier = Modifier.transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec),
                                )
                            }
                            category.buddies.forEach { buddy ->
                                item(key = "buddy:${buddy.categoryId}:${buddy.uid}") {
                                    val avatarModel = buddy.avatarPath
                                        .removePrefix("file://")
                                        .takeIf { it.isNotBlank() }
                                        ?.let(::File)
                                        ?.takeIf(File::isFile)
                                    OfficialReportTargetBox(
                                        key = "contact:${buddy.categoryId}:${buddy.uid}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .transformedHeight(this, transformationSpec),
                                        elementId = OfficialReportBridge.ElementIds.CONTACT_ENTRY,
                                        reuseIdentifier = buddy.uid,
                                    ) { reportTarget ->
                                        Button(
                                            onClick = {
                                                OfficialReportBridge.reportElementClick(
                                                    target = reportTarget,
                                                    elementId = OfficialReportBridge.ElementIds.CONTACT_ENTRY,
                                                    reuseIdentifier = buddy.uid,
                                                )
                                                onOpenChat(
                                                    buddy.uid,
                                                    buddy.uin.toString(),
                                                    buddy.nick,
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumVerticalContentPadding(
                                                    ButtonDefaults.minimumVerticalListContentPadding,
                                                )
                                                .padding(vertical = 2.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(),
                                            contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                                            transformation = SurfaceTransformation(transformationSpec),
                                            icon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(ButtonDefaults.LargeIconSize)
                                                        .background(scheme.surfaceContainer, CircleShape)
                                                        .clickable { onOpenProfile(buddy) },
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
                                                                WatchAvatarViews.create(context).also { avatarView ->
                                                                    runCatching {
                                                                        QRoute.api(IAvatarLoaderApi::class.java)
                                                                            .build(context)
                                                                            .target(avatarView)
                                                                            .loadAvatarByGroupCode(buddy.uin, GlobalScope)
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
                        if (groups.isNotEmpty()) {
                            item(key = "groups-header") {
                                QmceListHeader(
                                    text = "群聊",
                                    modifier = Modifier.transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec),
                                )
                            }
                            items(groups, key = { "group:${it.groupCode}" }) { group ->
                                Button(
                                    onClick = { onOpenGroup(group) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, transformationSpec)
                                        .minimumVerticalContentPadding(
                                            ButtonDefaults.minimumVerticalListContentPadding,
                                        )
                                        .padding(vertical = 2.dp),
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
                                                localAvatar = null,
                                                remoteAvatarUrls = listOf(group.avatarUrl),
                                                fallbackText = group.groupName.take(1).ifEmpty { "群" },
                                            )
                                        }
                                    },
                                    secondaryLabel = {
                                        Text(group.groupCode.toString(), maxLines = 1)
                                    },
                                ) {
                                    Text(
                                        group.groupName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            QmceScreenScaffold(
                scrollState = listState,
                content = listBody,
            )
        }
    }
}

private fun categoryHeaderText(category: ContactsViewModel.UiCategory): String {
    // onlineCount 来自 BuddyListCategory，表示该分组上报的在线人数，非单好友实时状态
    return if (category.onlineCount > 0) {
        "${category.name} · ${category.onlineCount}人在线"
    } else {
        "${category.name} (${category.buddies.size})"
    }
}

@Composable
private fun ContactsSortPickerScreen(
    currentMode: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val options = listOf(
        Triple("category", "按分组顺序", "沿用 QQ 好友分组的默认排列"),
        Triple("name", "按名称", "各分组内按备注/昵称字母排序"),
        Triple("online", "分组在线人数优先", "按分组上报的在线人数从多到少排列，非单好友状态"),
    )

    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "sort-title") {
                QmceListHeader(
                    text = "联系人排序",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ListHeaderDefaults.minimumTopListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            options.forEach { (mode, label, detail) ->
                item(key = "sort:$mode") {
                    RadioButton(
                        selected = currentMode == mode,
                        onSelect = { onSelect(mode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = { Text(label, maxLines = 1) },
                        secondaryLabel = {
                            Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
            }
        }
    }
}

private sealed class ContactSearchResult {
    abstract val key: String

    data class Buddy(
        override val key: String,
        val buddy: ContactsViewModel.UiBuddy,
    ) : ContactSearchResult()

    data class Group(
        override val key: String,
        val group: ContactsViewModel.UiGroup,
    ) : ContactSearchResult()
}

@Composable
private fun ContactSearchScreen(
    categories: List<ContactsViewModel.UiCategory>,
    groups: List<ContactsViewModel.UiGroup>,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenChat: (ContactsViewModel.UiBuddy) -> Unit,
    onOpenGroup: (ContactsViewModel.UiGroup) -> Unit,
    onOpenProfile: (ContactsViewModel.UiBuddy) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val scheme = MaterialTheme.colorScheme
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val matches = remember(categories, groups, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            val buddyMatches = categories.flatMapIndexed { categoryIndex, category ->
                category.buddies.mapIndexedNotNull { buddyIndex, buddy ->
                    val hit = listOf(buddy.nick, buddy.remark, buddy.uid, buddy.uin.toString())
                        .any { it.contains(normalizedQuery, ignoreCase = true) }
                    if (hit) {
                        ContactSearchResult.Buddy(
                            key = "search:buddy:$categoryIndex:${category.id}:$buddyIndex:${buddy.uid}",
                            buddy = buddy,
                        )
                    } else {
                        null
                    }
                }
            }
            val groupMatches = groups.mapNotNull { group ->
                val hit = listOf(group.groupName, group.groupCode.toString())
                    .any { it.contains(normalizedQuery, ignoreCase = true) }
                if (hit) {
                    ContactSearchResult.Group(
                        key = "search:group:${group.groupCode}",
                        group = group,
                    )
                } else {
                    null
                }
            }
            buddyMatches + groupMatches
        }
    }

    QmceScreenScaffold(scrollState = listState) { contentPadding ->
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
                                "昵称、备注、群名、QQ号或UID",
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
                        text = "输入关键词搜索好友或群聊",
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
                    when (result) {
                        is ContactSearchResult.Buddy -> {
                            val buddy = result.buddy
                            val avatarModel = buddy.avatarPath
                                .removePrefix("file://")
                                .takeIf { it.isNotBlank() }
                                ?.let(::File)
                                ?.takeIf(File::isFile)
                            OfficialReportTargetBox(
                                key = result.key,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec),
                                elementId = OfficialReportBridge.ElementIds.CONTACT_ENTRY,
                                reuseIdentifier = buddy.uid,
                            ) { reportTarget ->
                                Button(
                                    onClick = {
                                        OfficialReportBridge.reportElementClick(
                                            target = reportTarget,
                                            elementId = OfficialReportBridge.ElementIds.CONTACT_ENTRY,
                                            reuseIdentifier = buddy.uid,
                                        )
                                        onOpenChat(buddy)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                                    transformation = SurfaceTransformation(transformationSpec),
                                    icon = {
                                        Box(
                                            modifier = Modifier
                                                .size(ButtonDefaults.LargeIconSize)
                                                .background(scheme.surfaceContainer, CircleShape)
                                                .clickable { onOpenProfile(buddy) },
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

                        is ContactSearchResult.Group -> {
                            val group = result.group
                            Button(
                                onClick = { onOpenGroup(group) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .transformedHeight(this, transformationSpec),
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
                                            localAvatar = null,
                                            remoteAvatarUrls = listOf(group.avatarUrl),
                                            fallbackText = group.groupName.take(1).ifEmpty { "群" },
                                        )
                                    }
                                },
                                secondaryLabel = {
                                    Text("群聊 · ${group.groupCode}", maxLines = 1)
                                },
                            ) {
                                Text(
                                    group.groupName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
