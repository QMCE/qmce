package rj.qmce.lite.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.data.chat.AtMention
import rj.qmce.lite.data.chat.DraftStore
import rj.qmce.lite.data.chat.GroupMemberRepository
import rj.qmce.lite.data.chat.MessageTokenCodec.BOUNDARY_END
import rj.qmce.lite.data.chat.MessageTokenCodec.BOUNDARY_START
import rj.qmce.lite.data.emotion.EmotionRepository
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import java.io.File
import java.util.UUID
import androidx.compose.material3.TextField as MaterialTextField
import androidx.compose.material3.TextFieldDefaults as MaterialTextFieldDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 不可见标记字符（Unicode 私用区），用户不会输入
private const val IMG_MARKER = ''

private const val AT_MARKER = ''
private const val FACE_MARKER = ''
private const val MARKET_FACE_MARKER = ''

data class ImageSlot(val id: String, val uri: Uri)

private sealed interface MarketFaceThumbnailState {
    data object Loading : MarketFaceThumbnailState
    data class Ready(val bitmap: Bitmap) : MarketFaceThumbnailState
    data object Unavailable : MarketFaceThumbnailState
}

@Composable
fun ChatInputScreen(
    vm: ChatDetailViewModel,
    peerUid: String = "",
    peerUin: String = "",
    chatType: Int = 1,
    editingText: String = "",
    replyTarget: ChatDetailViewModel.ReplyTarget? = null,
    onConsumeReplyTarget: () -> Unit = {},
    onSend: (String, ChatDetailViewModel.ReplyTarget?) -> Unit,
    onSendEdited: (String) -> Unit,
    onSendMixed: (
        String,
        Map<String, Uri>,
        Map<String, AtMention>,
        Map<String, EmotionRepository.Selection>,
        ChatDetailViewModel.ReplyTarget?,
    ) -> Unit,
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
    val faceSlots = remember { mutableStateListOf<EmotionRepository.Selection.SystemFace>() }
    val marketFaceSlots = remember { mutableStateListOf<EmotionRepository.Selection.MarketFace>() }
    var imagePickerSelection by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val atSlots = remember { mutableStateListOf<AtMention>() }
    val editSendState by vm.editSendState.collectAsState()
    val isEditSending = editSendState is ChatDetailViewModel.EditSendState.Sending
    var activeReplyTarget by remember(peerUid, chatType) {
        mutableStateOf<ChatDetailViewModel.ReplyTarget?>(null)
    }
    val groupMembers by vm.groupMembers.collectAsState()
    val scheme = MaterialTheme.colorScheme
    var showImagePicker by remember { mutableStateOf(false) }
    var pickerNotice by remember { mutableStateOf<String?>(null) }
    var pendingCameraAction by remember { mutableStateOf<CameraAction?>(null) }
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var showVideoPicker by remember { mutableStateOf(false) }
    var showToolPanel by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
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

    fun addAtMention(uid: String, displayName: String) {
        if (uid.isBlank()) {
            pickerNotice = "该成员缺少 UID，无法发送 @"
            return
        }
        val cursor = textFieldValue.selection.end
        val slotIndex = textFieldValue.text.substring(0, cursor).count { it == AT_MARKER }
        val mention = AtMention(uid, displayName)
        atSlots.add(slotIndex.coerceIn(0, atSlots.size), mention)
        val text = textFieldValue.text
        val inserted = "$AT_MARKER "
        textFieldValue = TextFieldValue(
            text = text.substring(0, cursor) + inserted + text.substring(cursor),
            selection = TextRange(cursor + inserted.length),
        )
    }

    fun addAtMention(member: GroupMemberRepository.Member) {
        addAtMention(member.uid, member.displayName)
    }

    fun insertSystemFace(face: EmotionRepository.Selection.SystemFace) {
        val cursor = textFieldValue.selection.end
        val slotIndex = textFieldValue.text.substring(0, cursor).count { it == FACE_MARKER }
        val text = textFieldValue.text
        faceSlots.add(slotIndex.coerceIn(0, faceSlots.size), face)
        textFieldValue = TextFieldValue(
            text = text.substring(0, cursor) + FACE_MARKER + text.substring(cursor),
            selection = TextRange(cursor + 1),
        )
    }

    fun insertMarketFace(face: EmotionRepository.Selection.MarketFace) {
        val cursor = textFieldValue.selection.end
        val slotIndex = textFieldValue.text.substring(0, cursor).count { it == MARKET_FACE_MARKER }
        val text = textFieldValue.text
        marketFaceSlots.add(slotIndex.coerceIn(0, marketFaceSlots.size), face)
        textFieldValue = TextFieldValue(
            text = text.substring(0, cursor) + MARKET_FACE_MARKER + text.substring(cursor),
            selection = TextRange(cursor + 1),
        )
    }

    LaunchedEffect(replyTarget?.messageId) {
        val target = replyTarget ?: return@LaunchedEffect
        activeReplyTarget = target
        if (!isEditing && target.isGroupChat && target.senderUid.isNotBlank()) {
            addAtMention(target.senderUid, target.senderName)
        }
        onConsumeReplyTarget()
    }

    LaunchedEffect(editSendState) {
        when (val state = editSendState) {
            ChatDetailViewModel.EditSendState.Succeeded -> if (isEditing) {
                textFieldValue = TextFieldValue("")
                imageSlots.clear()
                faceSlots.clear()
                marketFaceSlots.clear()
                atSlots.clear()
                if (peerUid.isNotBlank()) DraftStore.clear(context, peerUid, chatType)
                vm.completeEditedSend()
                onBack()
            }

            is ChatDetailViewModel.EditSendState.Failed -> pickerNotice = "编辑失败：${state.message}"
            else -> Unit
        }
    }

    BackHandler(enabled = isEditSending) {
        pickerNotice = "正在保存修改"
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

    val videoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasVideoGalleryAccess(context)) {
            showVideoPicker = true
            pickerNotice = null
        } else {
            pickerNotice = "未获得视频访问权限"
        }
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
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera(action)
        } else {
            pendingCameraAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun completeImagePickerSelection(uris: List<Uri> = imagePickerSelection) {
        if (uris.isNotEmpty()) addImageUris(uris)
        imagePickerSelection = emptyList()
        showImagePicker = false
    }

    if (showImagePicker) {
        LocalImagePickerScreen(
            existingUris = imageSlots.mapTo(linkedSetOf()) { it.uri.toString() },
            selectedUris = imagePickerSelection.map(Uri::toString),
            onSelectionChange = { uris -> imagePickerSelection = uris },
            onDismiss = ::completeImagePickerSelection,
            onConfirm = ::completeImagePickerSelection,
        )
        return
    }

    if (showVideoPicker) {
        LocalVideoPickerScreen(
            onDismiss = { showVideoPicker = false },
            onConfirm = { uri ->
                showVideoPicker = false
                showToolPanel = false
                onSendVideo(uri)
            },
        )
        return
    }

    if (showEmojiPicker) {
        EmotionPickerScreen(
            context = context,
            onSelectSystemFace = { face ->
                insertSystemFace(face)
                showEmojiPicker = false
            },
            onSelectMarketFace = { face ->
                insertMarketFace(face)
                showEmojiPicker = false
            },
            onBack = { showEmojiPicker = false },
        )
        return
    }

    if (showToolPanel) {
        ChatInputToolsScreen(
            isGroupChat = chatType == 2 && peerUin.toLongOrNull() != null,
            onBack = { showToolPanel = false },
            onSelectImage = {
                showToolPanel = false
                if (hasGalleryAccess(context)) {
                    showImagePicker = true
                    pickerNotice = null
                } else {
                    galleryPermissionLauncher.launch(galleryReadPermissions())
                }
            },
            onSelectVideo = {
                if (hasVideoGalleryAccess(context)) {
                    showVideoPicker = true
                    pickerNotice = null
                } else {
                    videoPermissionLauncher.launch(videoReadPermissions())
                }
            },
            onCapturePhoto = {
                showToolPanel = false
                launchCamera(CameraAction.Photo)
            },
            onCaptureVideo = {
                showToolPanel = false
                launchCamera(CameraAction.Video)
            },
            onSelectMember = {
                val uin = peerUin.toLongOrNull() ?: return@ChatInputToolsScreen
                showToolPanel = false
                vm.loadGroupMembers(uin)
                showAtPicker = true
            },
            onSelectEmoji = {
                showToolPanel = false
                showEmojiPicker = true
            },
            onRecordVoice = {
                showToolPanel = false
                onOpenVoiceRecorder()
            },
        )
        return
    }

    if (showAtPicker) {
        AtMemberPickerScreen(
            query = atQuery,
            members = groupMembers,
            errorMessage = vm.groupMembersError.value,
            onQueryChange = { atQuery = it },
            onSelect = { member ->
                addAtMention(member)
                if (member.uid.isNotBlank()) {
                    showAtPicker = false
                    atQuery = ""
                }
            },
            onBack = {
                showAtPicker = false
                atQuery = ""
            },
        )
        return
    }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val canSend = textFieldValue.text
        .replace(IMG_MARKER.toString(), "")
        .replace(AT_MARKER.toString(), "")
        .replace(FACE_MARKER.toString(), "")
        .replace(MARKET_FACE_MARKER.toString(), "")
        .isNotBlank() || imageSlots.isNotEmpty() || atSlots.isNotEmpty()
        || faceSlots.isNotEmpty() || marketFaceSlots.isNotEmpty()

    ScreenScaffold(
        scrollState = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        edgeButton = {
            EdgeButton(
                onClick = {
                    val text = textFieldValue.text
                    val hasText = text
                        .replace(IMG_MARKER.toString(), "")
                        .replace(AT_MARKER.toString(), "")
                        .replace(FACE_MARKER.toString(), "")
                        .replace(MARKET_FACE_MARKER.toString(), "")
                        .isNotBlank()
                    val hasImages = imageSlots.isNotEmpty()
                    val hasMentions = atSlots.isNotEmpty()
                    val hasFaces = faceSlots.isNotEmpty() || marketFaceSlots.isNotEmpty()

                    if (!hasText && !hasImages && !hasMentions && !hasFaces) return@EdgeButton

                    if (isEditing) {
                        onSendEdited(text)
                        return@EdgeButton
                    }

                    if (hasImages || hasMentions || hasFaces) {
                        val uriMap = imageSlots.associate { it.id to it.uri }
                        val atMap =
                            atSlots.mapIndexed { index, mention -> "at-$index" to mention }.toMap()
                        val emotionMap = buildMap {
                            faceSlots.forEachIndexed { index, face ->
                                put("face:$index", face)
                            }
                            marketFaceSlots.forEachIndexed { index, face ->
                                put("market:$index", face)
                            }
                        }
                        var imageIndex = 0
                        var atIndex = 0
                        var faceIndex = 0
                        var marketFaceIndex = 0
                        val mappedText = buildString {
                            text.forEach { ch ->
                                when (ch) {
                                    IMG_MARKER -> {
                                        val id = imageSlots.getOrNull(imageIndex++)?.id.orEmpty()
                                        append(BOUNDARY_START).append("img:").append(id)
                                            .append(BOUNDARY_END)
                                    }

                                    AT_MARKER -> {
                                        append(BOUNDARY_START).append("at:").append("at-$atIndex")
                                            .append(BOUNDARY_END)
                                        atIndex++
                                    }

                                    FACE_MARKER -> {
                                        append(BOUNDARY_START).append("face:").append(faceIndex++)
                                            .append(BOUNDARY_END)
                                    }

                                    MARKET_FACE_MARKER -> {
                                        append(BOUNDARY_START).append("market:")
                                            .append(marketFaceIndex++)
                                            .append(BOUNDARY_END)
                                    }

                                    else -> append(ch)
                                }
                            }
                        }
                        onSendMixed(mappedText, uriMap, atMap, emotionMap, activeReplyTarget)
                    } else {
                        onSend(text, activeReplyTarget)
                    }
                    textFieldValue = TextFieldValue("")
                    imageSlots.clear()
                    faceSlots.clear()
                    marketFaceSlots.clear()
                    atSlots.clear()
                    activeReplyTarget = null
                    if (peerUid.isNotBlank()) DraftStore.clear(context, peerUid, chatType)
                    onBack()
                },
                enabled = canSend && !isEditSending,
                buttonSize = EdgeButtonSize.Medium,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        },
        edgeButtonSpacing = 2.5.dp
    ) {
        TransformingLazyColumn(
            state = listState,
            contentPadding = it,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isEditing) {
                item(key = "edit-banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContainerTransformation()
                                    applyContentTransformation()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                            .background(scheme.primaryContainer, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                vm.cancelEdit()
                                onBack()
                            },
                            enabled = !isEditSending,
                        ) {
                            Text("取消", color = scheme.onPrimaryContainer)
                        }
                    }
                }
            }
            activeReplyTarget?.let { target ->
                item(key = "reply-banner:${target.messageId}") {
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
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                            .background(scheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "回复 ${target.senderName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onPrimaryContainer,
                            )
                            TextButton(onClick = { activeReplyTarget = null }) {
                                Text("取消", color = scheme.onPrimaryContainer)
                            }
                        }
                        Text(
                            text = target.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onPrimaryContainer,
                        )
                    }
                }
            }
            pickerNotice?.let { notice ->
                item(key = "picker-notice") {
                    Text(
                        text = notice,
                        color = scheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContainerTransformation()
                                    applyContentTransformation()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                    )
                }
            }
            item(key = "input") {
                MaterialTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        // 检测标记被删除 → 移除对应图片
                        val oldMarkers = textFieldValue.text.count { it == IMG_MARKER }
                        val newMarkers = newValue.text.count { it == IMG_MARKER }
                        if (newMarkers < oldMarkers) {
                            // 找出哪个标记被删了（位置法）
                            val removedIdx =
                                findRemovedMarkerIndex(textFieldValue.text, newValue.text)
                            if (removedIdx in imageSlots.indices) {
                                imageSlots.removeAt(removedIdx)
                            }
                        }
                        val oldAtMarkers = textFieldValue.text.count { it == AT_MARKER }
                        val newAtMarkers = newValue.text.count { it == AT_MARKER }
                        if (newAtMarkers < oldAtMarkers) {
                            val removedIdx = findRemovedMarkerIndex(
                                textFieldValue.text,
                                newValue.text,
                                AT_MARKER
                            )
                            if (removedIdx in atSlots.indices) atSlots.removeAt(removedIdx)
                        }
                        val oldFaceMarkers = textFieldValue.text.count { it == FACE_MARKER }
                        val newFaceMarkers = newValue.text.count { it == FACE_MARKER }
                        if (newFaceMarkers < oldFaceMarkers) {
                            val removedIdx = findRemovedMarkerIndex(
                                textFieldValue.text,
                                newValue.text,
                                FACE_MARKER,
                            )
                            if (removedIdx in faceSlots.indices) faceSlots.removeAt(removedIdx)
                        }
                        val oldMarketFaceMarkers =
                            textFieldValue.text.count { it == MARKET_FACE_MARKER }
                        val newMarketFaceMarkers = newValue.text.count { it == MARKET_FACE_MARKER }
                        if (newMarketFaceMarkers < oldMarketFaceMarkers) {
                            val removedIdx = findRemovedMarkerIndex(
                                textFieldValue.text,
                                newValue.text,
                                MARKET_FACE_MARKER,
                            )
                            if (removedIdx in marketFaceSlots.indices) {
                                marketFaceSlots.removeAt(removedIdx)
                            }
                        }
                        textFieldValue = newValue
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 10.dp)
                        .defaultMinSize(minHeight = 100.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    visualTransformation = imgMarkerTransformation(
                        imageCount = imageSlots.size,
                        atMentions = atSlots,
                        faceCount = faceSlots.size,
                        marketFaceCount = marketFaceSlots.size,
                        highlightColor = scheme.primary,
                    ),
                    placeholder = {
                        Text("输入消息", style = MaterialTheme.typography.bodySmall)
                    },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    colors = MaterialTextFieldDefaults.colors(
                        focusedContainerColor = scheme.surfaceContainerHigh,
                        unfocusedContainerColor = scheme.surfaceContainerHigh,
                        disabledContainerColor = scheme.surfaceContainerHigh,
                        focusedTextColor = scheme.onSurface,
                        unfocusedTextColor = scheme.onSurface,
                        cursorColor = scheme.primary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedPlaceholderColor = scheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
                    ),
                )
            }
            item(key = "more") {
                Button(
                    onClick = { showToolPanel = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 10.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                        )
                    },
                ) { Text("更多") }
            }
        }
    }
}

@Composable
private fun ChatInputToolsScreen(
    isGroupChat: Boolean,
    onBack: () -> Unit,
    onSelectImage: () -> Unit,
    onSelectVideo: () -> Unit,
    onCapturePhoto: () -> Unit,
    onCaptureVideo: () -> Unit,
    onSelectMember: () -> Unit,
    onSelectEmoji: () -> Unit,
    onRecordVoice: () -> Unit,
) {
    var showVideoActions by remember { mutableStateOf(false) }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    BackHandler(onBack = onBack)
    ScreenScaffold(scrollState = listState) { contentPadding ->
        androidx.wear.compose.foundation.lazy.TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "emoji") {
                ChatInputToolButton(
                    icon = Icons.Default.EmojiEmotions,
                    label = "表情",
                    description = "插入常用表情",
                    onClick = onSelectEmoji,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "image") {
                ChatInputToolButton(
                    icon = Icons.Default.Image,
                    label = "图片",
                    description = "从本地图片库选择",
                    onClick = onSelectImage,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "photo") {
                ChatInputToolButton(
                    icon = Icons.Default.PhotoCamera,
                    label = "拍照",
                    description = "拍摄后插入到消息中",
                    onClick = onCapturePhoto,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "video") {
                ChatInputToolButton(
                    icon = Icons.Default.VideoLibrary,
                    label = "视频",
                    description = "从本地选择或直接拍摄",
                    onClick = { showVideoActions = !showVideoActions },
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            if (showVideoActions) {
                item(key = "video-library") {
                    ChatInputToolButton(
                        icon = Icons.Default.VideoLibrary,
                        label = "选择本地视频",
                        description = "浏览系统媒体库",
                        onClick = onSelectVideo,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
                item(key = "video-capture") {
                    ChatInputToolButton(
                        icon = Icons.Default.PhotoCamera,
                        label = "拍摄视频",
                        description = "使用系统相机拍摄",
                        onClick = onCaptureVideo,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
            item(key = "voice") {
                ChatInputToolButton(
                    icon = Icons.Default.Mic,
                    label = "语音",
                    description = "按住录制语音消息",
                    onClick = onRecordVoice,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            if (isGroupChat) {
                item(key = "mention") {
                    ChatInputToolButton(
                        icon = Icons.Default.AlternateEmail,
                        label = "@成员",
                        description = "从群成员中选择",
                        onClick = onSelectMember,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
        }
    }
}

@Composable
fun EmotionPickerScreen(
    context: android.content.Context,
    onSelectSystemFace: (EmotionRepository.Selection.SystemFace) -> Unit,
    onSelectMarketFace: (EmotionRepository.Selection.MarketFace) -> Unit,
    onBack: () -> Unit,
) {
    var systemFaces by remember { mutableStateOf<List<EmotionRepository.Selection.SystemFace>>(emptyList()) }
    var marketPacks by remember { mutableStateOf<List<EmotionRepository.MarketPack>>(emptyList()) }
    var selectedPack by remember { mutableStateOf<EmotionRepository.MarketPack?>(null) }
    var marketFaces by remember { mutableStateOf<List<EmotionRepository.Selection.MarketFace>>(emptyList()) }
    var loadingMarketFaces by remember { mutableStateOf(false) }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        systemFaces = EmotionRepository.loadSystemFaces()
        EmotionRepository.loadMarketPacks { marketPacks = it }
    }
    LaunchedEffect(selectedPack?.epId) {
        val pack = selectedPack ?: run {
            marketFaces = emptyList()
            return@LaunchedEffect
        }
        loadingMarketFaces = true
        EmotionRepository.loadMarketFaces(context, pack.epId) {
            marketFaces = it
            loadingMarketFaces = false
        }
    }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "emoji-title") {
                Text(
                    text = if (selectedPack == null) "表情" else selectedPack?.name.orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (selectedPack == null) {
                item(key = "system-face-heading") {
                    Text(
                        text = "系统表情",
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                systemFaces.chunked(4).forEachIndexed { rowIndex, row ->
                    item(key = "system-face-row:$rowIndex") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { face ->
                                EmotionOptionButton(
                                    label = face.label,
                                    drawable = remember(face.faceType, face.faceIndex) {
                                        EmotionRepository.systemFaceDrawable(face)
                                    },
                                    onClick = { onSelectSystemFace(face) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                item(key = "market-face-heading") {
                    Text(
                        text = "系列表情",
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                marketPacks.forEach { pack ->
                    item(key = "market-pack:${pack.epId}") {
                        Button(
                            onClick = { selectedPack = pack },
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text(pack.name)
                        }
                    }
                }
                if (marketPacks.isEmpty()) {
                    item(key = "market-pack-empty") {
                        Text(
                            text = "暂无可用系列表情",
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                item(key = "market-back") {
                    TextButton(
                        onClick = { selectedPack = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    ) {
                        Text("返回表情包")
                    }
                }
                if (loadingMarketFaces) {
                    item(key = "market-loading") {
                        Text(
                            text = "正在加载表情…",
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                marketFaces.chunked(3).forEachIndexed { rowIndex, row ->
                    item(key = "market-face-row:$rowIndex") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            row.forEach { face ->
                                MarketFaceOptionButton(
                                    face = face,
                                    onClick = { onSelectMarketFace(face) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (!loadingMarketFaces && marketFaces.isEmpty()) {
                    item(key = "market-face-empty") {
                        Text(
                            text = "该表情包暂时没有可用资源",
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmotionOptionButton(
    label: String,
    drawable: Drawable?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Button(onClick = onClick, modifier = modifier) {
        if (drawable == null) {
            Text("表情")
        } else {
            val bitmap = remember(drawable) { drawable.toPreviewBitmap() }
            if (bitmap == null) Text("表情") else {
                ComposeImage(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun MarketFaceOptionButton(
    face: EmotionRepository.Selection.MarketFace,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    var previewPath by remember(face.epId, face.eId, face.staticPath) {
        mutableStateOf<String?>(null)
    }
    var previewResolved by remember(face.epId, face.eId, face.staticPath) {
        mutableStateOf(false)
    }
    LaunchedEffect(face.epId, face.eId, face.staticPath) {
        previewResolved = false
        EmotionRepository.resolveMarketFacePreview(face) { path ->
            previewPath = path
            previewResolved = true
        }
    }
    val thumbnailState by produceState<MarketFaceThumbnailState>(
        initialValue = MarketFaceThumbnailState.Loading,
        previewPath,
        previewResolved,
    ) {
        value = when {
            !previewResolved -> MarketFaceThumbnailState.Loading
            previewPath.isNullOrBlank() -> MarketFaceThumbnailState.Unavailable
            else -> withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(previewPath)
            }?.let(MarketFaceThumbnailState::Ready) ?: MarketFaceThumbnailState.Unavailable
        }
    }
    Button(onClick = onClick, modifier = modifier) {
        when (val state = thumbnailState) {
            MarketFaceThumbnailState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }

            is MarketFaceThumbnailState.Ready -> {
                ComposeImage(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = face.label,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            MarketFaceThumbnailState.Unavailable -> Text(face.label)
        }
    }
}

private fun Drawable.toPreviewBitmap(): Bitmap? = runCatching {
    val width = intrinsicWidth.takeIf { it > 0 } ?: 48
    val height = intrinsicHeight.takeIf { it > 0 } ?: 48
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        setBounds(0, 0, width, height)
        draw(Canvas(bitmap))
    }
}.getOrNull()

@Composable
private fun ChatInputToolButton(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        transformation = transformation,
        colors = ButtonDefaults.filledTonalButtonColors(),
        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.LargeIconSize),
            )
        },
        secondaryLabel = { Text(description) },
    ) { Text(label) }
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
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
    )

    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasGalleryAccess(context: android.content.Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_IMAGES,
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                ) == PackageManager.PERMISSION_GRANTED
    }

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES,
        ) == PackageManager.PERMISSION_GRANTED
    }

    else -> {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun videoReadPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
    )

    else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasVideoGalleryAccess(context: android.content.Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO,
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                ) == PackageManager.PERMISSION_GRANTED
    }

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    else -> {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/** VisualTransformation：将图片和 @ 标记显示为可读 token，偏移映射保证光标正确。 */
private fun imgMarkerTransformation(
    imageCount: Int,
    atMentions: List<AtMention>,
    faceCount: Int,
    marketFaceCount: Int,
    highlightColor: Color,
): VisualTransformation {
    return VisualTransformation { original ->
        val text = original.text
        val display = AnnotatedString.Builder()
        // origOffsets[i] = 原文本中对应 display 第 i 个字符的位置
        val origOffsets = mutableListOf<Int>()

        var slotIdx = 0
        var atIdx = 0
        var faceIdx = 0
        var marketFaceIdx = 0
        for (i in text.indices) {
            if (text[i] == IMG_MARKER) {
                val label = if (imageCount > 1) "[图片${slotIdx + 1}]" else "[图片]"
                display.withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(label)
                }
                // label 的每个字符都映射到原文的同一个位置 i
                repeat(label.length) { origOffsets.add(i) }
                slotIdx++
            } else if (text[i] == AT_MARKER) {
                val label = "@${atMentions.getOrNull(atIdx)?.nick ?: "成员"}"
                display.withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(label)
                }
                repeat(label.length) { origOffsets.add(i) }
                atIdx++
            } else if (text[i] == FACE_MARKER) {
                val label = if (faceCount > 1) "[表情${faceIdx + 1}]" else "[表情]"
                display.withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(label)
                }
                repeat(label.length) { origOffsets.add(i) }
                faceIdx++
            } else if (text[i] == MARKET_FACE_MARKER) {
                val label = if (marketFaceCount > 1) "[大表情${marketFaceIdx + 1}]" else "[大表情]"
                display.withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(label)
                }
                repeat(label.length) { origOffsets.add(i) }
                marketFaceIdx++
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
                if (offset in revMap.indices) revMap[offset].coerceIn(
                    0,
                    origArr.size
                ) else origArr.size

            override fun transformedToOriginal(offset: Int): Int =
                if (offset in origArr.indices) origArr[offset] else text.length
        }

        TransformedText(display.toAnnotatedString(), mapping)
    }
}

/** 找出从 oldText 到 newText 中被删掉的指定标记序号（0-based）。 */
private fun findRemovedMarkerIndex(
    oldText: String,
    newText: String,
    marker: Char = IMG_MARKER
): Int {
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
