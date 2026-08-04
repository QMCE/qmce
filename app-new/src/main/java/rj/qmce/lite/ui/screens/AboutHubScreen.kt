package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.data.update.OtaDownloadMode
import rj.qmce.lite.data.update.OtaSourceMode
import rj.qmce.lite.data.update.OtaUpdateSession
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import rj.qmce.lite.ui.wear.SettingsListHeader
import rj.qmce.lite.ui.wear.SettingsNavButton
import rj.qmce.lite.ui.wear.SettingsSubHeader
import rj.qmce.lite.ui.wear.SettingsSwitch
import rj.qmce.lite.viewmodel.SettingsViewModel

@Composable
fun AboutHubScreen(
    settingsVm: SettingsViewModel,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
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
            item(key = "about-hub-header") {
                SettingsListHeader(
                    text = "关于",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sec-update") {
                SettingsSubHeader(
                    text = "更新",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "ota-require-wifi") {
                SettingsSwitch(
                    checked = settings.otaRequireWifi,
                    onCheckedChange = settingsVm::setOtaRequireWifi,
                    label = "仅 Wi‑Fi 更新",
                    secondaryLabel = "关闭后可在移动网络检查与下载",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "ota-source-mode") {
                SettingsNavButton(
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
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "ota-probe") {
                SettingsNavButton(
                    icon = Icons.Default.Refresh,
                    title = "检测延迟",
                    subtitle = "比较 GitHub 与服务器 RTT",
                    onClick = { OtaUpdateSession.probeLatencies() },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "check-update-action") {
                SettingsNavButton(
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
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sec-diagnostics") {
                SettingsSubHeader(
                    text = "诊断",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "qmce-verbose-log") {
                SettingsSwitch(
                    checked = settings.qmceVerboseLog,
                    onCheckedChange = settingsVm::setQmceVerboseLog,
                    label = "详细日志",
                    secondaryLabel = "打开后 logcat 输出 v/d/i，可能刷屏",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "qlog-local-write") {
                SettingsSwitch(
                    checked = settings.qlogLocalWriteEnabled,
                    onCheckedChange = settingsVm::setQlogLocalWriteEnabled,
                    label = "QQ QLog 写本地",
                    secondaryLabel = "默认关闭；打开后 SDK 可写本地日志文件",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            if (!BuildConfig.DEBUG) {
                item(key = "appcenter-reporting") {
                    SettingsSwitch(
                        checked = settings.appCenterReportingEnabled,
                        onCheckedChange = settingsVm::setAppCenterReportingEnabled,
                        label = "崩溃与分析上报",
                        secondaryLabel = "关闭可减少后台流量，默认开启",
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                    )
                }
            }
            item(key = "sec-about") {
                SettingsSubHeader(
                    text = "应用",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "about-detail") {
                SettingsNavButton(
                    icon = Icons.Default.Info,
                    title = "版本与开源信息",
                    subtitle = "查看完整关于页",
                    onClick = onOpenAbout,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}
