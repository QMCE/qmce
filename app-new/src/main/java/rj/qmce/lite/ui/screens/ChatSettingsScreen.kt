package rj.qmce.lite.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import rj.qmce.lite.viewmodel.ChatSettingsViewModel

@Composable
fun ChatSettingsScreen(
    contact: RecentContactInfo,
    peerUid: String,
    peerUin: Long,
    displayName: String,
    vm: ChatSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val isGroup = contact.chatType == 2
    LaunchedEffect(contact, peerUid, peerUin, displayName) {
        vm.load(contact, peerUin, peerUid, displayName)
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(
            viewportVerticalOffsetResolver = { 0 },
        ),
    ) {
        item(key = "settings-title") {
            Text(
                text = if (isGroup) "群聊设置" else "好友设置",
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        item(key = "settings-back") {
                Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = { Icon(Icons.Default.ArrowBack, contentDescription = "返回") },
                ) { Text("返回聊天") }
        }
        item(key = "settings-top") {
            Button(
                onClick = vm::togglePinned,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.pinned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (state.pinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                icon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                secondaryLabel = { Text("保持会话显示在顶部") },
            ) { Text(if (state.pinned) "已置顶会话" else "置顶会话") }
        }
        item(key = "settings-muted") {
            Button(
                onClick = vm::toggleMuted,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.muted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (state.muted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                icon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                secondaryLabel = { Text("控制这个会话的通知推送") },
            ) { Text(if (state.muted) "已开启消息免打扰" else "消息免打扰") }
        }
        if (state.busy) {
            item(key = "settings-busy") { CircularProgressIndicator(Modifier.padding(8.dp).fillMaxWidth(), strokeWidth = 2.dp) }
        }
        state.error?.let { message ->
            item(key = "settings-error") {
                Text(message, fontSize = 10.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
