package rj.qmce.lite.ui.screens

import androidx.compose.runtime.Composable
import rj.qmce.lite.ui.wear.QmceConfirmScreen

@Composable
fun LogoutConfirmationScreen(onConfirm: () -> Unit, onBack: () -> Unit) {
    QmceConfirmScreen(
        title = "退出当前账号？",
        detail = "本机保存的登录票据将被清除，需要重新扫码登录。",
        confirmLabel = "退出登录",
        destructive = true,
        onConfirm = onConfirm,
        onBack = onBack,
    )
}
