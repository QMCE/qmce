package rj.qmce.lite.notify

import android.content.ComponentName
import android.content.Context
import android.util.Log
import rj.qmce.lite.util.QmceDevice
import rj.qmce.lite.viewmodel.SettingsViewModel

/** Best-effort refresh hooks for Wear complications / tiles. */
object QmceWearSurfaces {
    private const val TAG = "QmceWearSurfaces"

    fun requestDataRefresh(context: Context) {
        if (!QmceDevice.isWear(context)) return
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val complicationsOn = prefs.getBoolean(SettingsViewModel.KEY_WEAR_COMPLICATIONS, true)
        val tilesOn = prefs.getBoolean(SettingsViewModel.KEY_WEAR_TILES, false)
        if (!complicationsOn && !tilesOn) return

        if (complicationsOn) {
            runCatching {
                val requesterClass = Class.forName(
                    "androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester",
                )
                val create = requesterClass.methods.firstOrNull {
                    it.name == "create" && it.parameterTypes.size == 2
                }
                val providers = listOf(
                    "rj.qmce.lite.wear.QmceLaunchComplicationService",
                    "rj.qmce.lite.wear.QmceLatestMessageComplicationService",
                    "rj.qmce.lite.wear.QmcePinnedMessageComplicationService",
                )
                for (name in providers) {
                    runCatching {
                        val cn = ComponentName(app, name)
                        val requester = create?.invoke(null, app, cn) ?: return@runCatching
                        requesterClass.getMethod("requestUpdateAll").invoke(requester)
                    }
                }
            }.onFailure { Log.d(TAG, "complication update skipped: ${it.message}") }
        }

        if (tilesOn) {
            runCatching {
                val tileClass = Class.forName("androidx.wear.tiles.TileService")
                val getUpdater = tileClass.getMethod("getUpdater", Context::class.java)
                val updater = getUpdater.invoke(null, app)
                val requestUpdate = updater.javaClass.methods.firstOrNull {
                    it.name == "requestUpdate" && it.parameterTypes.size == 1
                }
                requestUpdate?.invoke(
                    updater,
                    Class.forName("rj.qmce.lite.wear.QmceWatchlistTileService"),
                )
            }.onFailure { Log.d(TAG, "tile update skipped: ${it.message}") }
        }
    }
}
