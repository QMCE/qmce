package rj.qmce.lite.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import rj.qmce.lite.ui.wear.SettingsListHeader
import rj.qmce.lite.ui.wear.SettingsSubHeader
import rj.qmce.lite.ui.wear.SettingsSwitch
import rj.qmce.lite.viewmodel.SettingsViewModel

@Composable
fun NotificationSettingsScreen(
    settingsVm: SettingsViewModel,
    @Suppress("UNUSED_PARAMETER") onOpenWatchlist: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by settingsVm.settings.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "notify-header") {
                SettingsListHeader(
                    text = "消息通知",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sec-msg") {
                SettingsSubHeader(
                    text = "开关",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "notify-enabled") {
                SwitchButton(
                    checked = settings.notifyEnabled,
                    onCheckedChange = { enabled ->
                        settingsVm.setNotifyEnabled(enabled)
                        if (!enabled) {
                            runCatching {
                                val nm = context.getSystemService(
                                    android.app.NotificationManager::class.java,
                                )
                                val channels = setOf(
                                    rj.qmce.lite.notify.QmceNotificationChannels.C2C,
                                    rj.qmce.lite.notify.QmceNotificationChannels.GROUP,
                                    rj.qmce.lite.notify.QmceNotificationChannels.CONTACT,
                                )
                                nm?.activeNotifications
                                    ?.filter { it.notification.channelId in channels }
                                    ?.forEach { nm.cancel(it.tag, it.id) }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("消息通知") },
                    secondaryLabel = {
                        Text("关闭后不发送会话通知", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
            item(key = "notify-c2c") {
                SettingsSwitch(
                    checked = settings.notifyC2c,
                    onCheckedChange = settingsVm::setNotifyC2c,
                    enabled = settings.notifyEnabled,
                    label = "私聊消息",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "notify-group") {
                SettingsSwitch(
                    checked = settings.notifyGroup,
                    onCheckedChange = settingsVm::setNotifyGroup,
                    enabled = settings.notifyEnabled,
                    label = "群消息",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "notify-contact") {
                SettingsSwitch(
                    checked = settings.notifyContact,
                    onCheckedChange = settingsVm::setNotifyContact,
                    enabled = settings.notifyEnabled,
                    label = "好友与群系统通知",
                    secondaryLabel = "待处理申请提醒",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sec-system") {
                SettingsSubHeader(
                    text = "系统",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "notify-permission") {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }.onFailure {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    secondaryLabel = { Text("打开系统设置或申请权限", maxLines = 2) },
                ) {
                    Text("系统通知权限")
                }
            }
        }
    }
}
