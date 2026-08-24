package rj.qmce.lite.data.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import rj.qmce.lite.util.QmceLog
import rj.qmce.lite.viewmodel.SettingsViewModel

object OtaNetworkGate {
    private const val TAG = "QmceOta"

    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun requireWifiEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        return prefs.getBoolean(SettingsViewModel.KEY_OTA_REQUIRE_WIFI, true)
    }

    /** True if OTA work may proceed (pref off or on Wi‑Fi). */
    fun mayProceed(context: Context): Boolean {
        if (!requireWifiEnabled(context)) {
            QmceLog.d(TAG, "wifi gate skipped; requireWifi=false")
            return true
        }
        val ok = isOnWifi(context)
        if (!ok) QmceLog.w(TAG, "wifi gate blocked; not on Wi‑Fi")
        return ok
    }

    fun openWifiSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
        QmceLog.e(TAG, "failed to open Wi‑Fi/settings")
    }
}
