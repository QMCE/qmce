package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.ui.wear.QmceListHeader
import rj.qmce.lite.viewmodel.SettingsViewModel

@Composable
fun DiagnosticsSettingsScreen(
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
            item(key = "diagnostics-header") {
                QmceListHeader(
                    text = "诊断",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "qmce-verbose-log") {
                SettingsSwitchRow(
                    checked = settings.qmceVerboseLog,
                    onCheckedChange = settingsVm::setQmceVerboseLog,
                    title = "详细日志",
                    subtitle = "打开后 logcat 输出 v/d/i，可能刷屏",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "qlog-local-write") {
                SettingsSwitchRow(
                    checked = settings.qlogLocalWriteEnabled,
                    onCheckedChange = settingsVm::setQlogLocalWriteEnabled,
                    title = "QQ QLog 写本地",
                    subtitle = "默认关闭；打开后 SDK 可写本地日志文件",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            if (!BuildConfig.DEBUG) {
                item(key = "appcenter-reporting") {
                    SettingsSwitchRow(
                        checked = settings.appCenterReportingEnabled,
                        onCheckedChange = settingsVm::setAppCenterReportingEnabled,
                        title = "崩溃与分析上报",
                        subtitle = "关闭可减少后台流量，默认开启",
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
        }
    }
}
