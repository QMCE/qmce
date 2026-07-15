package rj.qmce.lite.ui.screens

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
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
    onOpenPacketTool: () -> Unit = {},
    onOpenClearCache: () -> Unit = {},
) {
    val settings by settingsVm.settings.collectAsState()
    val operationStatus by myVm.operationStatus.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                Text(
                    text = "设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(vertical = 8.dp),
                )
            }
            item(key = "display-label") {
                SettingsSectionLabel(
                    text = "显示",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "show-time") {
                SwitchButton(
                    checked = settings.showTimeText,
                    onCheckedChange = settingsVm::setShowTimeText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("顶部时间") },
                    secondaryLabel = { Text("在屏幕顶部显示当前时间") },
                )
            }
            item(key = "show-page-indicator") {
                SwitchButton(
                    checked = settings.showPageIndicator,
                    onCheckedChange = settingsVm::setShowPageIndicator,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("分页指示器") },
                    secondaryLabel = { Text("显示会话、联系人、空间和我的页面位置") },
                )
            }
            item(key = "show-online-status") {
                SwitchButton(
                    checked = settings.showOnlineStatus,
                    onCheckedChange = settingsVm::setShowOnlineStatus,
                    enabled = settings.showTimeText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("顶部在线状态") },
                    secondaryLabel = { Text("需开启时间显示") },
                )
            }
            item(key = "fullscreen-dialogs") {
                SwitchButton(
                    checked = settings.fullscreenDialogs,
                    onCheckedChange = settingsVm::setFullscreenDialogs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("对话框全屏") },
                    secondaryLabel = { Text("对话框默认铺满手表屏幕") },
                )
            }
            item(key = "auto-scale") {
                SwitchButton(
                    checked = settings.autoScale,
                    onCheckedChange = settingsVm::setAutoScale,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("自动缩放") },
                    secondaryLabel = { Text("使用系统原生尺寸和密度") },
                )
            }
            item(key = "manual-scale") {
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
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("手动缩放")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.2fx", settings.manualScale),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
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
            item(key = "data-label") {
                SettingsSectionLabel(
                    text = "同步与数据",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "packet-tool") {
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
            item(key = "refresh-contacts") {
                SettingsActionRow(
                    icon = Icons.Default.Refresh,
                    title = "刷新联系人",
                    subtitle = "重新请求好友和分组数据",
                    onClick = { contactsVm.loadBuddies(runtime, forceRefresh = true) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "refresh-qzone") {
                SettingsActionRow(
                    icon = Icons.Default.Cached,
                    title = "刷新空间动态",
                    subtitle = "重新请求最新空间动态",
                    onClick = { qZoneVm.loadFeeds(forceRefresh = true) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "clear-cache") {
                SettingsActionRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "清理聊天缓存",
                    subtitle = "清除内核的聊天媒体缓存",
                    destructive = true,
                    onClick = onOpenClearCache,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "app-label") {
                SettingsSectionLabel(
                    text = "应用",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "app-info") {
                Text(
                    text = "QMCE Lite X ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）\n${BuildConfig.APPLICATION_ID}",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (operationStatus.isNotBlank()) {
                item(key = "operation-status") {
                    Text(
                        text = operationStatus,
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(
    text: String,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                with(transformation) {
                    applyContainerTransformation()
                    applyContentTransformation()
                }
            }
            .padding(horizontal = 16.dp, vertical = 5.dp),
    )
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (destructive) scheme.errorContainer else scheme.surfaceContainerHigh
    val contentColor = if (destructive) scheme.onErrorContainer else scheme.onSurface
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        transformation = transformation,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            secondaryContentColor = contentColor.copy(alpha = 0.78f),
        ),
        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
        icon = { Icon(icon, contentDescription = null) },
        secondaryLabel = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    ) { Text(title, color = contentColor, fontWeight = FontWeight.Medium, maxLines = 1) }
}
