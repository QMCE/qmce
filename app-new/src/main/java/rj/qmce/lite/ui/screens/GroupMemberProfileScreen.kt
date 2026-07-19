package rj.qmce.lite.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import rj.qmce.lite.data.chat.GroupMemberRepository
import rj.qmce.lite.data.media.MediaStoreSaver
import java.io.File
import java.util.Locale

@Composable
fun GroupMemberProfileScreen(
    groupCode: Long,
    member: GroupMemberRepository.Member,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val saveScope = rememberCoroutineScope()
    val saver = remember { MediaStoreSaver() }
    var avatarTarget by remember(member.entryIndex, member.uid, member.uin) {
        mutableStateOf<AvatarPreviewTarget?>(null)
    }
    var saveLabel by remember { mutableStateOf("保存") }
    val localAvatar = member.avatarPath.removePrefix("file://")
        .takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf(File::isFile)
    val avatarUrls = remember(member.uin) {
        member.uin.takeIf { it > 0L }?.let { uin ->
            listOf(
                "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=100",
                "https://q2.qlogo.cn/headimg_dl?dst_uin=$uin&spec=100",
                "https://qlogo2.store.qq.com/qzone/$uin/$uin/100",
            )
        }.orEmpty()
    }
    val avatarModel: Any? = localAvatar ?: avatarUrls.firstOrNull()
    val avatarSource = localAvatar?.absolutePath ?: avatarUrls.firstOrNull()

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
                            saver.saveImage(context, target.source)
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
    val displayName = member.displayName.ifBlank { "群成员" }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "member-profile-header:${member.entryIndex}") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    onClick = {
                        if (avatarModel != null && avatarSource != null) {
                            avatarTarget = AvatarPreviewTarget(
                                media = ViewerMedia(
                                    key = "group-member-avatar:${member.uid}:${member.uin}:${member.entryIndex}",
                                    model = avatarModel,
                                    description = "${displayName}的头像",
                                ),
                                source = avatarSource,
                            )
                        }
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                displayName.firstOrNull()?.toString() ?: "?",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (avatarModel != null) {
                                AsyncImage(
                                    model = avatarModel,
                                    contentDescription = "${displayName}的头像",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        Text(
                            displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            member.roleLabelForUi(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item(key = "member-profile-copy:${member.entryIndex}") {
                Button(
                    onClick = {
                        copyMemberValue(
                            context,
                            "QQ号",
                            member.uin.takeIf { it > 0L }?.toString() ?: member.uid,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                ) { Text("复制 QQ 号") }
            }
            memberProfileField(
                key = "member-profile-uin:${member.entryIndex}",
                label = "QQ号",
                value = member.uin.takeIf { it > 0L }?.toString() ?: "未知",
                transformationSpec = transformationSpec,
            )
            memberProfileField(
                key = "member-profile-uid:${member.entryIndex}",
                label = "UID",
                value = member.uid.ifBlank { "未知" },
                transformationSpec = transformationSpec,
            )
            memberProfileField(
                key = "member-profile-card:${member.entryIndex}",
                label = "群名片",
                value = member.cardName.ifBlank { "未设置" },
                transformationSpec = transformationSpec,
            )
            memberProfileField(
                key = "member-profile-nick:${member.entryIndex}",
                label = "昵称",
                value = member.nick.ifBlank { "未知" },
                transformationSpec = transformationSpec,
            )
            member.memberLevel?.takeIf { it > 0 }?.let { level ->
                memberProfileField(
                    key = "member-profile-level:${member.entryIndex}",
                    label = "群等级",
                    value = "LV$level",
                    transformationSpec = transformationSpec,
                )
            }
            memberProfileField(
                key = "member-profile-group:${member.entryIndex}",
                label = "群号",
                value = groupCode.takeIf { it > 0L }?.toString() ?: "未知",
                transformationSpec = transformationSpec,
            )
        }
    }
}

private fun GroupMemberRepository.Member.roleLabelForUi(): String = when (role.uppercase(Locale.ROOT)) {
    "OWNER" -> "群主"
    "ADMIN" -> "管理员"
    "MEMBER" -> "成员"
    "STRANGER" -> "陌生人"
    else -> "群成员"
}

private fun copyMemberValue(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope.memberProfileField(
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
    key: String,
    label: String,
    value: String,
) {
    item(key = key) {
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
                .padding(horizontal = 16.dp, vertical = 5.dp),
        ) {
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
