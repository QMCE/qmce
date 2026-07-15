package rj.qmce.lite

import android.os.Build

object AppConfig {
    @Suppress("SpellCheckingInspection")
    val isMiWatch5Mode: Boolean by lazy {
        Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) &&
                (Build.MODEL.trim().equals("M2505W1", ignoreCase = true) ||
                        Build.MODEL.trim().equals("M2501W1", ignoreCase = true))
    }
}
