package rj.qmce.lite.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

/**
 * Fixed Material Blue fallback when Wear dynamic color is unavailable or disabled.
 * Surfaces keep the dark Wear structure; primary family uses a blue seed.
 */
val QmceBlueColorScheme = ColorScheme(
    primary = Color(0xFFADC6FF),
    primaryDim = Color(0xFF8AB4F8),
    primaryContainer = Color(0xFF004A77),
    onPrimary = Color(0xFF002E69),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFB8C8E8),
    secondaryDim = Color(0xFF9CB0D4),
    secondaryContainer = Color(0xFF3B4858),
    onSecondary = Color(0xFF233148),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFF9BCAFF),
    tertiaryDim = Color(0xFF7BB4F0),
    tertiaryContainer = Color(0xFF004C6E),
    onTertiary = Color(0xFF00344D),
    onTertiaryContainer = Color(0xFFCDE5FF),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE6E1E5),
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFFF897D),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)
