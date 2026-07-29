package rj.qmce.lite.ui.screens

import androidx.compose.runtime.Composable
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox
import rj.qmce.lite.ui.wear.QmceConfirmScreen

@Composable
fun SettingsClearChatCacheScreen(onConfirm: () -> Unit, onBack: () -> Unit) {
    OfficialReportTargetBox(
        key = "settings-clear-cache:confirm",
        elementId = OfficialReportBridge.ElementIds.EMPTY,
    ) { reportTarget ->
        QmceConfirmScreen(
            title = "清理聊天缓存？",
            detail = "图片、表情和其他聊天媒体会在需要时重新下载。",
            confirmLabel = "清理",
            destructive = true,
            onConfirm = {
                OfficialReportBridge.reportElementClick(
                    target = reportTarget,
                    elementId = OfficialReportBridge.ElementIds.EMPTY,
                )
                onConfirm()
            },
            onBack = onBack,
        )
    }
}
