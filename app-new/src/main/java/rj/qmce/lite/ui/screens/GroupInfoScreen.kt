package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rj.qmce.lite.data.media.MediaStoreSaver
import rj.qmce.lite.viewmodel.GroupInfoState
import rj.qmce.lite.viewmodel.GroupInfoViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupInfoScreen(
    groupCode: Long,
    avatarPath: String,
    avatarUrl: String,
    vm: GroupInfoViewModel,
    onOpenMembers: () -> Unit,
    onOpenManagement: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val saveScope = rememberCoroutineScope()
    val mediaStoreSaver = remember { MediaStoreSaver() }
    var avatarTarget by remember(groupCode) { mutableStateOf<AvatarPreviewTarget?>(null) }
    var saveLabel by remember { mutableStateOf("保存") }
    val localAvatar = avatarPath.removePrefix("file://")
        .takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf(File::isFile)
    val avatarUrls = remember(groupCode, avatarUrl) {
        listOfNotNull(
            avatarUrl.takeIf { it.isNotBlank() },
            "https://p.qlogo.cn/gh/$groupCode/$groupCode/100",
        )
    }
    val avatarModel: Any? = localAvatar ?: avatarUrls.firstOrNull()
    val avatarSource = localAvatar?.absolutePath ?: avatarUrls.firstOrNull()

    LaunchedEffect(groupCode) {
        vm.load(groupCode)
    }

    avatarTarget?.let { target ->
        FullscreenMediaViewer(
            media = target.media,
            onDismiss = {
                avatarTarget = null
                saveLabel = "保存"
            },
            onSave = {
                if (saveLabel != "正在保存…") {
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
                }
            },
            saveLabel = saveLabel,
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
            item(key = "group-info-refresh") {
                CompactButton(
                    onClick = { vm.load(groupCode, forceRefresh = true) },
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .padding(vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新群资料")
                }
            }
            item(key = "group-info-header") {
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
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .then(
                                    if (avatarModel != null && avatarSource != null) {
                                        Modifier.clickable {
                                            avatarTarget = AvatarPreviewTarget(
                                                media = ViewerMedia(
                                                    key = "group-avatar:$groupCode",
                                                    model = avatarModel,
                                                    description = "群头像",
                                                ),
                                                source = avatarSource,
                                            )
                                        }
                                    } else Modifier,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                state.detail?.groupName?.firstOrNull()?.toString() ?: "群",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (avatarModel != null) {
                                AsyncImage(
                                    model = avatarModel,
                                    contentDescription = "群头像",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        Text(
                            state.detail?.groupName?.takeIf { it.isNotBlank() } ?: "群资料",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "群号：$groupCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "group-info-members") {
                Button(
                    onClick = onOpenMembers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledVariantButtonColors(),
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = { Icon(Icons.Default.Group, contentDescription = null) },
                ) { Text("群成员") }
            }
            if (state.detail?.cmdUinPrivilege == MemberRole.OWNER ||
                state.detail?.cmdUinPrivilege == MemberRole.ADMIN
            ) {
                item(key = "group-info-management") {
                    Button(
                        onClick = onOpenManagement,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                        icon = { Icon(Icons.Default.Group, contentDescription = null) },
                    ) { Text("群管理") }
                }
            }
            if (state.loading && state.detail == null) {
                item(key = "group-info-loading") {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(12.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            state.error?.let { error ->
                item(key = "group-info-error") {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
            state.detail?.let { detail ->
                groupField(transformationSpec, "群名", detail.groupName.orEmpty())
                groupField(transformationSpec, "群名片", detail.remarkName.orEmpty().ifBlank { "未设置" })
                groupField(transformationSpec, "成员", "${detail.memberNum}/${detail.maxMemberNum}")
                groupField(transformationSpec, "群主 UID", detail.ownerUid.orEmpty().ifBlank { "未知" })
                groupField(
                    transformationSpec,
                    "群权限",
                    detail.cmdUinPrivilege?.name?.let(::groupRoleLabel) ?: "未知",
                )
                groupField(transformationSpec, "全员禁言", if (detail.shutUpAllTimestamp > 0) "已开启" else "未开启")
                if (detail.groupMemo.orEmpty().isNotBlank()) {
                    groupField(transformationSpec, "群简介", detail.groupMemo.orEmpty())
                }
            }
            item(key = "group-bulletin-title") {
                Text(
                    "群公告",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.bulletinLoading && state.bulletins.isEmpty()) {
                item(key = "group-bulletin-loading") {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(12.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (state.bulletins.isEmpty()) {
                item(key = "group-bulletin-empty") {
                    Text(
                        state.bulletinError ?: "暂无活动群公告",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            } else {
                state.bulletins.forEach { bulletin ->
                    item(key = "group-bulletin:${bulletin.feedId}") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    if (bulletin.pinned) "置顶公告" else "群公告",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    bulletin.text.ifBlank { "该公告没有可显示的文字内容" },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                                Text(
                                    bulletinTimeText(bulletin.createTime),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun groupRoleLabel(role: String): String = when (role.uppercase(Locale.ROOT)) {
    "OWNER" -> "群主"
    "ADMIN" -> "管理员"
    "MEMBER" -> "成员"
    "STRANGER" -> "陌生人"
    else -> role
}

private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope.groupField(
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
    label: String,
    value: String,
) {
    item(key = "group-field:$label") {
        GroupField(transformationSpec, label, value)
    }
}

@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.GroupField(
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
    label: String,
    value: String,
) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .transformedHeight(this, transformationSpec)
                .padding(horizontal = 16.dp, vertical = 5.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text(value.ifBlank { "未知" }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
}

private fun bulletinTimeText(seconds: Int): String = runCatching {
    SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date(seconds.toLong() * 1000L))
}.getOrDefault("未知时间")
