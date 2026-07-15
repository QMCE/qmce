package rj.qmce.lite.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.viewmodel.ChatListViewModel
import rj.qmce.lite.viewmodel.MyViewModel
import java.io.File

@Composable
fun MyScreen(
    uin: String,
    chatListVm: ChatListViewModel,
    onOpenSettings: () -> Unit,
    onOpenClearCache: () -> Unit,
    onOpenLogoutConfirmation: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: MyViewModel = viewModel(),
) {
    val profile by vm.profile.collectAsState()
    val operationStatus by vm.operationStatus.collectAsState()
    val context = LocalContext.current
    var avatarIndex by remember(profile.uin, profile.avatarPath) { mutableIntStateOf(0) }
    val avatarModel = profile.avatarPath.takeIf { it.isNotBlank() }?.let(::File)
        ?: profile.avatarUrls.getOrNull(avatarIndex)

    LaunchedEffect(uin) { vm.load(uin) }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "profile") {
                ProfileHeader(
                    profile = profile,
                    avatarModel = avatarModel,
                    onAvatarError = {
                        if (profile.avatarPath.isBlank() && avatarIndex < profile.avatarUrls.lastIndex) avatarIndex++
                    },
                    onCopyUin = { copyUin(context, profile.uin) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "account-label") {
                MySectionLabel(
                    text = "账号",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "copy-account") {
                MyActionRow(
                    icon = Icons.Default.ContentCopy,
                    title = "复制 QQ 号",
                    subtitle = profile.uin.ifBlank { "账号未就绪" },
                    onClick = { copyUin(context, profile.uin) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "refresh-profile") {
                MyActionRow(
                    icon = Icons.Default.Refresh,
                    title = "刷新资料",
                    subtitle = if (profile.refreshing) "正在请求最新资料" else "同步昵称、签名和头像缓存",
                    onClick = { vm.load(uin, forceRefresh = true) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "debug-label") {
                MySectionLabel(
                    text = "Debug",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "sync-messages") {
                MyActionRow(
                    icon = Icons.Default.Sync,
                    title = "同步消息列表",
                    subtitle = "立即请求 NT 消息同步",
                    onClick = { vm.syncMessages(chatListVm) },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "clear-cache") {
                MyActionRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "清理聊天缓存",
                    subtitle = "清除内核的聊天媒体缓存",
                    destructive = true,
                    onClick = onOpenClearCache,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "about-label") {
                MySectionLabel(
                    text = "应用",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "settings") {
                MyActionRow(
                    icon = Icons.Default.Settings,
                    title = "设置",
                    subtitle = "显示、同步与缓存管理",
                    onClick = onOpenSettings,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "about") {
                MyActionRow(
                    icon = Icons.Default.Info,
                    title = "关于 QMCE Lite X",
                    subtitle = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                    onClick = onOpenAbout,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "logout") {
                MyActionRow(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    title = "退出登录",
                    destructive = true,
                    onClick = onOpenLogoutConfirmation,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
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
private fun ProfileHeader(
    profile: MyViewModel.Profile,
    avatarModel: Any?,
    onAvatarError: () -> Unit,
    onCopyUin: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onCopyUin,
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        transformation = transformation,
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ),
        contentPadding = ButtonDefaults.ButtonWithExtraLargeIconContentPadding,
        icon = {
            Box(
                modifier = Modifier
                    .size(ButtonDefaults.ExtraLargeIconSize)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarModel != null) {
                    AsyncImage(
                        model = avatarModel,
                        contentDescription = "头像",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        onError = { onAvatarError() },
                    )
                } else {
                    Text(profile.nickname.take(1).ifBlank { "Q" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        },
        secondaryLabel = {
            Column {
                Text("QQ：${profile.uin}", maxLines = 1)
                if (profile.signature.isNotBlank()) {
                    Text(profile.signature, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("轻触复制账号", style = MaterialTheme.typography.bodySmall)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.nickname,
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
    }
}

@Composable
private fun MySectionLabel(
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
private fun MyActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
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
        secondaryLabel = subtitle?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
    ) { Text(title, fontWeight = FontWeight.Medium, maxLines = 1) }
}

private fun copyUin(context: Context, uin: String) {
    if (uin.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("QQ 号", uin))
    Toast.makeText(context, "QQ 号已复制", Toast.LENGTH_SHORT).show()
}
