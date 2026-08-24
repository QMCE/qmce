package rj.qmce.lite.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

object QmceDevice {
    fun isWear(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) return true
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiMode == Configuration.UI_MODE_TYPE_WATCH
    }
}
