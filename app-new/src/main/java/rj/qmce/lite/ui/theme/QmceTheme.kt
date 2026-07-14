package rj.qmce.lite.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun QmceTheme(content: @Composable () -> Unit) {
    val deviceDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = deviceDensity.density * 1.5f,
            fontScale = deviceDensity.fontScale,
        ),
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
    ) {
        MaterialTheme(
            colorScheme = ColorScheme(),
        ) {
            content()
        }
    }
}
