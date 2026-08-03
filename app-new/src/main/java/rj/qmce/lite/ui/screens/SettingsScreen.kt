package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
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
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import mqq.app.AppRuntime
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox
import rj.qmce.lite.data.update.OtaDownloadMode
import rj.qmce.lite.data.update.OtaSourceMode
import rj.qmce.lite.data.update.OtaUpdateSession
import rj.qmce.lite.ui.wear.QmceConfirmScreen
import rj.qmce.lite.ui.wear.QmceListHeader
import rj.qmce.lite.viewmodel.ChatListViewModel
import rj.qmce.lite.viewmodel.ContactsViewModel
import rj.qmce.lite.viewmodel.MyViewModel
import rj.qmce.lite.viewmodel.QZoneViewModel
import rj.qmce.lite.viewmodel.SettingsViewModel
import rj.qmce.lite.viewmodel.StorageViewModel
import rj.qmce.lite.viewmodel.formatBytes

@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenInteraction: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSyncData: () -> Unit,
    onOpenStorage: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenDeveloperTools: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenAgentSettings: () -> Unit = {},
    onOpenAbout: () -> Unit,
    onCheckUpdate: () -> Unit = onOpenAbout,
    onOpenDiagnostics: () -> Unit = {},
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "settings-section-general") {
                QmceListHeader(
                    text = "常规",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-appearance") {
                val params = mapOf("function_name" to "显示与外观")
                OfficialReportTargetBox(
                    key = "settings:appearance",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                    reportImpression = true,
                ) { reportTarget ->
                    SettingsActionRow(
                        icon = Icons.Default.Settings,
                        title = "显示与外观",
                        subtitle = "时间、状态、缩放与显示尺寸",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onOpenAppearance()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "settings-interaction") {
                val params = mapOf("function_name" to "交互与导航")
                OfficialReportTargetBox(
                    key = "settings:interaction",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                    reportImpression = true,
                ) { reportTarget ->
                    SettingsActionRow(
                        icon = Icons.Default.Refresh,
                        title = "交互与导航",
                        subtitle = "分页提示和全屏交互",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onOpenInteraction()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "settings-notifications") {
                SettingsActionRow(
                    icon = Icons.Default.Notifications,
                    title = "通知与保活",
                    subtitle = "消息推送、刷新频率与 Wear 专属",
                    onClick = onOpenNotifications,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-sync") {
                val params = mapOf("function_name" to "同步与数据")
                OfficialReportTargetBox(
                    key = "settings:sync",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                    reportImpression = true,
                ) { reportTarget ->
                    SettingsActionRow(
                        icon = Icons.Default.Sync,
                        title = "同步与数据",
                        subtitle = "消息、联系人与空间",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onOpenSyncData()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "settings-storage") {
                val params = mapOf("function_name" to "存储与缓存")
                OfficialReportTargetBox(
                    key = "settings:storage",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                    reportImpression = true,
                ) { reportTarget ->
                    SettingsActionRow(
                        icon = Icons.Default.DeleteSweep,
                        title = "存储与缓存",
                        subtitle = "聊天媒体缓存管理",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onOpenStorage()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "settings-section-advanced") {
                QmceListHeader(
                    text = "高级",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-ai") {
                val params = mapOf("function_name" to "AI 接入")
                OfficialReportTargetBox(
                    key = "settings:ai",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                    reportImpression = true,
                ) { reportTarget ->
                    SettingsActionRow(
                        icon = Icons.Default.SmartToy,
                        title = "AI 接入",
                        subtitle = "消息总结模型，默认内置可用",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onOpenAiSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "settings-agent") {
                val params = mapOf("function_name" to "Fluoxetine智能体")
                OfficialReportTargetBox(
                    key = "settings:agent",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                    reportImpression = true,
                ) { reportTarget ->
                    SettingsActionRow(
                        icon = Icons.Default.SmartToy,
                        title = "Fluoxetine智能体",
                        subtitle = "内置 AI 助手，可执行发送/群管理等操作",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onOpenAgentSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "settings-section-about") {
                QmceListHeader(
                    text = "关于",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-check-update") {
                SettingsActionRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "升级",
                    subtitle = "Wi‑Fi、更新源与检查升级",
                    onClick = onCheckUpdate,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-diagnostics") {
                SettingsActionRow(
                    icon = Icons.Default.BugReport,
                    title = "诊断",
                    subtitle = "详细日志、QLog 与上报",
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings-about") {
                val params = mapOf("function_name" to "关于")
                OfficialReportTargetBox(
                    key = "settings:about",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                    reportImpression = true,
                ) { reportTarget ->
                    SettingsActionRow(
                        icon = Icons.Default.Info,
                        title = "关于",
                        subtitle = "版本、构建和应用信息",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onOpenAbout()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
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
    QmceScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "appearance-header") {
                QmceListHeader(
                    text = "显示与外观",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
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
                    subtitle = "按屏幕宽度自动填充界面密度（约 1.3–2.0×），列表保留 Wear 滚动缩放",
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
                        if (settings.autoScale) {
                            "关闭自动缩放后可手动调整；过大可能裁切标题"
                        } else {
                            "调整界面缩放倍率（最大 2.2×）"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = settings.manualScale,
                        onValueChange = settingsVm::setManualScale,
                        enabled = !settings.autoScale,
                        steps = 24,
                        valueRange = 0.75f..2.20f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item(key = "appearance-font-scale") {
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
                        Text("字体大小", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            String.format(java.util.Locale.US, "%.2fx", settings.fontScale),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "独立于界面缩放调整文字大小，不影响布局",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = settings.fontScale,
                        onValueChange = settingsVm::setFontScale,
                        steps = 10,
                        valueRange = 0.85f..1.40f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item(key = "appearance-edge-safe") {
                SettingsSwitchRow(
                    checked = settings.edgeSafeAreaEnabled,
                    onCheckedChange = settingsVm::setEdgeSafeAreaEnabled,
                    title = "边缘安全区",
                    subtitle = "圆屏边缘留白，防止内容贴边裁切",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "appearance-edge-scale") {
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
                        Text("安全区强度", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            String.format(
                                java.util.Locale.US,
                                "官方 ×%.2f",
                                settings.edgeSafeAreaScale,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "相对 Wear 官方边距比例调节",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = settings.edgeSafeAreaScale,
                        onValueChange = settingsVm::setEdgeSafeAreaScale,
                        enabled = settings.edgeSafeAreaEnabled,
                        steps = 24,
                        valueRange = 0.25f..1.5f,
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
    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "interaction-header") {
                QmceListHeader(
                    text = "交互与导航",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
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
    onOpenTileGroupPicker: () -> Unit = {},
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = androidx.compose.ui.platform.LocalContext.current
    val operationStatus by myVm.operationStatus.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val watchlistEntries by rj.qmce.lite.wear.QmceWatchlistStore.entries.collectAsState()
    LaunchedEffect(Unit) { rj.qmce.lite.wear.QmceWatchlistStore.load(context) }
    val watchlistCount = watchlistEntries.count { it.chatType == 2 }
    val isWear = rj.qmce.lite.util.QmceDevice.isWear(context)
    QmceScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "sync-header") {
                QmceListHeader(
                    text = "同步与数据",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
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
            if (isWear) {
                item(key = "sync-tile-groups") {
                    SettingsActionRow(
                        icon = Icons.Default.Notifications,
                        title = "Tile 群聊选取",
                        subtitle = if (watchlistCount <= 0) {
                            "尚未选取，点按设置"
                        } else {
                            "已选 $watchlistCount 个群"
                        },
                        onClick = onOpenTileGroupPicker,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            if (operationStatus.isNotBlank()) {
                item(key = "sync-status") {
                    Text(
                        operationStatus,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperToolsSettingsScreen(
    onOpenPacketTool: () -> Unit,
    onBack: () -> Unit,
) {
    var showPacketWarn by remember { mutableStateOf(false) }
    if (showPacketWarn) {
        QmceConfirmScreen(
            title = "风险提示",
            detail = "发包工具可能导致账号异常或协议风险，仅供开发调试。确认继续？",
            confirmLabel = "我了解风险",
            onConfirm = {
                showPacketWarn = false
                onOpenPacketTool()
            },
            onBack = { showPacketWarn = false },
        )
        return
    }

    BackHandler(onBack = onBack)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "developer-header") {
                QmceListHeader(
                    text = "开发工具",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "developer-warning") {
                Text(
                    "以下功能存在协议与账号风险，打开前会再次确认。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item(key = "developer-packet-tool") {
                SettingsActionRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "发包工具",
                    subtitle = "发送 PB、OIDB 或 Ark 消息",
                    onClick = { showPacketWarn = true },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
        }
    }
}

@Composable
fun AiSettingsScreen(
    settingsVm: SettingsViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val settings by settingsVm.settings.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme
    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "ai-header") {
                QmceListHeader(
                    text = "AI 接入",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "ai-builtin-hint") {
                Text(
                    "默认使用内置模型。启用自定义后需同时填写 Base URL、API Key 与 Model，否则仍走内置。",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item(key = "ai-custom-toggle") {
                SettingsSwitchRow(
                    checked = settings.aiCustomEnabled,
                    onCheckedChange = settingsVm::setAiCustomEnabled,
                    title = "启用自定义模型",
                    subtitle = "覆盖内置 OpenAI 兼容接口",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            if (settings.aiCustomEnabled) {
                item(key = "ai-base-url") {
                    AiTextFieldRow(
                        label = "Base URL",
                        value = settings.aiBaseUrl,
                        onValueChange = settingsVm::setAiBaseUrl,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
                item(key = "ai-api-key") {
                    AiTextFieldRow(
                        label = "API Key",
                        value = settings.aiApiKey,
                        onValueChange = settingsVm::setAiApiKey,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
                item(key = "ai-model") {
                    AiTextFieldRow(
                        label = "Model",
                        value = settings.aiModel,
                        onValueChange = settingsVm::setAiModel,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
        }
    }
}

@Composable
fun StorageSettingsScreen(
    storageVm: StorageViewModel,
    onOpenClearCache: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val cacheState by storageVm.state.collectAsState()
    LaunchedEffect(Unit) {
        storageVm.refreshCacheSizes()
    }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val kernelBytes = cacheState.kernelCacheBytes
    val appBytes = cacheState.appCacheBytes
    val totalBytes = cacheState.totalCacheBytes
    val kernelLabel = when {
        cacheState.scanning -> "扫描中…"
        kernelBytes != null -> formatBytes(kernelBytes)
        else -> "暂不可用"
    }
    val appLabel = when {
        cacheState.scanning -> "扫描中…"
        appBytes != null -> formatBytes(appBytes)
        else -> "暂不可用"
    }
    val totalLabel = when {
        cacheState.scanning -> "扫描中…"
        totalBytes != null -> formatBytes(totalBytes)
        else -> "暂不可用"
    }
    val clearSubtitle = when {
        cacheState.clearing -> "正在清理…"
        cacheState.scanning -> "正在统计占用…"
        totalBytes != null -> "共约 $totalLabel，清除内核媒体与应用临时文件"
        else -> "清除内核的聊天媒体缓存与应用临时文件"
    }
    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "storage-header") {
                QmceListHeader(
                    text = "存储与缓存",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "storage-description") {
                Text(
                    "清理后不会删除帐号、联系人或已发送消息，只会删除内核缓存的聊天媒体文件和应用临时目录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            item(key = "storage-kernel-cache") {
                SettingsInfoRow(
                    title = "内核聊天缓存",
                    subtitle = kernelLabel,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "storage-app-cache") {
                SettingsInfoRow(
                    title = "应用临时目录",
                    subtitle = appLabel,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "storage-total-cache") {
                SettingsInfoRow(
                    title = "合计占用",
                    subtitle = totalLabel,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            if (cacheState.statusMessage.isNotBlank()) {
                item(key = "storage-status") {
                    Text(
                        cacheState.statusMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            item(key = "storage-clear-cache") {
                SettingsActionRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "清理聊天缓存",
                    subtitle = clearSubtitle,
                    onClick = {
                        if (!cacheState.clearing && !cacheState.scanning) {
                            onOpenClearCache()
                        }
                    },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
        }
    }
}

@Composable
fun CheckUpdateScreen(
    onBack: () -> Unit,
    settingsVm: SettingsViewModel,
) {
    BackHandler(onBack = onBack)
    val settings by settingsVm.settings.collectAsState()
    val sourceMode = OtaSourceMode.fromPref(settings.otaSourceMode)
    val lastDownload = OtaDownloadMode.fromPref(settings.otaLastDownloadMode)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "check-update-header") {
                QmceListHeader(
                    text = "升级",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "ota-require-wifi") {
                SettingsSwitchRow(
                    checked = settings.otaRequireWifi,
                    onCheckedChange = settingsVm::setOtaRequireWifi,
                    title = "仅 Wi‑Fi 更新",
                    subtitle = "关闭后可在移动网络检查与下载",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "ota-source-mode") {
                SettingsActionRow(
                    icon = Icons.Default.Cached,
                    title = "更新源",
                    subtitle = when (sourceMode) {
                        OtaSourceMode.Auto -> "自动（GitHub 延迟高则用服务器）"
                        OtaSourceMode.GitHub -> "仅 GitHub Releases"
                        OtaSourceMode.Server -> "仅自有服务器"
                    },
                    onClick = {
                        val next = when (sourceMode) {
                            OtaSourceMode.Auto -> OtaSourceMode.GitHub
                            OtaSourceMode.GitHub -> OtaSourceMode.Server
                            OtaSourceMode.Server -> OtaSourceMode.Auto
                        }
                        settingsVm.setOtaSourceMode(next)
                    },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "ota-probe") {
                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    title = "检测延迟",
                    subtitle = "比较 GitHub 与服务器 RTT",
                    onClick = { OtaUpdateSession.probeLatencies() },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "check-update-action") {
                SettingsActionRow(
                    icon = Icons.Default.SystemUpdate,
                    title = "检查升级",
                    subtitle = "上次方式：${
                        when (lastDownload) {
                            OtaDownloadMode.WatchBrowser -> "手表浏览器"
                            OtaDownloadMode.Phone -> "手机打开"
                            OtaDownloadMode.InApp -> "应用内下载"
                        }
                    }",
                    onClick = { OtaUpdateSession.checkForUpdate() },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
        }
    }
}

@Composable
private fun AiTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                with(transformation) {
                    applyContainerTransformation()
                    applyContentTransformation()
                }
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun SettingsSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String?,
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
            .padding(vertical = 2.dp),
        transformation = transformation,
        label = { Text(title) },
        secondaryLabel = subtitle?.let { text ->
            {
                Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
    )
}

@Composable
private fun SettingsInfoRow(
    title: String,
    subtitle: String,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Button(
        onClick = {},
        enabled = false,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        transformation = transformation,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
        secondaryLabel = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    ) { Text(title, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
internal fun SettingsActionRow(
    icon: ImageVector? = null,
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
            .padding(vertical = 2.dp),
        transformation = transformation,
        colors = ButtonDefaults.filledTonalButtonColors(),
        contentPadding = if (icon != null) {
            ButtonDefaults.ButtonWithLargeIconContentPadding
        } else {
            ButtonDefaults.ContentPadding
        },
        icon = icon?.let { image ->
            { Icon(image, contentDescription = null) }
        },
        secondaryLabel = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    ) { Text(title, fontWeight = FontWeight.Medium, maxLines = 1) }
}
