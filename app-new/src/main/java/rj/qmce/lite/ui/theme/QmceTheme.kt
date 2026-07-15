package rj.qmce.lite.ui.theme

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigation.NavController
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import rj.qmce.lite.AppConfig

@Composable
fun QmceTheme(
    navController: NavController? = null,
    autoScale: Boolean = true,
    manualScale: Float = 1.1f,
    content: @Composable () -> Unit,
) {
    val deviceDensity = LocalDensity.current
    val navBackStackEntry by navController?.currentBackStackEntryAsState()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) }
    val canGoBack = remember(navBackStackEntry) {
        navController?.previousBackStackEntry != null
    }
    val backHandlerEnabled = remember(canGoBack) {
        canGoBack && (AppConfig.isMiWatch5Mode || Build.VERSION.SDK_INT < 35)
    }
    if (navController != null) {
        BackHandler(enabled = backHandlerEnabled && AppConfig.isMiWatch5Mode) {
            navController.popBackStack()
        }
    }
    MaterialTheme {
        if (autoScale) {
            content()
        } else {
            val scale = manualScale.coerceIn(MIN_SCALE, MAX_SCALE)
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = deviceDensity.density * scale,
                    fontScale = deviceDensity.fontScale,
                ),
            ) {
                content()
            }
        }
    }
}

private const val MIN_SCALE = 0.75f
private const val MAX_SCALE = 2.0f
