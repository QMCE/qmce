package rj.qmce.lite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import coil3.compose.AsyncImage
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// 消息列表的单个联系人
@Composable
fun ChatItem(
    contact: RecentContactInfo,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val isGroup = contact.chatType == 2
    val name = if (isGroup) {
        contact.peerName?.takeIf { it.isNotBlank() } ?: contact.id ?: "未知群"
    } else {
        contact.remark?.takeIf { it.isNotBlank() }
            ?: contact.peerName?.takeIf { it.isNotBlank() }
            ?: contact.memberName?.takeIf { it.isNotBlank() }
            ?: contact.id ?: "未知"
    }
    val preview = contact.abstractContent
        ?.joinToString("") { it.content ?: "" }
        ?.takeIf { it.isNotBlank() } ?: ""
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

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = avatarModel,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh, CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (timeStr.isNotEmpty()) {
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.outline,
                            fontSize = 9.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTime(msgTime: Long): String {
    if (msgTime <= 0) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = msgTime * 1000 }
    val today = Calendar.getInstance()
    return if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
    } else {
        SimpleDateFormat("MM/dd", Locale.getDefault()).format(cal.time)
    }
}
