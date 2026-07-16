package rj.qmce.lite.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
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

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "settings-top") {
                Button(
                    onClick = vm::togglePinned,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = if (state.pinned) {
                        ButtonDefaults.filledVariantButtonColors()
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                    secondaryLabel = { Text("保持会话显示在顶部") },
                ) { Text(if (state.pinned) "已置顶会话" else "置顶会话") }
            }
            item(key = "settings-muted") {
                Button(
                    onClick = vm::toggleMuted,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = if (state.muted) {
                        ButtonDefaults.filledVariantButtonColors()
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = { Icon(Icons.Default.NotificationsOff, contentDescription = null) },
                    secondaryLabel = { Text("控制这个会话的通知推送") },
                ) { Text(if (state.muted) "已开启消息免打扰" else "消息免打扰") }
            }
            if (state.busy) {
                item(key = "settings-busy") {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(8.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            state.error?.let { message ->
                item(key = "settings-error") {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            }
        }
    }
}
