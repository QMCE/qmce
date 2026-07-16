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
import rj.qmce.lite.viewmodel.QZoneViewModel

@Composable
fun QZoneDeleteConfirmationScreen(
    deleteState: QZoneViewModel.DeleteState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val processing = deleteState is QZoneViewModel.DeleteState.Submitting ||
        deleteState is QZoneViewModel.DeleteState.Refreshing
    val confirmed = deleteState is QZoneViewModel.DeleteState.Confirmed
    BackHandler(enabled = !processing) {
        if (confirmed) onDeleted() else onDismiss()
    }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme
    val buttonLabel = when (deleteState) {
        QZoneViewModel.DeleteState.Idle,
        QZoneViewModel.DeleteState.Submitting,
        QZoneViewModel.DeleteState.Refreshing -> "删除动态"
        QZoneViewModel.DeleteState.Confirmed -> "完成"
        QZoneViewModel.DeleteState.Unconfirmed -> "关闭"
        is QZoneViewModel.DeleteState.Failed -> "重试"
    }
    val onEdgeButtonClick = when (deleteState) {
        QZoneViewModel.DeleteState.Confirmed -> onDeleted
        QZoneViewModel.DeleteState.Unconfirmed -> onDismiss
        else -> onConfirm
    }
    val status = when (deleteState) {
        QZoneViewModel.DeleteState.Idle -> "删除后无法恢复。提交后会刷新动态流确认结果。"
        QZoneViewModel.DeleteState.Submitting -> "正在提交删除请求…"
        QZoneViewModel.DeleteState.Refreshing -> "删除请求已提交，正在刷新确认…"
        QZoneViewModel.DeleteState.Confirmed -> "已确认这条动态不再出现在当前动态流中。"
        QZoneViewModel.DeleteState.Unconfirmed -> "请求已提交，但暂未能从动态流确认；请稍后手动刷新。"
        is QZoneViewModel.DeleteState.Failed -> "删除失败：${deleteState.message}"
    }
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onEdgeButtonClick,
                enabled = !processing,
                buttonSize = EdgeButtonSize.Small,
                colors = if (confirmed) {
                    ButtonDefaults.filledTonalButtonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError,
                    )
                },
            ) { Text(buttonLabel) }
        },
        edgeButtonSpacing = 2.5.dp,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "qzone-delete-title") {
                Text(
                    "删除这条动态？",
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
            item(key = "qzone-delete-detail") {
                Text(
                    status,
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
