package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import mqq.app.AppRuntime
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import rj.qmce.lite.ui.wear.SettingsInfoButton
import rj.qmce.lite.ui.wear.SettingsListHeader
import rj.qmce.lite.ui.wear.SettingsNavButton
import rj.qmce.lite.ui.wear.SettingsSubHeader
import rj.qmce.lite.viewmodel.ChatListViewModel
import rj.qmce.lite.viewmodel.ContactsViewModel
import rj.qmce.lite.viewmodel.MyViewModel
import rj.qmce.lite.viewmodel.QZoneViewModel
import rj.qmce.lite.viewmodel.StorageViewModel
import rj.qmce.lite.viewmodel.formatBytes

@Composable
fun DataSettingsScreen(
    runtime: AppRuntime?,
    chatListVm: ChatListViewModel,
    contactsVm: ContactsViewModel,
    qZoneVm: QZoneViewModel,
    myVm: MyViewModel,
    storageVm: StorageViewModel,
    onOpenClearCache: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val operationStatus by myVm.operationStatus.collectAsState()
    val cacheState by storageVm.state.collectAsState()
    LaunchedEffect(Unit) { storageVm.refreshCacheSizes() }
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
            item(key = "data-header") {
                SettingsListHeader(
                    text = "数据",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sec-sync") {
                SettingsSubHeader(
                    text = "同步",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sync-messages") {
                SettingsNavButton(
                    icon = Icons.Default.Sync,
                    title = "同步消息列表",
                    subtitle = "立即请求 NT 消息同步",
                    onClick = { myVm.syncMessages(chatListVm) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sync-contacts") {
                SettingsNavButton(
                    icon = Icons.Default.Refresh,
                    title = "刷新联系人",
                    subtitle = "重新请求好友和分组数据",
                    onClick = { contactsVm.loadBuddies(runtime, forceRefresh = true) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sync-qzone") {
                SettingsNavButton(
                    icon = Icons.Default.Cached,
                    title = "刷新空间动态",
                    subtitle = "重新请求最新空间动态",
                    onClick = { qZoneVm.loadFeeds(forceRefresh = true) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
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
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            item(key = "sec-storage") {
                SettingsSubHeader(
                    text = "存储",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "storage-kernel-cache") {
                SettingsInfoButton(
                    title = "内核聊天缓存",
                    subtitle = kernelLabel,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "storage-app-cache") {
                SettingsInfoButton(
                    title = "应用临时目录",
                    subtitle = appLabel,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "storage-total-cache") {
                SettingsInfoButton(
                    title = "合计占用",
                    subtitle = totalLabel,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
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
                SettingsNavButton(
                    icon = Icons.Default.DeleteSweep,
                    title = "清理聊天缓存",
                    subtitle = clearSubtitle,
                    onClick = {
                        if (!cacheState.clearing && !cacheState.scanning) {
                            onOpenClearCache()
                        }
                    },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}
