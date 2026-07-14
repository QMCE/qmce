package rj.qmce.lite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import mqq.app.AppRuntime
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.viewmodel.ChatListViewModel
import rj.qmce.lite.viewmodel.ContactsViewModel
import rj.qmce.lite.viewmodel.MyViewModel
import rj.qmce.lite.viewmodel.QZoneViewModel
import rj.qmce.lite.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    runtime: AppRuntime?,
    chatListVm: ChatListViewModel,
    contactsVm: ContactsViewModel,
    qZoneVm: QZoneViewModel,
    myVm: MyViewModel,
    settingsVm: SettingsViewModel,
) {
    val settings by settingsVm.settings.collectAsState()
    val operationStatus by myVm.operationStatus.collectAsState()
    var showClearCacheConfirmation by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(
            viewportVerticalOffsetResolver = { 0 },
        ),
    ) {
        item(key = "title") {
            Text(
                text = "设置",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
        item(key = "display-label") { SettingsSectionLabel("显示") }
        item(key = "show-time") {
            SwitchButton(
                checked = settings.showTimeText,
                onCheckedChange = settingsVm::setShowTimeText,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                label = { Text("顶部时间") },
                secondaryLabel = { Text("在屏幕顶部显示当前时间") },
            )
        }
        item(key = "show-page-indicator") {
            SwitchButton(
                checked = settings.showPageIndicator,
                onCheckedChange = settingsVm::setShowPageIndicator,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                label = { Text("分页指示器") },
                secondaryLabel = { Text("显示会话、联系人、空间和我的页面位置") },
            )
        }
        item(key = "show-online-status") {
            SwitchButton(
                checked = settings.showOnlineStatus,
                onCheckedChange = settingsVm::setShowOnlineStatus,
                enabled = settings.showTimeText,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                label = { Text("顶部在线状态") },
                secondaryLabel = { Text("需开启时间显示") },
            )
        }
        item(key = "data-label") { SettingsSectionLabel("同步与数据") }
        item(key = "sync-messages") {
            SettingsActionRow(
                icon = Icons.Default.Sync,
                title = "同步消息列表",
                subtitle = "立即请求 NT 消息同步",
                onClick = { myVm.syncMessages(chatListVm) },
            )
        }
        item(key = "refresh-contacts") {
            SettingsActionRow(
                icon = Icons.Default.Refresh,
                title = "刷新联系人",
                subtitle = "重新请求好友和分组数据",
                onClick = { contactsVm.loadBuddies(runtime, forceRefresh = true) },
            )
        }
        item(key = "refresh-qzone") {
            SettingsActionRow(
                icon = Icons.Default.Cached,
                title = "刷新空间动态",
                subtitle = "重新请求最新空间动态",
                onClick = { qZoneVm.loadFeeds(forceRefresh = true) },
            )
        }
        item(key = "clear-cache") {
            SettingsActionRow(
                icon = Icons.Default.DeleteSweep,
                title = "清理聊天缓存",
                subtitle = "清除内核的聊天媒体缓存",
                destructive = true,
                onClick = { showClearCacheConfirmation = true },
            )
        }
        item(key = "app-label") { SettingsSectionLabel("应用") }
        item(key = "app-info") {
            Text(
                text = "QMCE Lite X ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）\n${BuildConfig.APPLICATION_ID}",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (operationStatus.isNotBlank()) {
            item(key = "operation-status") {
                Text(
                    text = operationStatus,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (showClearCacheConfirmation) {
        val scheme = MaterialTheme.colorScheme
        AlertDialog(
            visible = true,
            onDismissRequest = { showClearCacheConfirmation = false },
            title = { Text("清理聊天缓存？", textAlign = TextAlign.Center) },
            text = {
                Text(
                    text = "图片、表情和其他聊天媒体会在需要时重新下载。",
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearCacheConfirmation = false
                        myVm.clearChatCache()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.error,
                        contentColor = scheme.onError,
                    ),
                ) { Text("清理", fontSize = 11.sp) }
            },
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (destructive) scheme.errorContainer else scheme.surfaceContainerHigh
    val contentColor = if (destructive) scheme.onErrorContainer else scheme.onSurface
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    color = contentColor.copy(alpha = 0.78f),
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
