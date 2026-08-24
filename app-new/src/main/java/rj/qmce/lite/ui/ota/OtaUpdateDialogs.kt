package rj.qmce.lite.ui.ota

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import rj.qmce.lite.data.update.OtaDownloadMode
import rj.qmce.lite.data.update.OtaUiState
import rj.qmce.lite.data.update.OtaUpdateSession

@Composable
fun OtaUpdateDialogHost() {
    val state by OtaUpdateSession.ui.collectAsState()

    when (val s = state) {
        is OtaUiState.NeedWifi -> {
            AlertDialog(
                visible = true,
                onDismissRequest = { OtaUpdateSession.dismiss() },
                title = { Text("需要 Wi‑Fi") },
                text = {
                    Text(
                        "当前未连接 Wi‑Fi。请连接后再检查或下载更新，也可在升级设置中关闭「仅 Wi‑Fi」。",
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                content = {
                    item {
                        Button(
                            onClick = { OtaUpdateSession.openWifiSettings() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text("打开 Wi‑Fi") }
                    }
                    item {
                        Button(
                            onClick = { OtaUpdateSession.dismiss() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) { Text("取消") }
                    }
                },
            )
        }
        is OtaUiState.Confirm -> {
            val force = s.available.forceUpdate
            val changelog = s.available.changelog ?: s.available.message
            val sizeLine = s.available.size.takeIf { it > 0 }?.let { " · ${formatSize(it)}" }.orEmpty()
            AlertDialog(
                visible = true,
                onDismissRequest = {
                    if (!force) OtaUpdateSession.confirmCancel()
                },
                title = { Text("发现新版本 ${s.available.versionName}") },
                text = {
                    Text(
                        buildString {
                            append(changelog?.take(120) ?: "有可用更新")
                            append(sizeLine)
                            if (s.available.source.isNotBlank()) {
                                append(" · ")
                                append(s.available.source)
                            }
                            if (force) append("\n（必须更新）")
                        },
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                content = {
                    item {
                        Button(
                            onClick = { OtaUpdateSession.confirmContinue() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text("继续") }
                    }
                    if (!force) {
                        item {
                            Button(
                                onClick = { OtaUpdateSession.confirmCancel() },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(),
                            ) { Text("取消") }
                        }
                    }
                },
            )
        }
        is OtaUiState.PickMethod -> {
            AlertDialog(
                visible = true,
                onDismissRequest = { OtaUpdateSession.dismiss() },
                title = { Text("选择下载方式") },
                text = {
                    Text("手表浏览器 / 手机打开 / 应用内下载", maxLines = 2)
                },
                content = {
                    item {
                        Button(
                            onClick = { OtaUpdateSession.pickMethod(OtaDownloadMode.InApp) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) { Text("应用内下载") }
                    }
                    item {
                        Button(
                            onClick = { OtaUpdateSession.pickMethod(OtaDownloadMode.WatchBrowser) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) { Text("手表浏览器") }
                    }
                    item {
                        Button(
                            onClick = { OtaUpdateSession.pickMethod(OtaDownloadMode.Phone) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) { Text("手机打开") }
                    }
                },
            )
        }
        is OtaUiState.Downloading -> {
            AlertDialog(
                visible = true,
                onDismissRequest = {
                    if (!s.forceUpdate) OtaUpdateSession.cancelDownload()
                },
                title = {
                    Text(if (s.indeterminate) "正在下载…" else "下载 ${s.percent}%")
                },
                icon = {
                    if (s.indeterminate) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    } else {
                        CircularProgressIndicator(
                            progress = { s.percent / 100f },
                            modifier = Modifier.size(40.dp),
                        )
                    }
                },
                content = if (!s.forceUpdate) {
                    {
                        item {
                            Button(
                                onClick = { OtaUpdateSession.cancelDownload() },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(),
                            ) { Text("取消下载") }
                        }
                    }
                } else {
                    null
                },
            )
        }
        is OtaUiState.DownloadFailed -> {
            AlertDialog(
                visible = true,
                onDismissRequest = { OtaUpdateSession.dismiss() },
                title = { Text("下载失败") },
                text = {
                    Text(s.reason, maxLines = 4, overflow = TextOverflow.Ellipsis)
                },
                content = {
                    item {
                        Button(
                            onClick = { OtaUpdateSession.dismiss() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text("知道了") }
                    }
                },
            )
        }
        is OtaUiState.Status -> {
            AlertDialog(
                visible = true,
                onDismissRequest = { OtaUpdateSession.dismiss() },
                title = { Text(s.message, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                content = {
                    item {
                        Button(
                            onClick = { OtaUpdateSession.dismiss() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text("知道了") }
                    }
                },
            )
        }
        is OtaUiState.ProbeDone -> {
            AlertDialog(
                visible = true,
                onDismissRequest = { OtaUpdateSession.dismiss() },
                title = { Text("延迟探测") },
                text = {
                    Text(s.report.summary(), maxLines = 4, overflow = TextOverflow.Ellipsis)
                },
                content = {
                    item {
                        Button(
                            onClick = { OtaUpdateSession.dismiss() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        ) { Text("知道了") }
                    }
                },
            )
        }
        OtaUiState.Checking, OtaUiState.Probing -> {
            AlertDialog(
                visible = true,
                onDismissRequest = {},
                title = {
                    Text(if (state is OtaUiState.Probing) "正在测延迟…" else "正在检查更新…")
                },
                icon = { CircularProgressIndicator(modifier = Modifier.size(40.dp)) },
                content = {
                    item {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) {
                            Text(if (state is OtaUiState.Probing) "探测中…" else "检查中…")
                        }
                    }
                },
            )
        }
        OtaUiState.Idle -> Unit
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1fKB", kb)
    val mb = kb / 1024.0
    return String.format("%.1fMB", mb)
}
