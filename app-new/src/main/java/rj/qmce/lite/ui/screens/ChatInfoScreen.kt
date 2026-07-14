package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil3.compose.AsyncImage
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import java.io.File

@Composable
fun ChatInfoScreen(
    peerUid: String,
    peerUin: Long,
    chatType: Int,
    peerName: String,
    avatarPath: String = "",
    avatarUrl: String = "",
    vm: ChatDetailViewModel,
    onOpenMembers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val members by vm.groupMembers.collectAsState()
    val isGroup = chatType == 2
    val displayName = peerName.ifBlank { if (isGroup) "未知群聊" else "未知联系人" }

    LaunchedEffect(isGroup, peerUin, peerUid) {
        if (isGroup && peerUin > 0L) {
            vm.loadGroupMembers(peerUin, updateStatus = false)
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(
            viewportVerticalOffsetResolver = { 0 },
        ),
    ) {
        item(key = "chat-info-header") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ChatInfoAvatar(
                        chatType = chatType,
                        peerUin = peerUin,
                        peerName = displayName,
                        avatarPath = avatarPath,
                        avatarUrl = avatarUrl,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isGroup) "群号：${peerUin.takeIf { it > 0L } ?: "未知"}"
                        else "QQ：${peerUin.takeIf { it > 0L } ?: "未知"}",
                        fontSize = 10.sp,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (isGroup && members.isNotEmpty()) {
                        Text(
                            text = "${members.size} 名成员",
                            fontSize = 10.sp,
                            color = scheme.outline,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        if (isGroup) {
            item(key = "chat-info-members") {
                Button(
                    onClick = onOpenMembers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("群成员", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("查看并搜索群成员", fontSize = 9.sp, color = scheme.onPrimaryContainer)
                    }
                }
            }
        }
        item(key = "chat-info-settings") {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (isGroup) "群聊设置" else "好友设置", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("置顶会话和消息提醒", fontSize = 9.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChatInfoAvatar(
    chatType: Int,
    peerUin: Long,
    peerName: String,
    avatarPath: String,
    avatarUrl: String,
) {
    val scheme = MaterialTheme.colorScheme
    val fallbackUrls = remember(chatType, peerUin, avatarUrl) {
        buildList {
            avatarUrl.takeIf { it.isNotBlank() }?.let(::add)
            if (peerUin > 0L) {
                if (chatType == 2) {
                    add("https://p.qlogo.cn/gh/$peerUin/$peerUin/100")
                } else {
                    add("https://q1.qlogo.cn/g?b=qq&nk=$peerUin&s=100")
                    add("https://q2.qlogo.cn/headimg_dl?dst_uin=$peerUin&spec=100")
                    add("https://qlogo2.store.qq.com/qzone/$peerUin/$peerUin/100")
                }
            }
        }
    }
    var remoteIndex by remember(fallbackUrls) { mutableIntStateOf(0) }
    val local = avatarPath.removePrefix("file://")
        .takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf(File::isFile)
    val model: Any? = local ?: fallbackUrls.getOrNull(remoteIndex)

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = peerName.firstOrNull()?.toString() ?: "?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
                onError = {
                    if (local == null && remoteIndex < fallbackUrls.lastIndex) remoteIndex++
                },
            )
        }
    }
}
