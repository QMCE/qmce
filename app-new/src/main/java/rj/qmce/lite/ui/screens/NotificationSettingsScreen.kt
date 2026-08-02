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
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ListSubHeader
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.notify.QmceMessageRefreshScheduler
import rj.qmce.lite.service.QmceKeepAliveService
import rj.qmce.lite.util.QmceDevice
import rj.qmce.lite.viewmodel.SettingsViewModel
import rj.qmce.lite.wear.QmceWatchlistStore

@Composable
fun NotificationSettingsScreen(
    settingsVm: SettingsViewModel,
    onOpenWatchlist: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by settingsVm.settings.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isWear = QmceDevice.isWear(context)
    val showLiveUpdates = isWear && Build.VERSION.SDK_INT >= 37
    val watchlistCount = QmceWatchlistStore.load(context).count { it.chatType == 2 }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    QmceScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "notify-header") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ListHeaderDefaults.minimumTopListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text("通知与保活")
                }
            }

            item(key = "sec-msg") {
                ListSubHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text("消息通知")
                }
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
                SwitchButton(
                    checked = settings.notifyC2c,
                    onCheckedChange = settingsVm::setNotifyC2c,
                    enabled = settings.notifyEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("私聊消息") },
                )
            }
            item(key = "notify-group") {
                SwitchButton(
                    checked = settings.notifyGroup,
                    onCheckedChange = settingsVm::setNotifyGroup,
                    enabled = settings.notifyEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("群消息") },
                )
            }
            item(key = "notify-contact") {
                SwitchButton(
                    checked = settings.notifyContact,
                    onCheckedChange = settingsVm::setNotifyContact,
                    enabled = settings.notifyEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("好友与群系统通知") },
                    secondaryLabel = {
                        Text("待处理申请提醒", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                )
            }

            item(key = "sec-keepalive") {
                ListSubHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text("刷新与保活")
                }
            }
            item(key = "notify-refresh") {
                Button(
                    onClick = {
                        val next = nextRefreshMode(settings.messageRefreshMode)
                        settingsVm.setMessageRefreshMode(next)
                        QmceMessageRefreshScheduler.onSettingsChanged(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    secondaryLabel = {
                        Text(refreshLabel(settings.messageRefreshMode), maxLines = 2)
                    },
                ) {
                    Text("消息刷新频率")
                }
            }
            item(key = "notify-keepalive") {
                SwitchButton(
                    checked = settings.keepAlive,
                    onCheckedChange = { enabled ->
                        settingsVm.setKeepAlive(enabled)
                        QmceKeepAliveService.sync(context, loggedIn = true)
                        if (!enabled) QmceKeepAliveService.stop(context)
                        else QmceKeepAliveService.startIfEnabled(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("后台保活") },
                    secondaryLabel = {
                        Text("前台服务维持连接", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
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

            if (isWear) {
                item(key = "sec-wear") {
                    ListSubHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text("Wear")
                    }
                }
                if (showLiveUpdates) {
                    item(key = "notify-live-updates") {
                        SwitchButton(
                            checked = settings.liveUpdates,
                            onCheckedChange = settingsVm::setLiveUpdates,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(
                                    ButtonDefaults.minimumVerticalListContentPadding,
                                ),
                            transformation = SurfaceTransformation(transformationSpec),
                            label = { Text("Live Updates") },
                        )
                    }
                }
                item(key = "notify-voice-bg") {
                    SwitchButton(
                        checked = settings.voiceBackground,
                        onCheckedChange = settingsVm::setVoiceBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ButtonDefaults.minimumVerticalListContentPadding,
                            ),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = { Text("语音可后台") },
                    )
                }
                item(key = "notify-voice-ongoing") {
                    SwitchButton(
                        checked = settings.voiceOngoingSurface,
                        onCheckedChange = settingsVm::setVoiceOngoingSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ButtonDefaults.minimumVerticalListContentPadding,
                            ),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = { Text("语音后台持续展示") },
                    )
                }
                item(key = "notify-complications") {
                    SwitchButton(
                        checked = settings.wearComplicationsEnabled,
                        onCheckedChange = settingsVm::setWearComplicationsEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ButtonDefaults.minimumVerticalListContentPadding,
                            ),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = { Text("表盘复杂组件") },
                    )
                }
                item(key = "notify-tiles") {
                    SwitchButton(
                        checked = settings.wearTilesEnabled,
                        onCheckedChange = settingsVm::setWearTilesEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ButtonDefaults.minimumVerticalListContentPadding,
                            ),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = { Text("消息 Tile") },
                    )
                }
                item(key = "notify-watchlist") {
                    Button(
                        onClick = onOpenWatchlist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                ButtonDefaults.minimumVerticalListContentPadding,
                            ),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        secondaryLabel = {
                            Text(
                                if (watchlistCount <= 0) "尚未选取"
                                else "已选 $watchlistCount 个群",
                                maxLines = 2,
                            )
                        },
                    ) {
                        Text("Tile 群聊选取")
                    }
                }
            }

            item(key = "sec-call") {
                ListSubHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text("通话")
                }
            }
            item(key = "notify-video-strict") {
                SwitchButton(
                    checked = settings.videoStrictForeground,
                    onCheckedChange = settingsVm::setVideoStrictForeground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("视频严格前台") },
                )
            }
            item(key = "notify-call-block-back") {
                SwitchButton(
                    checked = settings.callBlockBack,
                    onCheckedChange = settingsVm::setCallBlockBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ButtonDefaults.minimumVerticalListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("通话禁止返回退出") },
                )
            }
        }
    }
}

private fun refreshLabel(mode: String): String = when (mode) {
    SettingsViewModel.REFRESH_15S -> "当前：15 秒"
    SettingsViewModel.REFRESH_30S -> "当前：30 秒"
    SettingsViewModel.REFRESH_1M -> "当前：1 分钟"
    SettingsViewModel.REFRESH_5M -> "当前：5 分钟"
    else -> "当前：仅推送（点击切换）"
}

private fun nextRefreshMode(mode: String): String = when (mode) {
    SettingsViewModel.REFRESH_PUSH_ONLY -> SettingsViewModel.REFRESH_15S
    SettingsViewModel.REFRESH_15S -> SettingsViewModel.REFRESH_30S
    SettingsViewModel.REFRESH_30S -> SettingsViewModel.REFRESH_1M
    SettingsViewModel.REFRESH_1M -> SettingsViewModel.REFRESH_5M
    else -> SettingsViewModel.REFRESH_PUSH_ONLY
}
