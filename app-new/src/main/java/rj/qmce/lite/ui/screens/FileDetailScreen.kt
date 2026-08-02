package rj.qmce.lite.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.data.chat.LocalMediaResolver
import rj.qmce.lite.ui.wear.QmceListHeader
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
    val context = LocalContext.current
    val localFile = remember(content.path) {
        LocalMediaResolver.resolveFile(content.path)
    }
    val expiry = content.expireTime?.takeIf { it > 0L }?.let { epoch ->
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(normalizeEpochMillis(epoch)))
    }
    val status = fileTransferStatus(content, localFile != null)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme

    val listBody: @Composable BoxScope.(PaddingValues) -> Unit = { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "file-detail-header") {
                QmceListHeader(
                    text = "文件详情",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "file-name") {
                Text(
                    text = content.name,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            item(key = "file-info-header") {
                QmceListHeader(
                    text = "信息",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "file-size") {
                FileDetailInfoRow(
                    label = "大小",
                    value = Formatter.formatShortFileSize(context, content.sizeBytes),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "file-status") {
                FileDetailInfoRow(
                    label = "状态",
                    value = status,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            content.progress?.takeIf { localFile == null }?.let { progress ->
                item(key = "file-progress") {
                    FileDetailInfoRow(
                        label = "进度",
                        value = "$progress%",
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            content.downloadError?.takeIf { localFile == null }?.let { error ->
                item(key = "file-download-error") {
                    FileDetailInfoRow(
                        label = "下载",
                        value = error,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            fileExtensionLabel(content.name)?.let { type ->
                item(key = "file-type") {
                    FileDetailInfoRow(
                        label = "类型",
                        value = type,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            expiry?.let { expiryText ->
                item(key = "file-expiry") {
                    FileDetailInfoRow(
                        label = "到期",
                        value = expiryText,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            if (localFile == null) {
                downloadUnavailableReason?.let { reason ->
                    item(key = "file-unavailable-reason") {
                        Text(
                            text = reason,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
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
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            if (localFile != null) {
                item(key = "file-share") {
                    Button(
                        onClick = { shareLocalMedia(context, localFile) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    ) {
                        Text("分享")
                    }
                }
            }
        }
    }

    if (localFile != null) {
        QmceScreenScaffold(
            scrollState = listState,
            edgeButton = {
                EdgeButton(
                    onClick = { onOpenLocalFile(localFile) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开")
                }
            },
            content = listBody,
        )
    } else {
        QmceScreenScaffold(
            scrollState = listState,
            content = listBody,
        )
    }
}

@Composable
private fun FileDetailInfoRow(
    label: String,
    value: String,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                with(transformation) {
                    applyContainerTransformation()
                    applyContentTransformation()
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Text(
            value,
            color = scheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun fileTransferStatus(
    content: ChatDetailViewModel.MessageContent.File,
    hasLocalFile: Boolean,
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
