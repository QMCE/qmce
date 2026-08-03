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
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeaderDefaults
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.notify.QmceWearSurfaces
import rj.qmce.lite.ui.wear.QmceListHeader
import rj.qmce.lite.wear.QmcePinnedComplicationStore
import rj.qmce.lite.wear.QmceWatchlistStore
import rj.qmce.lite.wear.PinnedChat
import rj.qmce.lite.wear.WatchlistEntry

@Composable
fun TileWatchlistScreen(
    recentChats: List<WatchlistEntry>,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val context = LocalContext.current
    val allWatchlist by QmceWatchlistStore.entries.collectAsState()
    val watchlist = remember(allWatchlist) { allWatchlist }
    var pinned by remember {
        mutableStateOf(QmcePinnedComplicationStore.load(context))
    }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LaunchedEffect(Unit) {
        QmceWatchlistStore.load(context)
    }
    LifecycleResumeEffect(Unit) {
        QmceWatchlistStore.load(context)
        onPauseOrDispose { }
    }

    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "watchlist-header") {
                QmceListHeader(
                    text = "Tile 关注会话",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ListHeaderDefaults.minimumTopListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "watchlist-hint") {
                Text(
                    "最多 ${QmceWatchlistStore.MAX_ENTRIES} 个。点选最近会话可关注，并固定到表盘组件。",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                )
            }
            if (watchlist.isEmpty()) {
                item(key = "watchlist-empty") {
                    Text(
                        "尚未添加群聊",
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            }
            watchlist.forEach { entry ->
                item(key = "wl-${entry.chatType}-${entry.peerUid}") {
                    Button(
                        onClick = {
                            QmceWatchlistStore.remove(context, entry.peerUid, entry.chatType)
                            QmceWearSurfaces.requestDataRefresh(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        secondaryLabel = { Text("点击移除") },
                    ) {
                        Text(entry.name, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
            item(key = "add-header") {
                QmceListHeader(
                    text = "从最近会话添加",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            recentChats.forEach { entry ->
                item(key = "rc-${entry.chatType}-${entry.peerUid}") {
                    val inList = watchlist.any {
                        it.peerUid == entry.peerUid && it.chatType == entry.chatType
                    }
                    val isPinned = pinned?.peerUid == entry.peerUid &&
                        pinned?.chatType == entry.chatType
                    Button(
                        onClick = {
                            if (!inList) {
                                QmceWatchlistStore.add(context, entry)
                            }
                            QmcePinnedComplicationStore.save(
                                context,
                                PinnedChat(entry.peerUid, entry.chatType, entry.name),
                            )
                            pinned = QmcePinnedComplicationStore.load(context)
                            QmceWearSurfaces.requestDataRefresh(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        secondaryLabel = {
                            Text(
                                buildString {
                                    append(if (entry.chatType == 2) "群" else "好友")
                                    if (inList) append(" · 已关注")
                                    if (isPinned) append(" · 已固定表盘")
                                    else append(" · 点按关注并固定")
                                },
                            )
                        },
                    ) {
                        Text(entry.name, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}
