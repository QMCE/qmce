package rj.qmce.lite.ui.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.ui.theme.LocalQmceAdaptive

/**
 * Standard Wear M3 confirmation screen: title + detail + EdgeButton.
 *
 * @param destructive tints the confirm button with the error color scheme; ignored if
 *   [confirmColors] is provided.
 * @param confirmEnabled disables the confirm button (e.g. while a request is in flight).
 * @param backEnabled disables swipe-to-dismiss / back navigation (e.g. while processing).
 * @param confirmColors overrides the confirm button colors, taking priority over [destructive].
 */
@Composable
fun QmceConfirmScreen(
    title: String,
    detail: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    backEnabled: Boolean = true,
    confirmColors: ButtonColors? = null,
) {
    BackHandler(enabled = backEnabled, onBack = onBack)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme
    QmceScreenScaffold(
        scrollState = listState,
        edgeButtonSpacing = LocalQmceAdaptive.current.edgeButtonSpacing,
        edgeButton = {
            EdgeButton(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
                enabled = confirmEnabled,
                buttonSize = EdgeButtonSize.Medium,
                colors = confirmColors ?: if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "confirm-title") {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            item(key = "confirm-detail") {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
        }
    }
}
