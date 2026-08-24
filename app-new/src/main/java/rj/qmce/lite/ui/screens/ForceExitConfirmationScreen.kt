package rj.qmce.lite.ui.screens

import androidx.compose.runtime.Composable
import rj.qmce.lite.ui.wear.QmceConfirmScreen

@Composable
fun ForceExitConfirmationScreen(
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    QmceConfirmScreen(
        title = "立即结束 QMCE 进程？",
        detail = "登录状态不会被清除。下次打开 QMCE 时仍会使用当前账号。",
        confirmLabel = "强制退出",
        destructive = true,
        onConfirm = onConfirm,
        onBack = onBack,
    )
}
