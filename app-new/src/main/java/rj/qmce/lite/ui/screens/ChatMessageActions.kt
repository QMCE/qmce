package rj.qmce.lite.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import java.io.File
import java.net.URLConnection

data class MessageAction(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
)

data class MessageActionContext(
    val isLastMessage: Boolean = false,
    val previousMessage: ChatDetailViewModel.UiMsg? = null,
)

object MessageActionResolver {
    fun resolve(message: ChatDetailViewModel.UiMsg, context: MessageActionContext = MessageActionContext()): List<MessageAction> = buildList {
        val contents = message.contents

        // 撤回：自己发的已送达消息
        if (message.isSelf && message.status == 2) {
            add(MessageAction("recall", "撤回", destructive = true))
        }

        // 编辑：自己发的有文本的消息
        val copyable = contents.copyableText()
        if (message.isSelf && copyable.isNotBlank()) {
            add(MessageAction("edit", "编辑"))
        }

        if (copyable.isNotBlank()) add(MessageAction("copy", "复制"))

        // +1：最后一条消息且与前一条文本相同
        if (context.isLastMessage && copyable.isNotBlank()) {
            val prevText = context.previousMessage?.contents?.copyableText()
            if (prevText == copyable) {
                add(MessageAction("repeat", "+1"))
            }
        }

        // 转发：有文本/图片/表情
        val hasForwardableContent = copyable.isNotBlank() ||
            contents.any { it is ChatDetailViewModel.MessageContent.Image || it is ChatDetailViewModel.MessageContent.MarketFace }
        if (hasForwardableContent) {
            add(MessageAction("forward", "转发"))
        }

        val hasMedia = contents.any {
            it is ChatDetailViewModel.MessageContent.Image ||
                it is ChatDetailViewModel.MessageContent.MarketFace
        }
        if (hasMedia) add(MessageAction("view_media", "查看图片"))

        val mediaFile = message.firstLocalMediaFile()
        if (mediaFile != null) add(MessageAction("share_media", "系统分享"))

        if (contents.any { it is ChatDetailViewModel.MessageContent.Forward }) {
            add(MessageAction("forward_detail", "查看聊天记录"))
        }

        // 多选
        add(MessageAction("multi_select", "多选"))

        // 删除
        add(MessageAction("delete", "删除", destructive = true))
    }

    fun copyableText(message: ChatDetailViewModel.UiMsg): String = message.contents.copyableText()
}

private fun List<ChatDetailViewModel.MessageContent>.copyableText(): String =
    filterIsInstance<ChatDetailViewModel.MessageContent.Text>()
        .joinToString("") { it.value }
        .ifBlank {
            filterIsInstance<ChatDetailViewModel.MessageContent.Card>()
                .joinToString("\n") { listOf(it.title, it.description).filter(String::isNotBlank).joinToString("\n") }
        }
        .ifBlank {
            buildList {
                filterIsInstance<ChatDetailViewModel.MessageContent.Wallet>().forEach {
                    add(listOf(it.title, it.description, it.notice).filterNotNull().filter(String::isNotBlank).joinToString("\n"))
                }
                filterIsInstance<ChatDetailViewModel.MessageContent.Calendar>().forEach {
                    add(listOf(it.title, it.description).filter(String::isNotBlank).joinToString("\n"))
                }
            }.filter(String::isNotBlank).joinToString("\n")
        }
        .ifBlank {
            filterIsInstance<ChatDetailViewModel.MessageContent.File>()
                .firstOrNull()?.name.orEmpty()
        }

@Composable
fun MessageActionsDialog(
    message: ChatDetailViewModel.UiMsg,
    context: MessageActionContext = MessageActionContext(),
    onDismiss: () -> Unit,
    onAction: (MessageAction) -> Unit,
) {
    val actions = MessageActionResolver.resolve(message, context)
    if (actions.isEmpty()) return
    AlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = { Text("消息操作", textAlign = TextAlign.Center) },
        confirmButton = {},
        dismissButton = {},
        content = {
            actions.forEach { action ->
                item {
                    Button(
                        onClick = { onAction(action) },
                        enabled = action.enabled,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        colors = if (action.destructive) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ) else ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text(action.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
    )
}

fun copyMessageText(context: Context, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("QQ 消息", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

fun openHttpLink(context: Context, url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (uri.scheme !in setOf("http", "https")) return false
    return runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}

fun shareLocalMedia(context: Context, file: File) {
    if (!file.isFile) return
    Thread {
        runCatching {
            val sharedFile = copyForExternalAccess(context, file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", sharedFile)
            postOnMain {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType(contentTypeFor(sharedFile))
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "分享媒体",
                    ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }.onFailure {
            postOnMain { Toast.makeText(context, "无法分享此文件", Toast.LENGTH_SHORT).show() }
        }
    }.start()
}

fun openLocalFile(context: Context, file: File) {
    if (!file.isFile) return
    Thread {
        runCatching {
            val sharedFile = copyForExternalAccess(context, file)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", sharedFile)
            postOnMain {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, contentTypeFor(sharedFile))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            }
        }.onFailure {
            postOnMain { Toast.makeText(context, "无法打开此文件", Toast.LENGTH_SHORT).show() }
        }
    }.start()
}

private fun postOnMain(action: () -> Unit) {
    Handler(Looper.getMainLooper()).post(action)
}

private fun copyForExternalAccess(context: Context, source: File): File {
    val sharedDir = File(context.cacheDir, "shared-media").apply { mkdirs() }
    val suffix = source.extension.takeIf { it.isNotBlank() }?.let { ".${it.take(12)}" }.orEmpty()
    val sharedFile = File(sharedDir, "media_${System.currentTimeMillis()}$suffix")
    source.inputStream().use { input -> sharedFile.outputStream().use(input::copyTo) }
    return sharedFile
}

private fun contentTypeFor(file: File): String =
    URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"

fun ChatDetailViewModel.UiMsg.firstLocalMediaFile(): File? = contents.firstNotNullOfOrNull { content ->
    when (content) {
        is ChatDetailViewModel.MessageContent.Image -> {
            (content.localPaths + content.thumbnailPaths + listOfNotNull(content.sourcePath))
                .asSequence()
                .map { File(it.removePrefix("file://")) }
                .firstOrNull(File::isFile)
        }
        is ChatDetailViewModel.MessageContent.MarketFace -> sequenceOf(content.dynamicPath, content.staticPath)
            .filterNotNull()
            .map { File(it.removePrefix("file://")) }
            .firstOrNull(File::isFile)
        else -> null
    }
}
