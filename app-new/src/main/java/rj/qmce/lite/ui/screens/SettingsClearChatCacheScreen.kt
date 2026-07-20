package rj.qmce.lite.ui.screens

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
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox

@Composable
fun SettingsClearChatCacheScreen(onConfirm: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            OfficialReportTargetBox(
                key = "settings-clear-cache:confirm",
                modifier = Modifier.fillMaxWidth(),
                elementId = OfficialReportBridge.ElementIds.EMPTY,
            ) { reportTarget ->
                EdgeButton(
                    onClick = {
                        OfficialReportBridge.reportElementClick(
                            target = reportTarget,
                            elementId = OfficialReportBridge.ElementIds.EMPTY,
                        )
                        onConfirm()
                    },
                    buttonSize = EdgeButtonSize.Small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError,
                    ),
                ) { Text("清理") }
            }
        },
        edgeButtonSpacing = 2.5.dp,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "settings-clear-cache-title") {
                Text(
                    "清理聊天缓存？",
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
            item(key = "settings-clear-cache-detail") {
                Text(
                    "图片、表情和其他聊天媒体会在需要时重新下载。",
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
