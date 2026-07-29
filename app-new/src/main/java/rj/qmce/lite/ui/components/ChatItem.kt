package rj.qmce.lite.ui.components

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox

/** Recent-contact row for the chat list (Wear M3 Button). */
@Composable
fun ChatItem(
    contact: RecentContactInfo,
    reportParams: Map<String, *> = emptyMap<String, Any?>(),
    reuseIdentifier: String? = null,
    onClick: (View) -> Unit = {},
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val isGroup = contact.chatType == 2
    val pinned = contact.topFlag.toInt() != 0
    val name = if (isGroup) {
        contact.peerName?.takeIf { it.isNotBlank() } ?: contact.id ?: "未知群"
    } else {
        contact.remark?.takeIf { it.isNotBlank() }
            ?: contact.peerName?.takeIf { it.isNotBlank() }
            ?: contact.memberName?.takeIf { it.isNotBlank() }
            ?: contact.id ?: "未知"
    }
    val preview = buildString {
        if (pinned) append("置顶 · ")
        append(
            contact.abstractContent
                ?.joinToString("") { it.content ?: "" }
                ?.takeIf { it.isNotBlank() }
                .orEmpty(),
        )
    }
    val timeStr = formatTime(contact.msgTime)
    val fallbackAvatarUrl = if (isGroup) {
        "https://p.qlogo.cn/gh/${contact.id}/${contact.id}/100"
    } else {
        "https://q1.qlogo.cn/g?b=qq&nk=${contact.id}&s=100"
    }
    val avatarModel = contact.avatarPath
        ?.removePrefix("file://")
        ?.let(::File)
        ?.takeIf(File::isFile)
        ?: contact.avatarUrl?.takeIf { it.isNotBlank() }
        ?: fallbackAvatarUrl

    val key = reuseIdentifier ?: contact.contactId.takeIf { it > 0L }?.toString()
        ?: contact.id ?: contact.peerUid ?: name
    OfficialReportTargetBox(
        key = "chat-item:$key",
        modifier = modifier,
        elementId = OfficialReportBridge.ElementIds.MESSAGE_ENTRY,
        params = reportParams,
        reuseIdentifier = reuseIdentifier ?: key,
    ) { reportTarget ->
        Button(
            onClick = { onClick(reportTarget) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (pinned) {
                    scheme.primaryContainer
                } else {
                    scheme.surfaceContainerHigh
                },
                contentColor = if (pinned) scheme.onPrimaryContainer else scheme.onSurface,
                secondaryContentColor = if (pinned) {
                    scheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    scheme.onSurfaceVariant
                },
            ),
            transformation = transformation,
            contentPadding = ButtonDefaults.ButtonWithExtraLargeIconContentPadding,
            icon = {
                val hasUnread = (contact.unreadCnt ?: 0) > 0
                Box {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(ButtonDefaults.ExtraLargeIconSize)
                            .clip(CircleShape)
                            .background(scheme.surfaceContainer, CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    if (hasUnread) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .align(Alignment.BottomEnd)
                                .background(scheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Flag,
                                contentDescription = "未读",
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(9.dp),
                            )
                        }
                    }
                }
            },
            secondaryLabel = {
                if (preview.isNotBlank()) {
                    Text(
                        text = preview,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (timeStr.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = timeStr,
                        color = scheme.outline,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun formatTime(msgTime: Long): String {
    if (msgTime <= 0) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = msgTime * 1000 }
    val today = Calendar.getInstance()
    return if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    ) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
    } else {
        SimpleDateFormat("MM/dd", Locale.getDefault()).format(cal.time)
    }
}
