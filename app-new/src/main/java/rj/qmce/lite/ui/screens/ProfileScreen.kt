package rj.qmce.lite.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.content.ClipData
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
import androidx.compose.material.icons.automirrored.filled.Message
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
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
import rj.qmce.lite.viewmodel.ContactsViewModel
import java.io.File

@Composable
fun ProfileScreen(
    buddy: ContactsViewModel.UiBuddy,
    onOpenChat: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val saveScope = rememberCoroutineScope()
    val mediaStoreSaver = remember { MediaStoreSaver() }
    var avatarTarget by remember(buddy.uid, buddy.uin) { mutableStateOf<AvatarPreviewTarget?>(null) }
    var saveLabel by remember { mutableStateOf("保存") }
    val localAvatar = buddy.avatarPath.removePrefix("file://")
        .takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf(File::isFile)
    val avatarModel: Any? = localAvatar ?: buddy.avatarUrls.firstOrNull()
    val avatarSource = localAvatar?.absolutePath ?: buddy.avatarUrls.firstOrNull()
    val displayName = buddy.remark.ifBlank { buddy.nick }.ifBlank { "QQ用户" }

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
            item(key = "profile-header:${buddy.uid}") {
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
                                                    key = "profile-avatar:${buddy.uid}:${buddy.uin}",
                                                    model = avatarModel,
                                                    description = "${displayName}的头像",
                                                ),
                                                source = avatarSource,
                                            )
                                        }
                                    } else Modifier,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                displayName.firstOrNull()?.toString() ?: "?",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (avatarModel != null) {
                                AsyncImage(
                                    model = avatarModel,
                                    contentDescription = "${displayName}的头像",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
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
                        if (buddy.nick.isNotBlank() && buddy.nick != displayName) {
                            Text(
                                "昵称：${buddy.nick}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            item(key = "profile-chat:${buddy.uid}") {
                Button(
                    onClick = onOpenChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledVariantButtonColors(),
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                ) { Text("发消息") }
            }
            item(key = "profile-copy:${buddy.uid}") {
                Button(
                    onClick = { copyProfileValue(context, "QQ号", buddy.uin.toString()) },
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
            item(key = "profile-field:QQ号") {
                ProfileField(
                    transformationSpec = transformationSpec,
                    label = "QQ号",
                    value = buddy.uin.takeIf { it > 0L }?.toString() ?: "未知",
                )
            }
            item(key = "profile-field:UID") {
                ProfileField(
                    transformationSpec = transformationSpec,
                    label = "UID",
                    value = buddy.uid.ifBlank { "未知" },
                )
            }
            item(key = "profile-field:分组") {
                ProfileField(
                    transformationSpec = transformationSpec,
                    label = "分组",
                    value = buddy.categoryName.ifBlank { "未分组" },
                )
            }
            if (buddy.remark.isNotBlank()) {
                item(key = "profile-field:备注") {
                    ProfileField(
                        transformationSpec = transformationSpec,
                        label = "备注",
                        value = buddy.remark,
                    )
                }
            }
        }
    }
}

private fun copyProfileValue(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.ProfileField(
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
    label: String,
    value: String,
) {
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
