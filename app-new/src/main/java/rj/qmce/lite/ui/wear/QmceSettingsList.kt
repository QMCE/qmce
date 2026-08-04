package rj.qmce.lite.ui.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text

@Composable
fun SettingsListHeader(
    text: String,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
) {
    ListHeader(
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
    ) {
        Text(text)
    }
}

@Composable
fun SettingsSubHeader(
    text: String,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
) {
    ListSubHeader(
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
    ) {
        Text(text)
    }
}

@Composable
fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondaryLabel: String? = null,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        label = { Text(label) },
        secondaryLabel = secondaryLabel?.let { text ->
            {
                Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}

@Composable
fun SettingsNavButton(
    title: String,
    onClick: () -> Unit,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = ButtonDefaults.filledTonalButtonColors(),
        contentPadding = if (icon != null) {
            ButtonDefaults.ButtonWithLargeIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        },
        icon = icon?.let { image ->
            { Icon(image, contentDescription = null) }
        },
        secondaryLabel = subtitle?.let { text ->
            {
                Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
    ) {
        Text(title, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun SettingsInfoButton(
    title: String,
    subtitle: String,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = {},
        enabled = false,
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        secondaryLabel = {
            Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
    ) {
        Text(title, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
