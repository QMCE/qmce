package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil3.compose.AsyncImage
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox
import rj.qmce.lite.viewmodel.MyViewModel
import java.io.File

@Composable
fun MyScreen(
    uin: String,
    onOpenSettings: () -> Unit,
    onOpenLogoutConfirmation: () -> Unit,
    onForceExit: () -> Unit,
    vm: MyViewModel = viewModel(),
) {
    val profile by vm.profile.collectAsState()
    val operationStatus by vm.operationStatus.collectAsState()
    var avatarIndex by remember(profile.uin, profile.avatarPath) { mutableIntStateOf(0) }
    val avatarModel = profile.avatarPath.takeIf { it.isNotBlank() }?.let(::File)
        ?: profile.avatarUrls.getOrNull(avatarIndex)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LaunchedEffect(uin) { vm.load(uin) }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "my-profile") {
                ProfileHeader(
                    profile = profile,
                    avatarModel = avatarModel,
                    onAvatarError = {
                        if (profile.avatarPath.isBlank() && avatarIndex < profile.avatarUrls.lastIndex) avatarIndex++
                    },
                )
            }
            item(key = "my-refresh-profile") {
                val params = mapOf("function_name" to "刷新资料")
                OfficialReportTargetBox(
                    key = "my:refresh-profile",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                ) { reportTarget ->
                    MyActionRow(
                        icon = Icons.Default.Refresh,
                        title = "刷新资料",
                        subtitle = if (profile.refreshing) "正在请求最新资料" else "同步昵称、签名和头像缓存",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            vm.load(uin, forceRefresh = true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "my-settings") {
                val params = mapOf("function_name" to "设置")
                OfficialReportTargetBox(
                    key = "my:settings",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                ) { reportTarget ->
                    MyActionRow(
                        icon = Icons.Default.Settings,
                        title = "设置",
                        subtitle = "显示、交互、同步与存储管理",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onOpenSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "my-account-label") {
                Text(
                    "账号",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
            item(key = "my-force-exit") {
                val params = mapOf("function_name" to "强制退出")
                OfficialReportTargetBox(
                    key = "my:force-exit",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                ) { reportTarget ->
                    MyActionRow(
                        icon = Icons.Default.PowerSettingsNew,
                        title = "强制退出",
                        subtitle = "立即终止 QMCE，不清理登录数据",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onForceExit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "my-logout") {
                val params = mapOf("function_name" to "退出登录")
                OfficialReportTargetBox(
                    key = "my:logout",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                    params = params,
                ) { reportTarget ->
                    MyActionRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        title = "退出登录",
                        subtitle = "清理本机登录状态后返回登录页",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.FEATURE_ENTRY,
                                params = params,
                            )
                            onOpenLogoutConfirmation()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            if (operationStatus.isNotBlank()) {
                item(key = "my-operation-status") {
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
private fun ProfileHeader(
    profile: MyViewModel.Profile,
    avatarModel: Any?,
    onAvatarError: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarModel != null) {
                AsyncImage(
                    model = avatarModel,
                    contentDescription = "头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { onAvatarError() },
                )
            } else {
                Text(
                    profile.nickname.take(1).ifBlank { "Q" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.nickname,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.refreshing) {
                    Spacer(Modifier.width(6.dp))
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                }
            }
            Text(
                "QQ：${profile.uin}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            if (profile.signature.isNotBlank()) {
                Text(
                    profile.signature,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MyActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
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
        secondaryLabel = subtitle?.let {
            {
                Text(
                    it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
    ) { Text(title, fontWeight = FontWeight.Medium, maxLines = 1) }
}
