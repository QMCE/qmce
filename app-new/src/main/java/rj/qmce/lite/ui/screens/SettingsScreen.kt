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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox
import rj.qmce.lite.ui.wear.QmceConfirmScreen
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import rj.qmce.lite.ui.wear.SettingsListHeader
import rj.qmce.lite.ui.wear.SettingsNavButton
import rj.qmce.lite.ui.wear.SettingsSubHeader
import rj.qmce.lite.ui.wear.SettingsSwitch
import rj.qmce.lite.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenInteraction: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBackground: () -> Unit,
    onOpenCall: () -> Unit,
    onOpenData: () -> Unit,
    onOpenIntelligence: () -> Unit,
    onOpenAboutHub: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "settings-header") {
                SettingsListHeader(
                    text = "设置",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "settings-appearance") {
                SettingsHubEntry(
                    title = "外观",
                    subtitle = "时间、缩放与安全区",
                    icon = Icons.Default.Settings,
                    reportName = "外观",
                    onClick = onOpenAppearance,
                    transformationSpec = transformationSpec,
                )
            }
            item(key = "settings-interaction") {
                SettingsHubEntry(
                    title = "操作",
                    subtitle = "分页与全屏交互",
                    icon = Icons.Default.Refresh,
                    reportName = "操作",
                    onClick = onOpenInteraction,
                    transformationSpec = transformationSpec,
                )
            }
            item(key = "settings-notifications") {
                SettingsNavButton(
                    icon = Icons.Default.Notifications,
                    title = "消息通知",
                    subtitle = "私聊、群与系统通知",
                    onClick = onOpenNotifications,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "settings-background") {
                SettingsNavButton(
                    icon = Icons.Default.Sync,
                    title = "后台",
                    subtitle = "保活、刷新与 Wear",
                    onClick = onOpenBackground,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "settings-call") {
                SettingsNavButton(
                    icon = Icons.Default.Call,
                    title = "通话",
                    subtitle = "视频前台与返回行为",
                    onClick = onOpenCall,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "settings-data") {
                SettingsHubEntry(
                    title = "数据",
                    subtitle = "同步与缓存",
                    icon = Icons.Default.DeleteSweep,
                    reportName = "数据",
                    onClick = onOpenData,
                    transformationSpec = transformationSpec,
                )
            }
            item(key = "settings-intelligence") {
                SettingsHubEntry(
                    title = "智能",
                    subtitle = "AI 与智能体",
                    icon = Icons.Default.SmartToy,
                    reportName = "智能",
                    onClick = onOpenIntelligence,
                    transformationSpec = transformationSpec,
                )
            }
            item(key = "settings-about-hub") {
                SettingsHubEntry(
                    title = "关于",
                    subtitle = "更新、诊断与版本",
                    icon = Icons.Default.Info,
                    reportName = "关于",
                    onClick = onOpenAboutHub,
                    transformationSpec = transformationSpec,
                )
            }
        }
    }
}

@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.SettingsHubEntry(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    reportName: String,
    onClick: () -> Unit,
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
) {
    val params = mapOf("function_name" to reportName)
    OfficialReportTargetBox(
        key = "settings:$reportName",
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, transformationSpec),
        elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
        params = params,
        reportImpression = true,
    ) { reportTarget ->
        SettingsNavButton(
            icon = icon,
            title = title,
            subtitle = subtitle,
            onClick = {
                OfficialReportBridge.reportElementClick(
                    target = reportTarget,
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                )
                onClick()
            },
            transformation = SurfaceTransformation(transformationSpec),
            modifier = Modifier.fillMaxWidth(),
        )
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
    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "appearance-header") {
                SettingsListHeader(
                    text = "外观",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sec-status") {
                SettingsSubHeader(
                    text = "状态栏",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "appearance-time") {
                SettingsSwitch(
                    checked = settings.showTimeText,
                    onCheckedChange = settingsVm::setShowTimeText,
                    label = "顶部时间",
                    secondaryLabel = "在屏幕顶部显示当前时间",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "appearance-online") {
                SettingsSwitch(
                    checked = settings.showOnlineStatus,
                    onCheckedChange = settingsVm::setShowOnlineStatus,
                    enabled = settings.showTimeText,
                    label = "顶部在线状态",
                    secondaryLabel = if (settings.showTimeText) {
                        "与时间一起显示在线状态"
                    } else {
                        "需先开启顶部时间"
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sec-scale") {
                SettingsSubHeader(
                    text = "缩放",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "appearance-auto-scale") {
                SettingsSwitch(
                    checked = settings.autoScale,
                    onCheckedChange = settingsVm::setAutoScale,
                    label = "自动缩放",
                    secondaryLabel = "按屏幕宽度自动填充界面密度",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "appearance-manual-scale") {
                AppearanceSliderBlock(
                    title = "手动缩放",
                    valueLabel = String.format(java.util.Locale.US, "%.2fx", settings.manualScale),
                    hint = if (settings.autoScale) {
                        "关闭自动缩放后可手动调整"
                    } else {
                        "调整界面缩放倍率（最大 2.2×）"
                    },
                    value = settings.manualScale,
                    onValueChange = settingsVm::setManualScale,
                    enabled = !settings.autoScale,
                    steps = 24,
                    valueRange = 0.75f..2.20f,
                    transformationSpec = transformationSpec,
                )
            }
            item(key = "appearance-font-scale") {
                AppearanceSliderBlock(
                    title = "字体大小",
                    valueLabel = String.format(java.util.Locale.US, "%.2fx", settings.fontScale),
                    hint = "独立于界面缩放调整文字大小",
                    value = settings.fontScale,
                    onValueChange = settingsVm::setFontScale,
                    enabled = true,
                    steps = 10,
                    valueRange = 0.85f..1.40f,
                    transformationSpec = transformationSpec,
                )
            }
            item(key = "sec-safe") {
                SettingsSubHeader(
                    text = "安全区",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "appearance-edge-safe") {
                SettingsSwitch(
                    checked = settings.edgeSafeAreaEnabled,
                    onCheckedChange = settingsVm::setEdgeSafeAreaEnabled,
                    label = "边缘安全区",
                    secondaryLabel = "圆屏边缘留白，防止内容贴边裁切",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "appearance-edge-scale") {
                AppearanceSliderBlock(
                    title = "安全区强度",
                    valueLabel = String.format(
                        java.util.Locale.US,
                        "官方 ×%.2f",
                        settings.edgeSafeAreaScale,
                    ),
                    hint = "相对 Wear 官方边距比例调节",
                    value = settings.edgeSafeAreaScale,
                    onValueChange = settingsVm::setEdgeSafeAreaScale,
                    enabled = settings.edgeSafeAreaEnabled,
                    steps = 24,
                    valueRange = 0.25f..1.5f,
                    transformationSpec = transformationSpec,
                )
            }
        }
    }
}

@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.AppearanceSliderBlock(
    title: String,
    valueLabel: String,
    hint: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    steps: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
) {
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
            .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Text(
                valueLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            hint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            steps = steps,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
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
                SettingsListHeader(
                    text = "操作",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sec-nav") {
                SettingsSubHeader(
                    text = "导航",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "interaction-page-indicator") {
                SettingsSwitch(
                    checked = settings.showPageIndicator,
                    onCheckedChange = settingsVm::setShowPageIndicator,
                    label = "分页指示器",
                    secondaryLabel = "显示会话、联系人、空间和我的位置",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "interaction-fullscreen-dialogs") {
                SettingsSwitch(
                    checked = settings.fullscreenDialogs,
                    onCheckedChange = settingsVm::setFullscreenDialogs,
                    label = "全屏任务页面",
                    secondaryLabel = "确认和输入任务使用完整圆屏舞台",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}

@Composable
fun DeveloperToolsSettingsScreen(
    settingsVm: SettingsViewModel,
    onOpenPacketTool: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by settingsVm.settings.collectAsState()
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
                SettingsListHeader(
                    text = "开发工具",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
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
            item(key = "developer-packet-enable") {
                SettingsSwitch(
                    checked = settings.packetToolEnabled,
                    onCheckedChange = settingsVm::setPacketToolEnabled,
                    label = "启用发包工具",
                    secondaryLabel = "默认关闭，打开后仍需确认",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "developer-packet-tool") {
                SettingsNavButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "发包工具",
                    subtitle = if (settings.packetToolEnabled) {
                        "发送 PB、OIDB 或 Ark 消息"
                    } else {
                        "请先打开上方开关"
                    },
                    onClick = {
                        if (settings.packetToolEnabled) {
                            showPacketWarn = true
                        }
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}
