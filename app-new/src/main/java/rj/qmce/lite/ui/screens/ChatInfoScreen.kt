package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rj.qmce.lite.data.media.MediaStoreSaver
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import java.io.File

@Composable
fun ChatInfoScreen(
    peerUid: String,
    peerUin: Long,
    chatType: Int,
    peerName: String,
    avatarPath: String = "",
    avatarUrl: String = "",
    vm: ChatDetailViewModel,
    onOpenMembers: () -> Unit,
    onOpenGroupInfo: () -> Unit,
    onOpenMessageSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val saveScope = rememberCoroutineScope()
    val mediaStoreSaver = remember { MediaStoreSaver() }
    var avatarTarget by remember(peerUid, peerUin, chatType) {
        mutableStateOf<AvatarPreviewTarget?>(null)
    }
    var saveLabel by remember { mutableStateOf("保存") }

    avatarTarget?.let { target ->
        FullscreenMediaViewer(
            media = target.media,
            onDismiss = {
                avatarTarget = null
                saveLabel = "保存"
            },
            onSave = {
                if (saveLabel == "正在保存…") return@FullscreenMediaViewer
                saveScope.launch {
                    saveLabel = "正在保存…"
                    val result = withContext(Dispatchers.IO) {
                        mediaStoreSaver.saveImage(context, target.source)
                    }
                    saveLabel = result.fold(
                        onSuccess = { "已保存" },
                        onFailure = { "保存失败" },
                    )
                }
            },
            saveLabel = saveLabel,
        )
        return
    }

    val scheme = MaterialTheme.colorScheme
    val members by vm.groupMembers.collectAsState()
    val isGroup = chatType == 2
    val displayName = peerName.ifBlank { if (isGroup) "未知群聊" else "未知联系人" }

    LaunchedEffect(isGroup, peerUin, peerUid) {
        if (isGroup && peerUin > 0L) {
            vm.loadGroupMembers(peerUin, updateStatus = false)
        }
    }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "chat-info-header") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ChatInfoAvatar(
                            chatType = chatType,
                            peerUin = peerUin,
                            peerName = displayName,
                            avatarPath = avatarPath,
                            avatarUrl = avatarUrl,
                            onClick = { model, source ->
                                avatarTarget = AvatarPreviewTarget(
                                    media = ViewerMedia(
                                        key = "avatar:$chatType:$peerUin:$peerUid",
                                        model = model,
                                        description = "$displayName 的头像",
                                    ),
                                    source = source,
                                )
                            },
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (isGroup) "群号：${peerUin.takeIf { it > 0L } ?: "未知"}"
                            else "QQ：${peerUin.takeIf { it > 0L } ?: "未知"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (isGroup && members.isNotEmpty()) {
                            Text(
                                text = "${members.size} 名成员",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.outline,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            if (isGroup) {
                item(key = "chat-info-members") {
                    Button(
                        onClick = onOpenMembers,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledVariantButtonColors(),
                        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                            )
                        },
                        secondaryLabel = { Text("查看并搜索群成员") },
                    ) { Text("群成员") }
                }
                item(key = "chat-info-group-info") {
                    Button(
                        onClick = onOpenGroupInfo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                        icon = { Icon(Icons.Default.Info, contentDescription = null) },
                        secondaryLabel = { Text("群号、公告和权限信息") },
                    ) { Text("群资料") }
                }
            }
            item(key = "chat-info-message-search") {
                Button(
                    onClick = onOpenMessageSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                        )
                    },
                    secondaryLabel = { Text("在已加载消息中查找") },
                ) { Text("搜索聊天记录") }
            }
            item(key = "chat-info-settings") {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                        )
                    },
                    secondaryLabel = { Text("置顶会话和消息提醒") },
                ) { Text(if (isGroup) "群聊设置" else "好友设置") }
            }
        }
    }
}

@Composable
private fun ChatInfoAvatar(
    chatType: Int,
    peerUin: Long,
    peerName: String,
    avatarPath: String,
    avatarUrl: String,
    onClick: (model: Any, source: String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fallbackUrls = remember(chatType, peerUin, avatarUrl) {
        buildList {
            avatarUrl.takeIf { it.isNotBlank() }?.let(::add)
            if (peerUin > 0L) {
                if (chatType == 2) {
                    add("https://p.qlogo.cn/gh/$peerUin/$peerUin/100")
                } else {
                    add("https://q1.qlogo.cn/g?b=qq&nk=$peerUin&s=100")
                    add("https://q2.qlogo.cn/headimg_dl?dst_uin=$peerUin&spec=100")
                    add("https://qlogo2.store.qq.com/qzone/$peerUin/$peerUin/100")
                }
            }
        }
    }
    var remoteIndex by remember(fallbackUrls) { mutableIntStateOf(0) }
    val local = avatarPath.removePrefix("file://")
        .takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf(File::isFile)
    val model: Any? = local ?: fallbackUrls.getOrNull(remoteIndex)
    val source = local?.absolutePath ?: fallbackUrls.getOrNull(remoteIndex)

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh, CircleShape)
            .then(
                if (model != null && source != null) {
                    Modifier.clickable { onClick(model, source) }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = peerName.firstOrNull()?.toString() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
                onError = {
                    if (local == null && remoteIndex < fallbackUrls.lastIndex) remoteIndex++
                },
            )
        }
    }
}
