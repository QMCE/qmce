package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import mqq.app.AppRuntime
import rj.qmce.lite.viewmodel.ChatListViewModel
import rj.qmce.lite.viewmodel.ContactsViewModel
import rj.qmce.lite.viewmodel.MyViewModel
import rj.qmce.lite.viewmodel.QZoneViewModel
import rj.qmce.lite.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenInteraction: () -> Unit,
    onOpenSyncData: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "settings-appearance") {
                SettingsActionRow(
                    icon = Icons.Default.Settings,
                    title = "显示与外观",
                    subtitle = "时间、状态、缩放与显示尺寸",
                    onClick = onOpenAppearance,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-interaction") {
                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    title = "交互与导航",
                    subtitle = "分页提示和全屏交互",
                    onClick = onOpenInteraction,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-sync") {
                SettingsActionRow(
                    icon = Icons.Default.Sync,
                    title = "同步与数据",
                    subtitle = "消息、联系人、空间与发包工具",
                    onClick = onOpenSyncData,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-storage") {
                SettingsActionRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "存储与缓存",
                    subtitle = "聊天媒体缓存管理",
                    onClick = onOpenStorage,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-about") {
                SettingsActionRow(
                    icon = Icons.Default.Info,
                    title = "关于",
                    subtitle = "版本、构建和应用信息",
                    onClick = onOpenAbout,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(
    settingsVm: SettingsViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val settings by settingsVm.settings.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "appearance-time") {
                SettingsSwitchRow(
                    checked = settings.showTimeText,
                    onCheckedChange = settingsVm::setShowTimeText,
                    title = "顶部时间",
                    subtitle = "在屏幕顶部显示当前时间",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "appearance-online") {
                SettingsSwitchRow(
                    checked = settings.showOnlineStatus,
                    onCheckedChange = settingsVm::setShowOnlineStatus,
                    enabled = settings.showTimeText,
                    title = "顶部在线状态",
                    subtitle = if (settings.showTimeText) "与时间一起显示在线状态" else "需先开启顶部时间",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "appearance-auto-scale") {
                SettingsSwitchRow(
                    checked = settings.autoScale,
                    onCheckedChange = settingsVm::setAutoScale,
                    title = "自动缩放",
                    subtitle = "使用手表的原生尺寸和密度",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "appearance-manual-scale") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("手动缩放", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            String.format(java.util.Locale.US, "%.2fx", settings.manualScale),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        if (settings.autoScale) "关闭自动缩放后可调整" else "调整界面缩放倍率",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = settings.manualScale,
                        onValueChange = settingsVm::setManualScale,
                        enabled = !settings.autoScale,
                        steps = 24,
                        valueRange = 0.75f..2.0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun InteractionSettingsScreen(
    settingsVm: SettingsViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val settings by settingsVm.settings.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "interaction-page-indicator") {
                SettingsSwitchRow(
                    checked = settings.showPageIndicator,
                    onCheckedChange = settingsVm::setShowPageIndicator,
                    title = "分页指示器",
                    subtitle = "显示会话、联系人、空间和我的位置",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "interaction-fullscreen-dialogs") {
                SettingsSwitchRow(
                    checked = settings.fullscreenDialogs,
                    onCheckedChange = settingsVm::setFullscreenDialogs,
                    title = "全屏任务页面",
                    subtitle = "确认和输入任务使用完整圆屏舞台",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
        }
    }
}

@Composable
fun SyncDataSettingsScreen(
    runtime: AppRuntime?,
    chatListVm: ChatListViewModel,
    contactsVm: ContactsViewModel,
    qZoneVm: QZoneViewModel,
    myVm: MyViewModel,
    onOpenPacketTool: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val operationStatus by myVm.operationStatus.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "sync-packet-tool") {
                SettingsActionRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "发包工具",
                    subtitle = "发送 PB、OIDB 或 Ark 消息",
                    onClick = onOpenPacketTool,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "sync-messages") {
                SettingsActionRow(
                    icon = Icons.Default.Sync,
                    title = "同步消息列表",
                    subtitle = "立即请求 NT 消息同步",
                    onClick = { myVm.syncMessages(chatListVm) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "sync-contacts") {
                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    title = "刷新联系人",
                    subtitle = "重新请求好友和分组数据",
                    onClick = { contactsVm.loadBuddies(runtime, forceRefresh = true) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "sync-qzone") {
                SettingsActionRow(
                    icon = Icons.Default.Cached,
                    title = "刷新空间动态",
                    subtitle = "重新请求最新空间动态",
                    onClick = { qZoneVm.loadFeeds(forceRefresh = true) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            if (operationStatus.isNotBlank()) {
                item(key = "sync-status") {
                    Text(
                        operationStatus,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun StorageSettingsScreen(
    onOpenClearCache: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "storage-description") {
                Text(
                    "清理后不会删除帐号、联系人或已发送消息，只会删除内核缓存的聊天媒体文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            item(key = "storage-clear-cache") {
                SettingsActionRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "清理聊天缓存",
                    subtitle = "清除内核的聊天媒体缓存",
                    onClick = onOpenClearCache,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier,
    transformation: SurfaceTransformation,
    enabled: Boolean = true,
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        transformation = transformation,
        label = { Text(title) },
        secondaryLabel = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        transformation = transformation,
        colors = ButtonDefaults.filledTonalButtonColors(),
        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
        icon = { Icon(icon, contentDescription = null) },
        secondaryLabel = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    ) { Text(title, fontWeight = FontWeight.Medium, maxLines = 1) }
}
