package rj.qmce.lite.ui.screens

import android.net.Uri
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.*
import java.util.UUID
import java.io.File
import rj.qmce.lite.data.chat.DraftStore
import rj.qmce.lite.data.chat.AtMention
import rj.qmce.lite.data.chat.GroupMemberRepository

// 不可见标记字符（Unicode 私用区），用户不会输入
private const val IMG_MARKER = ''
// 发送时包裹 slotId 的边界字符，和 IMG_MARKER 不同
private const val BOUNDARY_START = ''
private const val BOUNDARY_END = ''
private const val AT_MARKER = ''

data class ImageSlot(val id: String, val uri: Uri)

@Composable
fun ChatInputScreen(
    vm: rj.qmce.lite.viewmodel.ChatDetailViewModel,
    peerUid: String = "",
    peerUin: String = "",
    chatType: Int = 1,
    editingText: String = "",
    onSend: (String) -> Unit,
    onSendEdited: (String) -> Unit,
    onSendMixed: (String, Map<String, Uri>, Map<String, AtMention>) -> Unit,
    onSendFile: (Uri) -> Unit,
    onSendVideo: (Uri) -> Unit,
    onOpenVoiceRecorder: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isEditing = editingText.isNotBlank()
    val initialText = if (isEditing) editingText else DraftStore.load(context, peerUid, chatType)
    var textFieldValue by remember(peerUid, chatType, isEditing) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    val imageSlots = remember { mutableStateListOf<ImageSlot>() }
    val atSlots = remember { mutableStateListOf<AtMention>() }
    val groupMembers by vm.groupMembers.collectAsState()
    val scheme = MaterialTheme.colorScheme
    var showImagePicker by remember { mutableStateOf(false) }
    var pickerNotice by remember { mutableStateOf<String?>(null) }
    var pendingCameraAction by remember { mutableStateOf<CameraAction?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showVideoCaptureOptions by remember { mutableStateOf(false) }
    var showAtPicker by remember { mutableStateOf(false) }
    var atQuery by remember { mutableStateOf("") }

    fun addImageUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val cursor = textFieldValue.selection.end
        val markerIndex = textFieldValue.text
            .substring(0, cursor)
            .count { it == IMG_MARKER }
        val slots = uris.map { uri -> ImageSlot(UUID.randomUUID().toString(), uri) }
        imageSlots.addAll(markerIndex, slots)
        val markers = IMG_MARKER.toString().repeat(slots.size)
        val text = textFieldValue.text
        textFieldValue = TextFieldValue(
            text = text.substring(0, cursor) + markers + text.substring(cursor),
            selection = TextRange(cursor + markers.length),
        )
    }

    fun addAtMention(member: GroupMemberRepository.Member) {
        if (member.uid.isBlank()) {
            pickerNotice = "该成员缺少 UID，无法发送 @"
            return
        }
        val cursor = textFieldValue.selection.end
        val slotIndex = textFieldValue.text.substring(0, cursor).count { it == AT_MARKER }
        val mention = AtMention(member.uid, member.displayName)
        atSlots.add(slotIndex.coerceIn(0, atSlots.size), mention)
        val text = textFieldValue.text
        val inserted = "$AT_MARKER "
        textFieldValue = TextFieldValue(
            text = text.substring(0, cursor) + inserted + text.substring(cursor),
            selection = TextRange(cursor + inserted.length),
        )
    }

    // Auto-save draft on text changes (only when not editing)
    LaunchedEffect(peerUid, chatType, textFieldValue.text, isEditing) {
        if (!isEditing && peerUid.isNotBlank()) {
            DraftStore.save(context, peerUid, chatType, textFieldValue.text)
        }
    }

    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasGalleryAccess(context)) {
            showImagePicker = true
            pickerNotice = null
        } else {
            pickerNotice = "未获得图片访问权限"
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(onSendFile)
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(onSendVideo)
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (success && uri != null) addImageUris(listOf(uri))
        else uri?.let(::deleteCaptureUri)
    }

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo(),
    ) { success ->
        val uri = pendingVideoUri
        pendingVideoUri = null
        if (success && uri != null) onSendVideo(uri)
        else uri?.let(::deleteCaptureUri)
    }

    fun startCamera(action: CameraAction) {
        runCatching {
            when (action) {
                CameraAction.Photo -> {
                    val uri = createCaptureUri(context, "photo", "jpg")
                    pendingPhotoUri = uri
                    takePhotoLauncher.launch(uri)
                }
                CameraAction.Video -> {
                    val uri = createCaptureUri(context, "video", "mp4")
                    pendingVideoUri = uri
                    captureVideoLauncher.launch(uri)
                }
            }
        }.onFailure {
            pendingPhotoUri = null
            pendingVideoUri = null
            pickerNotice = "相机不可用"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingCameraAction
        pendingCameraAction = null
        if (granted && action != null) {
            startCamera(action)
        } else if (!granted) {
            pickerNotice = "需要相机权限"
        }
    }

    fun launchCamera(action: CameraAction) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera(action)
        } else {
            pendingCameraAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showImagePicker) {
        LocalImagePickerScreen(
            existingUris = imageSlots.mapTo(linkedSetOf()) { it.uri.toString() },
            onDismiss = { showImagePicker = false },
            onConfirm = { uris ->
                addImageUris(uris)
                showImagePicker = false
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹ 返回",
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 4.dp),
                color = Color.White,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text("编辑消息", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        if (isEditing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp)
                    .background(Color(0xFF4F378A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("编辑中", color = Color(0xFFD9C4FF), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "取消",
                    color = Color(0xFFD1CBD7), fontSize = 10.sp,
                    modifier = Modifier.clickable {
                        vm.cancelEdit()
                        onBack()
                    },
                )
            }
        }
        pickerNotice?.let { notice ->
            Text(
                text = notice,
                color = scheme.error,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }
        // 可滚动输入区域
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            reverseLayout = false
        ) {
            item {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        // 检测标记被删除 → 移除对应图片
                        val oldMarkers = textFieldValue.text.count { it == IMG_MARKER }
                        val newMarkers = newValue.text.count { it == IMG_MARKER }
                        if (newMarkers < oldMarkers) {
                            // 找出哪个标记被删了（位置法）
                            val removedIdx = findRemovedMarkerIndex(textFieldValue.text, newValue.text)
                            if (removedIdx in imageSlots.indices) {
                                imageSlots.removeAt(removedIdx)
                            }
                        }
                        val oldAtMarkers = textFieldValue.text.count { it == AT_MARKER }
                        val newAtMarkers = newValue.text.count { it == AT_MARKER }
                        if (newAtMarkers < oldAtMarkers) {
                            val removedIdx = findRemovedMarkerIndex(textFieldValue.text, newValue.text, AT_MARKER)
                            if (removedIdx in atSlots.indices) atSlots.removeAt(removedIdx)
                        }
                        textFieldValue = newValue
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1D1B20), RoundedCornerShape(16.dp))
                        .padding(horizontal = 4.dp)
                        .defaultMinSize(minHeight = 88.dp),
                    textStyle = TextStyle(fontSize = 13.sp, color = Color.White),
                    cursorBrush = SolidColor(scheme.primary),
                    visualTransformation = imgMarkerTransformation(imageSlots.size, atSlots),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.padding(10.dp)) {
                            if (textFieldValue.text.isEmpty()) {
                                Text("输入消息", fontSize = 12.sp, color = scheme.outline)
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 功能按钮行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(Icons.Default.Image, "图片") {
                if (hasGalleryAccess(context)) {
                    showImagePicker = true
                    pickerNotice = null
                } else {
                    galleryPermissionLauncher.launch(galleryReadPermissions())
                }
            }
            CircleIconButton(Icons.Default.AttachFile, "文件") {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
            CircleIconButton(Icons.Default.VideoLibrary, "视频") {
                showVideoCaptureOptions = true
            }
            CircleIconButton(Icons.Default.PhotoCamera, "拍照") {
                launchCamera(CameraAction.Photo)
            }
            if (chatType == 2 && peerUin.toLongOrNull() != null) {
                CircleIconButton(Icons.Default.AlternateEmail, "@成员") {
                    vm.loadGroupMembers(peerUin.toLong())
                    showAtPicker = true
                }
            }
            CircleIconButton(Icons.Default.Mic, "语音", onOpenVoiceRecorder)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 发送按钮
        Button(
            onClick = {
                val text = textFieldValue.text
                val hasText = text
                    .replace(IMG_MARKER.toString(), "")
                    .replace(AT_MARKER.toString(), "")
                    .isNotBlank()
                val hasImages = imageSlots.isNotEmpty()
                val hasMentions = atSlots.isNotEmpty()

                if (!hasText && !hasImages && !hasMentions) return@Button

                if (hasImages || hasMentions) {
                    val uriMap = imageSlots.associate { it.id to it.uri }
                    val atMap = atSlots.mapIndexed { index, mention -> "at-$index" to mention }.toMap()
                    var imageIndex = 0
                    var atIndex = 0
                    val mappedText = buildString {
                        text.forEach { ch ->
                            when (ch) {
                                IMG_MARKER -> {
                                    val id = imageSlots.getOrNull(imageIndex++)?.id.orEmpty()
                                    append(BOUNDARY_START).append("img:").append(id).append(BOUNDARY_END)
                                }
                                AT_MARKER -> {
                                    append(BOUNDARY_START).append("at:").append("at-$atIndex").append(BOUNDARY_END)
                                    atIndex++
                                }
                                else -> append(ch)
                            }
                        }
                    }
                    onSendMixed(mappedText, uriMap, atMap)
                } else if (isEditing) {
                    onSendEdited(text)
                } else {
                    onSend(text)
                }
                textFieldValue = TextFieldValue("")
                imageSlots.clear()
                atSlots.clear()
                if (peerUid.isNotBlank()) DraftStore.clear(context, peerUid, chatType)
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(36.dp),
            enabled = textFieldValue.text.replace(IMG_MARKER.toString(), "").isNotBlank() || imageSlots.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4F378A),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, "发送", Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("发送", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }

    if (showVideoCaptureOptions) {
        AlertDialog(
            visible = true,
            onDismissRequest = { showVideoCaptureOptions = false },
            title = { Text("发送视频") },
            confirmButton = {},
            dismissButton = {},
            content = {
                item {
                    Button(
                        onClick = {
                            showVideoCaptureOptions = false
                            videoPickerLauncher.launch("video/*")
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) { Text("选择视频", fontSize = 12.sp) }
                }
                item {
                    Button(
                        onClick = {
                            showVideoCaptureOptions = false
                            launchCamera(CameraAction.Video)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    ) { Text("拍摄视频", fontSize = 12.sp) }
                }
            },
        )
    }

    if (showAtPicker) {
        val normalizedQuery = atQuery.trim()
        val visibleMembers = groupMembers.filter { member ->
            normalizedQuery.isBlank() || listOf(
                member.displayName,
                member.nick,
                member.cardName,
                member.uid,
                member.uin.toString(),
            ).any { it.contains(normalizedQuery, ignoreCase = true) }
        }
        AlertDialog(
            visible = true,
            onDismissRequest = {
                showAtPicker = false
                atQuery = ""
            },
            title = { Text("@成员") },
            confirmButton = {},
            dismissButton = {},
            content = {
                item {
                    BasicTextField(
                        value = atQuery,
                        onValueChange = { atQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .background(scheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = scheme.onSurface,
                            fontSize = 11.sp,
                        ),
                        cursorBrush = SolidColor(scheme.primary),
                        decorationBox = { inner ->
                            if (atQuery.isBlank()) Text("搜索昵称、群名片、QQ号或 UID", fontSize = 11.sp, color = scheme.outline)
                            inner()
                        },
                    )
                }
                if (groupMembers.isEmpty()) {
                    item {
                        Text(
                            text = vm.groupMembersError.value ?: "暂无可用群成员",
                            fontSize = 11.sp,
                            color = scheme.outline,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                } else if (visibleMembers.isEmpty()) {
                    item { Text("没有匹配成员", fontSize = 11.sp, modifier = Modifier.padding(12.dp)) }
                } else {
                    visibleMembers.take(40).forEach { member ->
                        item {
                            Button(
                                onClick = {
                                    addAtMention(member)
                                    if (member.uid.isNotBlank()) {
                                        showAtPicker = false
                                        atQuery = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = scheme.surfaceContainerHigh,
                                    contentColor = scheme.onSurface,
                                ),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(member.displayName, fontSize = 11.sp, maxLines = 1)
                                    Text(
                                        member.uin.takeIf { it > 0L }?.toString() ?: member.uid,
                                        fontSize = 9.sp,
                                        color = scheme.outline,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

private enum class CameraAction { Photo, Video }

private fun createCaptureUri(
    context: android.content.Context,
    prefix: String,
    extension: String,
): Uri {
    val directory = File(context.cacheDir, "shared-media")
    check(directory.exists() || directory.mkdirs()) { "无法创建相机临时目录" }
    val file = File(directory, "qmce_${prefix}_${System.currentTimeMillis()}.$extension")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

private fun deleteCaptureUri(uri: Uri) {
    runCatching {
        val path = uri.path ?: return@runCatching
        File(path).takeIf(File::exists)?.delete()
    }
}

private fun galleryReadPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
    )
    else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasGalleryAccess(context: android.content.Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_IMAGES,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_IMAGES,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    else -> {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

/** VisualTransformation：将图片和 @ 标记显示为可读 token，偏移映射保证光标正确。 */
private fun imgMarkerTransformation(
    imageCount: Int,
    atMentions: List<AtMention>,
): VisualTransformation {
    return VisualTransformation { original ->
        val text = original.text
        val display = AnnotatedString.Builder()
        // origOffsets[i] = 原文本中对应 display 第 i 个字符的位置
        val origOffsets = mutableListOf<Int>()

        var slotIdx = 0
        var atIdx = 0
        for (i in text.indices) {
            if (text[i] == IMG_MARKER) {
                val label = if (imageCount > 1) "[图片${slotIdx + 1}]" else "[图片]"
                display.withStyle(SpanStyle(color = Color(0xFF80CBC4), fontWeight = FontWeight.Bold)) {
                    append(label)
                }
                // label 的每个字符都映射到原文的同一个位置 i
                repeat(label.length) { origOffsets.add(i) }
                slotIdx++
            } else if (text[i] == AT_MARKER) {
                val label = "@${atMentions.getOrNull(atIdx)?.nick ?: "成员"}"
                display.withStyle(SpanStyle(color = Color(0xFF80CBC4), fontWeight = FontWeight.Bold)) {
                    append(label)
                }
                repeat(label.length) { origOffsets.add(i) }
                atIdx++
            } else {
                display.append(text[i])
                origOffsets.add(i)
            }
        }

        // 构建双向 offset mapping
        val origArr = origOffsets.toIntArray()
        // displayToOrig: 找 display 位置在 origArr 中对应的原始位置
        // origToDisplay: 找原始位置 i 在 origArr 中第一次出现的位置
        val revMap = IntArray(text.length + 1) { -1 }
        for (d in origArr.indices) {
            val o = origArr[d]
            if (revMap[o] == -1) revMap[o] = d
        }
        // 原文本末尾位置
        if (text.isNotEmpty() && revMap[text.length] == -1) {
            revMap[text.length] = origArr.size
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (offset in revMap.indices) revMap[offset].coerceIn(0, origArr.size) else origArr.size
            override fun transformedToOriginal(offset: Int): Int =
                if (offset in origArr.indices) origArr[offset] else text.length
        }

        TransformedText(display.toAnnotatedString(), mapping)
    }
}

/** 找出从 oldText 到 newText 中被删掉的指定标记序号（0-based）。 */
private fun findRemovedMarkerIndex(oldText: String, newText: String, marker: Char = IMG_MARKER): Int {
    var markerIdx = 0
    var j = 0
    for (i in oldText.indices) {
        if (j < newText.length && oldText[i] == newText[j]) {
            j++ // 同步前进
        } else if (oldText[i] == marker) {
            return markerIdx // 这个 marker 在 newText 中不存在，就是被删的
        }
        if (oldText[i] == marker) markerIdx++
    }
    return -1
}

@Composable
private fun CircleIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFF1D1B20), CircleShape)
        ) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Text(label, fontSize = 9.sp, color = scheme.outline)
    }
}
