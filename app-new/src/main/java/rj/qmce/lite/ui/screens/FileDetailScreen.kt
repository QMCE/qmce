package rj.qmce.lite.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import rj.qmce.lite.data.chat.LocalMediaResolver
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
internal fun FileDetailScreen(
    message: ChatDetailViewModel.UiMsg,
    content: ChatDetailViewModel.MessageContent.File,
    onOpenLocalFile: (File) -> Unit,
    onDownloadFile: () -> Unit,
    downloadUnavailableReason: String?,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val context = androidx.compose.ui.platform.LocalContext.current
    val localFile = remember(content.path) {
        LocalMediaResolver.resolveFile(content.path)
    }
    val expiry = content.expireTime?.takeIf { it > 0L }?.let { epoch ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(normalizeEpochMillis(epoch)))
    }
    val status = fileTransferStatus(content, localFile != null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "文件详情",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = content.name,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        FileDetailLine("大小", Formatter.formatShortFileSize(context, content.sizeBytes))
        FileDetailLine("状态", status)
        content.progress?.takeIf { localFile == null }?.let { FileDetailLine("进度", "$it%") }
        content.downloadError?.takeIf { localFile == null }?.let { FileDetailLine("下载", it) }
        fileExtensionLabel(content.name)?.let { FileDetailLine("类型", it) }
        expiry?.let { FileDetailLine("到期", it) }
        content.invalidState?.takeIf { it != 0 }?.let { FileDetailLine("文件状态", "不可用 ($it)") }
        content.fileUuid?.let { FileDetailLine("文件标识", it.take(24)) }

        Spacer(Modifier.weight(1f))
        if (localFile != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onOpenLocalFile(localFile) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("打开")
                }
                Button(
                    onClick = { shareLocalMedia(context, localFile) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("分享")
                }
            }
        } else {
            downloadUnavailableReason?.let { FileDetailLine("下载", it) }
            Button(
                onClick = onDownloadFile,
                enabled = downloadUnavailableReason == null &&
                        !content.isDownloading &&
                        (content.invalidState == null || content.invalidState == 0),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    when {
                        downloadUnavailableReason != null -> "下载不可用"
                        content.isDownloading -> "正在下载"
                        content.downloadError != null -> "重新下载"
                        else -> "下载文件"
                    },
                )
            }
        }
    }
}

@Composable
private fun FileDetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.width(48.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun fileTransferStatus(
    content: ChatDetailViewModel.MessageContent.File,
    hasLocalFile: Boolean
): String = when {
    hasLocalFile -> "已缓存"
    content.invalidState != null && content.invalidState != 0 -> "文件不可用"
    content.isDownloading -> "正在请求"
    content.downloadError != null -> content.downloadError
    else -> "未缓存"
}

private fun fileExtensionLabel(name: String): String? = name.substringAfterLast('.', "")
    .takeIf { it.isNotBlank() }
    ?.uppercase()

private fun normalizeEpochMillis(value: Long): Long =
    if (value < 10_000_000_000L) value * 1000L else value
