package rj.qmce.lite.ui.screens

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ButtonDefaults
import rj.qmce.lite.ui.wear.QmceConfirmScreen
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
    QmceConfirmScreen(
        title = "删除这条动态？",
        detail = status,
        confirmLabel = buttonLabel,
        destructive = true,
        confirmColors = if (confirmed) ButtonDefaults.filledTonalButtonColors() else null,
        confirmEnabled = !processing,
        backEnabled = !processing,
        onConfirm = onEdgeButtonClick,
        onBack = { if (confirmed) onDeleted() else onDismiss() },
    )
}
