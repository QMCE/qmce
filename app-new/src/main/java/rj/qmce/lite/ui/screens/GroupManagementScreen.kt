package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.viewmodel.GroupManagementState
import rj.qmce.lite.viewmodel.GroupManagementViewModel

@Composable
fun GroupManagementScreen(
    groupCode: Long,
    vm: GroupManagementViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val state by vm.state.collectAsState()
    var pendingMute by remember(groupCode) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(groupCode) {
        vm.load(groupCode)
    }

    val requestedMute = pendingMute
    if (requestedMute != null) {
        GroupManagementConfirmScreen(
            enabled = requestedMute,
            busy = state.busy,
            onConfirm = {
                vm.toggleAllMuted(requestedMute)
                pendingMute = null
            },
            onCancel = { pendingMute = null },
        )
        return
    }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "group-management-refresh") {
                androidx.wear.compose.material3.CompactButton(
                    onClick = { vm.load(groupCode, forceRefresh = true) },
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .padding(vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新群权限")
                }
            }
            item(key = "group-management-context") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("群管理", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "群号：${groupCode.takeIf { it > 0L } ?: "未知"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "当前身份：${state.roleLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.canManage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (state.loading && state.detail == null) {
                item(key = "group-management-loading") {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .padding(vertical = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (state.error != null && state.detail == null) {
                item(key = "group-management-error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            state.error ?: "群管理信息加载失败",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { vm.load(groupCode, forceRefresh = true) },
                            modifier = Modifier.padding(top = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) { Text("重试") }
                    }
                }
            } else if (!state.canManage) {
                item(key = "group-management-readonly") {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                        secondaryLabel = { Text("只有群主或管理员可以管理全员禁言") },
                    ) { Text("只读") }
                }
            } else {
                item(key = "group-management-all-muted") {
                    SwitchButton(
                        checked = state.allMuted,
                        onCheckedChange = { pendingMute = it },
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = { Text("全员禁言") },
                        secondaryLabel = {
                            Text(if (state.allMuted) "当前已开启，点击可关闭" else "允许群成员发言")
                        },
                    )
                }
            }
            state.error?.takeIf { state.detail != null }?.let { error ->
                item(key = "group-management-status-error") {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupManagementConfirmScreen(
    enabled: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "group-management-confirm-warning") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Text(
                        if (enabled) "开启全员禁言？" else "关闭全员禁言？",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (enabled) "群成员将暂时不能发言。" else "群成员将恢复发言。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item(key = "group-management-confirm-action") {
                Button(
                    onClick = onConfirm,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) { Text(if (busy) "处理中…" else "确认") }
            }
            item(key = "group-management-confirm-cancel") {
                Button(
                    onClick = onCancel,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledVariantButtonColors(),
                ) { Text("取消") }
            }
        }
    }
}
