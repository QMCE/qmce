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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
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
    onLogout: () -> Unit,
    vm: MyViewModel = viewModel(),
) {
    val profile by vm.profile.collectAsState()
    val operationStatus by vm.operationStatus.collectAsState()
    val context = LocalContext.current
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var showClearCacheConfirmation by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var avatarIndex by remember(profile.uin, profile.avatarPath) { mutableIntStateOf(0) }
    val avatarModel = profile.avatarPath.takeIf { it.isNotBlank() }?.let(::File)
        ?: profile.avatarUrls.getOrNull(avatarIndex)

    LaunchedEffect(uin) { vm.load(uin) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        scalingParams = ScalingLazyColumnDefaults.scalingParams(
            viewportVerticalOffsetResolver = { 0 },
        ),
    ) {
        item(key = "profile") {
            ProfileHeader(
                profile = profile,
                avatarModel = avatarModel,
                onAvatarError = {
                    if (profile.avatarPath.isBlank() && avatarIndex < profile.avatarUrls.lastIndex) avatarIndex++
                },
                onCopyUin = { copyUin(context, profile.uin) },
            )
        }
        item(key = "account-label") { MySectionLabel("账号") }
        item(key = "copy-account") {
            MyActionRow(
                icon = Icons.Default.ContentCopy,
                title = "复制 QQ 号",
                subtitle = profile.uin.ifBlank { "账号未就绪" },
                onClick = { copyUin(context, profile.uin) },
            )
        }
        item(key = "refresh-profile") {
            MyActionRow(
                icon = Icons.Default.Refresh,
                title = "刷新资料",
                subtitle = if (profile.refreshing) "正在请求最新资料" else "同步昵称、签名和头像缓存",
                onClick = { vm.load(uin, forceRefresh = true) },
            )
        }
        item(key = "debug-label") { MySectionLabel("Debug") }
        item(key = "sync-messages") {
            MyActionRow(
                icon = Icons.Default.Sync,
                title = "同步消息列表",
                subtitle = "立即请求 NT 消息同步",
                onClick = { vm.syncMessages(chatListVm) },
            )
        }
        item(key = "clear-cache") {
            MyActionRow(
                icon = Icons.Default.DeleteSweep,
                title = "清理聊天缓存",
                subtitle = "清除内核的聊天媒体缓存",
                destructive = true,
                onClick = { showClearCacheConfirmation = true },
            )
        }
        item(key = "about-label") { MySectionLabel("应用") }
        item(key = "settings") {
            MyActionRow(
                icon = Icons.Default.Settings,
                title = "设置",
                subtitle = "显示、同步与缓存管理",
                onClick = onOpenSettings,
            )
        }
        item(key = "about") {
            MyActionRow(
                icon = Icons.Default.Info,
                title = "关于 QMCE Lite X",
                subtitle = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                onClick = { showAbout = true },
            )
        }
        item(key = "logout") {
            MyActionRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "退出登录",
                destructive = true,
                onClick = { showLogoutConfirmation = true },
            )
        }
        if (operationStatus.isNotBlank()) {
            item(key = "operation-status") {
                Text(
                    text = operationStatus,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (showClearCacheConfirmation) {
        ConfirmActionDialog(
            title = "清理聊天缓存？",
            detail = "图片、表情和其他聊天媒体会在需要时重新下载。",
            confirmLabel = "清理",
            destructive = true,
            onDismiss = { showClearCacheConfirmation = false },
            onConfirm = {
                showClearCacheConfirmation = false
                vm.clearChatCache()
            },
        )
    }
    if (showLogoutConfirmation) {
        ConfirmActionDialog(
            title = "退出当前账号？",
            detail = "本机保存的登录票据将被清除，需要重新扫码登录。",
            confirmLabel = "退出登录",
            destructive = true,
            onDismiss = { showLogoutConfirmation = false },
            onConfirm = {
                showLogoutConfirmation = false
                onLogout()
            },
        )
    }
    if (showAbout) {
        ConfirmActionDialog(
            title = "QMCE Lite X",
            detail = "独立轻量 QQ 客户端\n版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）\n包名 ${BuildConfig.APPLICATION_ID}",
            confirmLabel = "关闭",
            destructive = false,
            onDismiss = { showAbout = false },
            onConfirm = { showAbout = false },
        )
    }
}

@Composable
private fun ProfileHeader(
    profile: MyViewModel.Profile,
    avatarModel: Any?,
    onAvatarError: () -> Unit,
    onCopyUin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onCopyUin,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
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
                    Text(profile.nickname.take(1).ifBlank { "Q" }, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.nickname,
                    fontSize = 16.sp,
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
                text = "QQ：${profile.uin}",
                color = scheme.onPrimaryContainer,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (profile.signature.isNotBlank()) {
                Text(
                    text = profile.signature,
                    color = scheme.onPrimaryContainer,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            Text(
                text = "轻触复制账号",
                color = scheme.onPrimaryContainer,
                fontSize = 8.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MySectionLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    )
}

@Composable
private fun MyActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (destructive) scheme.errorContainer else scheme.surfaceContainerHigh
    val contentColor = if (destructive) scheme.onErrorContainer else scheme.onSurface
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = contentColor, maxLines = 1)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        color = contentColor.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmActionDialog(
    title: String,
    detail: String,
    confirmLabel: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = { Text(title, textAlign = TextAlign.Center) },
        text = { Text(detail, textAlign = TextAlign.Center, fontSize = 11.sp) },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (destructive) scheme.error else scheme.primary,
                    contentColor = if (destructive) scheme.onError else scheme.onPrimary,
                ),
            ) { Text(confirmLabel, fontSize = 11.sp) }
        },
    )
}

private fun copyUin(context: Context, uin: String) {
    if (uin.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("QQ 号", uin))
    Toast.makeText(context, "QQ 号已复制", Toast.LENGTH_SHORT).show()
}
