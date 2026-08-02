package rj.qmce.lite.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ListSubHeader
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import mqq.app.AppRuntime
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import rj.qmce.lite.notify.QmceWearSurfaces
import rj.qmce.lite.ui.wear.QmceLoadingState
import rj.qmce.lite.viewmodel.ContactsViewModel
import rj.qmce.lite.wear.PinnedChat
import rj.qmce.lite.wear.QmcePinnedComplicationStore
import rj.qmce.lite.wear.QmceWatchlistStore
import rj.qmce.lite.wear.WatchlistEntry

@Composable
fun TileGroupPickerScreen(
    @Suppress("UNUSED_PARAMETER") runtime: AppRuntime?,
    contactsVm: ContactsViewModel,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val context = LocalContext.current
    val groups by contactsVm.groups.collectAsState()
    val groupsLoading by contactsVm.groupsLoading.collectAsState()
    val groupsError by contactsVm.groupsError.collectAsState()
    var watchlist by remember {
        mutableStateOf(QmceWatchlistStore.load(context).filter { it.chatType == 2 })
    }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val recentContacts = remember {
        val recent = KernelBridge.getRecentContactService()
        runCatching { recent?.let { SdkCompat.getRecentContactFromCache(it, 0) } }
            .getOrNull()
            .orEmpty()
            .filter { it.chatType == 2 }
    }
    val recentGroups = remember(recentContacts) {
        recentContacts.mapNotNull { contact ->
            val code = contact.peerUin.takeIf { it > 0L }
                ?: contact.peerUid?.toLongOrNull()?.takeIf { it > 0L }
                ?: return@mapNotNull null
            ContactsViewModel.UiGroup(
                groupCode = code,
                groupName = contact.peerName?.takeIf { it.isNotBlank() }
                    ?: contact.remark?.takeIf { it.isNotBlank() }
                    ?: code.toString(),
                memberCount = 0,
                avatarUrl = contact.avatarUrl.orEmpty(),
            )
        }.distinctBy { it.groupCode }
    }
    val allGroups = remember(recentGroups, groups) {
        val seen = LinkedHashSet<Long>()
        buildList {
            recentGroups.forEach { g ->
                if (seen.add(g.groupCode)) add(g)
            }
            groups.forEach { g ->
                if (seen.add(g.groupCode)) add(g)
            }
        }
    }
    val showFullScreenLoading = allGroups.isEmpty() && groupsLoading

    LaunchedEffect(Unit) {
        contactsVm.ensureGroupsLoaded()
    }

    fun resolvePeerUid(groupCode: Long): String {
        val fromRecent = recentContacts.firstOrNull { it.peerUin == groupCode }?.peerUid
        return fromRecent?.takeIf { it.isNotBlank() } ?: groupCode.toString()
    }

    fun persist(entries: List<WatchlistEntry>) {
        val others = QmceWatchlistStore.load(context).filter { it.chatType != 2 }
        val selectedGroups = entries.take(QmceWatchlistStore.MAX_ENTRIES)
        QmceWatchlistStore.save(context, others + selectedGroups)
        watchlist = QmceWatchlistStore.load(context).filter { it.chatType == 2 }
        val first = selectedGroups.firstOrNull()
        if (first != null) {
            QmcePinnedComplicationStore.save(
                context,
                PinnedChat(first.peerUid, first.chatType, first.name),
            )
        }
        QmceWearSurfaces.requestDataRefresh(context)
    }

    if (showFullScreenLoading) {
        QmceLoadingState(message = "加载群列表…")
        return
    }

    QmceScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "picker-header") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ListHeaderDefaults.minimumTopListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text("Tile 群聊")
                }
            }
            item(key = "picker-hint") {
                ListSubHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text("最多 ${QmceWatchlistStore.MAX_ENTRIES} 个")
                }
            }
            if (watchlist.isNotEmpty()) {
                item(key = "sec-selected") {
                    ListSubHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("已选取")
                    }
                }
                items(watchlist, key = { "sel-${it.peerUin}-${it.peerUid}" }) { entry ->
                    SwitchButton(
                        checked = true,
                        onCheckedChange = {
                            persist(watchlist.filterNot {
                                it.peerUin == entry.peerUin || it.peerUid == entry.peerUid
                            })
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ButtonDefaults.minimumVerticalListContentPadding,
                            ),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = {
                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        secondaryLabel = { Text("点击取消选取") },
                    )
                }
            }
            if (recentGroups.isNotEmpty()) {
                item(key = "sec-recent") {
                    ListSubHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("最近群")
                    }
                }
                items(recentGroups, key = { "rg-${it.groupCode}" }) { group ->
                    GroupPickSwitch(
                        group = group,
                        watchlist = watchlist,
                        transformationSpec = transformationSpec,
                        resolvePeerUid = ::resolvePeerUid,
                        onPersist = ::persist,
                    )
                }
            }
            item(key = "sec-all") {
                ListSubHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(
                        when {
                            groupsLoading && groups.isEmpty() -> "全部群（加载中…）"
                            groupsLoading -> "全部群（更新中…）"
                            else -> "全部群"
                        },
                    )
                }
            }
            if (groups.isEmpty() && !groupsLoading) {
                item(key = "empty-groups") {
                    Button(
                        onClick = { contactsVm.refreshGroups() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ButtonDefaults.minimumVerticalListContentPadding,
                            ),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        secondaryLabel = {
                            Text(
                                groupsError ?: "请同步群列表",
                                maxLines = 2,
                            )
                        },
                    ) {
                        Text("刷新群列表")
                    }
                }
            } else {
                val fullOnly = groups.filter { g ->
                    recentGroups.none { it.groupCode == g.groupCode }
                }
                items(fullOnly, key = { "g-${it.groupCode}" }) { group ->
                    GroupPickSwitch(
                        group = group,
                        watchlist = watchlist,
                        transformationSpec = transformationSpec,
                        resolvePeerUid = ::resolvePeerUid,
                        onPersist = ::persist,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.GroupPickSwitch(
    group: ContactsViewModel.UiGroup,
    watchlist: List<WatchlistEntry>,
    transformationSpec: TransformationSpec,
    resolvePeerUid: (Long) -> String,
    onPersist: (List<WatchlistEntry>) -> Unit,
) {
    val selected = watchlist.any {
        it.peerUin == group.groupCode ||
            it.peerUid == group.groupCode.toString()
    }
    val atLimit = !selected && watchlist.size >= QmceWatchlistStore.MAX_ENTRIES
    SwitchButton(
        checked = selected,
        onCheckedChange = { checked ->
            if (checked) {
                if (atLimit) return@SwitchButton
                val entry = WatchlistEntry(
                    peerUid = resolvePeerUid(group.groupCode),
                    peerUin = group.groupCode,
                    chatType = 2,
                    name = group.groupName,
                )
                onPersist(
                    (watchlist.filterNot {
                        it.peerUin == group.groupCode
                    } + entry).take(QmceWatchlistStore.MAX_ENTRIES),
                )
            } else {
                onPersist(
                    watchlist.filterNot {
                        it.peerUin == group.groupCode ||
                            it.peerUid == group.groupCode.toString()
                    },
                )
            }
        },
        enabled = selected || !atLimit,
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, transformationSpec)
            .minimumVerticalContentPadding(
                ButtonDefaults.minimumVerticalListContentPadding,
            ),
        transformation = SurfaceTransformation(transformationSpec),
        label = {
            Text(group.groupName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = {
            Text(
                when {
                    selected -> "已选取"
                    atLimit -> "已达上限"
                    group.memberCount > 0 -> "${group.memberCount} 人"
                    else -> "最近会话"
                },
                maxLines = 1,
            )
        },
    )
}
