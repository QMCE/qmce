package rj.qmce.lite.ui.theme

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigation.NavController
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import rj.qmce.lite.AppConfig

@Composable
fun QmceTheme(
    navController: NavController? = null,
    autoScale: Boolean = true,
    manualScale: Float = 1.0f,
    fontScale: Float = 1.0f,
    edgeSafeAreaEnabled: Boolean = true,
    edgeSafeAreaScale: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val deviceDensity = LocalDensity.current
    val adaptive = rememberQmceAdaptive(
        autoScale = autoScale,
        manualScale = manualScale,
        fontScale = fontScale,
        edgeSafeEnabled = edgeSafeAreaEnabled,
        edgeSafeScale = edgeSafeAreaScale,
    )
    val colorScheme = remember(context) {
        dynamicColorScheme(context) ?: QmceBlueColorScheme
    }
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
    // Apply adaptive.densityScale (layout) and fontScale (text) as separate
    // channels; list focus scaling stays separate via TransformationSpec.
    val resolvedDensity = if (adaptive.densityScale == 1.0f && adaptive.fontScale == 1.0f) {
        deviceDensity
    } else {
        Density(
            density = deviceDensity.density * adaptive.densityScale,
            fontScale = deviceDensity.fontScale * adaptive.fontScale,
        )
    }
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalQmceAdaptive provides adaptive,
            LocalDensity provides resolvedDensity,
        ) {
            content()
        }
    }
}
