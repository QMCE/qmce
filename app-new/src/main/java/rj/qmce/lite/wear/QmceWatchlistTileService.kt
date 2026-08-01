package rj.qmce.lite.wear

import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import rj.qmce.lite.notify.QmceRecentContactText
import rj.qmce.lite.viewmodel.SettingsViewModel

class QmceWatchlistTileService : TileService() {
    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val prefs = getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, MODE_PRIVATE)
        val enabled = prefs.getBoolean(SettingsViewModel.KEY_WEAR_TILES, true)
        val loggedIn = runCatching {
            QmceApplication.ensureRuntime()?.isLogin() == true
        }.getOrDefault(false)
        val entries = if (enabled) {
            QmceWatchlistStore.load(this).filter { it.chatType == 2 }
        } else {
            emptyList()
        }
        val recent = KernelBridge.getRecentContactService()
        val cache = recent?.let {
            runCatching { SdkCompat.getRecentContactFromCache(it, 0) }.getOrNull()
        }.orEmpty()

        val state = when {
            !loggedIn -> QmceWatchlistTileLayouts.TileState.LOGGED_OUT
            !enabled -> QmceWatchlistTileLayouts.TileState.DISABLED
            entries.isEmpty() -> QmceWatchlistTileLayouts.TileState.EMPTY
            else -> QmceWatchlistTileLayouts.TileState.DATA
        }
        val rows = entries.map { entry ->
            val contact = cache.firstOrNull {
                it.chatType == 2 &&
                    (it.peerUid == entry.peerUid || it.peerUin == entry.peerUin)
            }
            QmceWatchlistTileLayouts.RowData(
                name = entry.name,
                abstract = contact?.let(QmceRecentContactText::abstractText) ?: "无摘要",
                peerUid = entry.peerUid,
                peerUin = entry.peerUin,
                chatType = entry.chatType,
            )
        }
        val root = runCatching {
            QmceWatchlistTileLayouts.buildRoot(
                context = this,
                deviceParameters = requestParams.deviceConfiguration,
                state = state,
                rows = rows,
            )
        }.getOrElse {
            QmceWatchlistTileLayouts.buildRoot(
                context = this,
                deviceParameters = requestParams.deviceConfiguration,
                state = QmceWatchlistTileLayouts.TileState.ERROR,
                rows = emptyList(),
            )
        }
        val layout = androidx.wear.protolayout.LayoutElementBuilders.Layout.Builder()
            .setRoot(root)
            .build()
        val entry = TimelineBuilders.TimelineEntry.Builder()
            .setLayout(layout)
            .build()
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(entry)
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("m3-1")
            .setTileTimeline(timeline)
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(requestParams.version)
            .build()
        return Futures.immediateFuture(resources)
    }
}
