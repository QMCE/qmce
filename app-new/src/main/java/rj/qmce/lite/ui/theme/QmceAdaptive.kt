package rj.qmce.lite.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffoldDefaults

/**
 * Screen adaptation metrics derived from watch shortest-side dp.
 * Consumed by [QmceTheme] (density) and scaffolds (padding / EdgeButton spacing).
 */
@Immutable
data class QmceAdaptive(
    val densityScale: Float,
    val listHorizontalPadding: Dp,
    val screenContentPadding: PaddingValues,
    val edgeButtonSpacing: Dp,
    /** Bottom clearance for floating chrome above an EdgeButton. */
    val composerClearance: Dp,
    val isRound: Boolean,
)

val LocalQmceAdaptive = staticCompositionLocalOf { QmceAdaptiveDefaults }

val QmceAdaptiveDefaults = QmceAdaptive(
    densityScale = 1.50f,
    listHorizontalPadding = 10.dp,
    screenContentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    edgeButtonSpacing = ScreenScaffoldDefaults.EdgeButtonSpacing,
    composerClearance = 72.dp,
    isRound = true,
)

private const val DESIGN_SHORTEST_DP = 192f
private const val AUTO_SCALE_BASE = 1.50f
private const val AUTO_SCALE_MIN = 1.40f
private const val AUTO_SCALE_MAX = 1.60f
private const val MANUAL_SCALE_MIN = 0.75f
private const val MANUAL_SCALE_MAX = 2.0f

fun computeAutoDensityScale(shortestScreenWidthDp: Int): Float {
    val shortest = shortestScreenWidthDp.coerceAtLeast(1).toFloat()
    // 屏越小倍率越高，贴近旧 1.6 可读性
    return (AUTO_SCALE_BASE * (DESIGN_SHORTEST_DP / shortest))
        .coerceIn(AUTO_SCALE_MIN, AUTO_SCALE_MAX)
}

fun buildQmceAdaptive(
    configuration: Configuration,
    autoScale: Boolean,
    manualScale: Float,
): QmceAdaptive {
    val shortestDp = configuration.smallestScreenWidthDp.coerceAtLeast(1)
    val isRound = configuration.isScreenRound
    val densityScale = if (autoScale) {
        computeAutoDensityScale(shortestDp)
    } else {
        manualScale.coerceIn(MANUAL_SCALE_MIN, MANUAL_SCALE_MAX)
    }
    val horizontal = if (isRound) 12.dp else 10.dp
    val vertical = if (isRound) 8.dp else 6.dp
    return QmceAdaptive(
        densityScale = densityScale,
        listHorizontalPadding = horizontal,
        screenContentPadding = PaddingValues(horizontal = horizontal, vertical = vertical),
        edgeButtonSpacing = if (isRound) 18.dp else ScreenScaffoldDefaults.EdgeButtonSpacing,
        composerClearance = if (isRound) 78.dp else 72.dp,
        isRound = isRound,
    )
}

@Composable
fun rememberQmceAdaptive(
    autoScale: Boolean,
    manualScale: Float,
): QmceAdaptive {
    val configuration = LocalConfiguration.current
    return remember(
        configuration.smallestScreenWidthDp,
        configuration.isScreenRound,
        autoScale,
        manualScale,
    ) {
        buildQmceAdaptive(configuration, autoScale, manualScale)
    }
}
