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
import kotlin.math.ceil

/**
 * Screen adaptation metrics. Auto [densityScale] follows screen shortest side (~1.4–1.6×);
 * manual multiplier when auto is off. Screen edge insets match Wear Material 3
 * (≈5.2% horizontal / ≈10% vertical), scaled by user edge-safe settings.
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

private const val DESIGN_SHORTEST_DP = 192f
private const val AUTO_SCALE_BASE = 1.50f
private const val AUTO_SCALE_MIN = 1.40f
private const val AUTO_SCALE_MAX = 1.60f
const val MANUAL_SCALE_MIN = 0.75f
const val MANUAL_SCALE_MAX = 1.75f
private const val HORIZONTAL_INSET_PERCENT = 5.2f
private const val VERTICAL_INSET_PERCENT = 10f
private const val DENSITY_BOOST_THRESHOLD = 1.35f
private const val DENSITY_BOOST_MAX = 1.25f

/** Smaller watches get a higher multiplier for readability; clamped to [AUTO_SCALE_MIN]–[AUTO_SCALE_MAX]. */
fun computeAutoDensityScale(shortestScreenWidthDp: Int): Float {
    val shortest = shortestScreenWidthDp.coerceAtLeast(1).toFloat()
    return (AUTO_SCALE_BASE * (DESIGN_SHORTEST_DP / shortest))
        .coerceIn(AUTO_SCALE_MIN, AUTO_SCALE_MAX)
}

fun officialHorizontalInset(screenWidthDp: Int): Dp =
    ceil(screenWidthDp * HORIZONTAL_INSET_PERCENT / 100f).dp

fun officialVerticalInset(screenHeightDp: Int): Dp =
    ceil(screenHeightDp * VERTICAL_INSET_PERCENT / 100f).dp

fun officialScreenContentPadding(
    screenWidthDp: Int,
    screenHeightDp: Int,
): PaddingValues =
    PaddingValues(
        horizontal = officialHorizontalInset(screenWidthDp),
        vertical = officialVerticalInset(screenHeightDp),
    )

val QmceAdaptiveDefaults = QmceAdaptive(
    densityScale = AUTO_SCALE_BASE,
    listHorizontalPadding = officialHorizontalInset(192),
    screenContentPadding = officialScreenContentPadding(192, 192),
    edgeButtonSpacing = ScreenScaffoldDefaults.EdgeButtonSpacing,
    composerClearance = 72.dp,
    isRound = true,
)

fun buildQmceAdaptive(
    configuration: Configuration,
    autoScale: Boolean,
    manualScale: Float,
    edgeSafeEnabled: Boolean = true,
    edgeSafeScale: Float = 1.0f,
): QmceAdaptive {
    val shortestDp = configuration.smallestScreenWidthDp.coerceAtLeast(1)
    val isRound = configuration.isScreenRound
    val densityScale = if (autoScale) {
        computeAutoDensityScale(shortestDp)
    } else {
        manualScale.coerceIn(MANUAL_SCALE_MIN, MANUAL_SCALE_MAX)
    }
    val width = configuration.screenWidthDp.coerceAtLeast(1)
    val height = configuration.screenHeightDp.coerceAtLeast(1)
    val contentPadding = if (!edgeSafeEnabled) {
        PaddingValues(0.dp)
    } else {
        val scale = edgeSafeScale.coerceIn(0.25f, 1.5f)
        val densityBoost = if (densityScale > DENSITY_BOOST_THRESHOLD) {
            val t = ((densityScale - DENSITY_BOOST_THRESHOLD) / (MANUAL_SCALE_MAX - DENSITY_BOOST_THRESHOLD))
                .coerceIn(0f, 1f)
            1f + (DENSITY_BOOST_MAX - 1f) * t
        } else {
            1f
        }
        val h = ceil(width * HORIZONTAL_INSET_PERCENT / 100f * scale * densityBoost).dp
        val v = ceil(height * VERTICAL_INSET_PERCENT / 100f * scale).dp
        PaddingValues(horizontal = h, vertical = v)
    }
    return QmceAdaptive(
        densityScale = densityScale,
        listHorizontalPadding = contentPadding.calculateLeftPadding(
            androidx.compose.ui.unit.LayoutDirection.Ltr,
        ),
        screenContentPadding = contentPadding,
        edgeButtonSpacing = ScreenScaffoldDefaults.EdgeButtonSpacing,
        composerClearance = if (isRound) 78.dp else 72.dp,
        isRound = isRound,
    )
}

@Composable
fun rememberQmceAdaptive(
    autoScale: Boolean,
    manualScale: Float,
    edgeSafeEnabled: Boolean = true,
    edgeSafeScale: Float = 1.0f,
): QmceAdaptive {
    val configuration = LocalConfiguration.current
    return remember(
        configuration.smallestScreenWidthDp,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.isScreenRound,
        autoScale,
        manualScale,
        edgeSafeEnabled,
        edgeSafeScale,
    ) {
        buildQmceAdaptive(
            configuration = configuration,
            autoScale = autoScale,
            manualScale = manualScale,
            edgeSafeEnabled = edgeSafeEnabled,
            edgeSafeScale = edgeSafeScale,
        )
    }
}
