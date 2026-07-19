package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonRemove
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
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
import rj.qmce.lite.data.chat.GroupMemberRepository
import rj.qmce.lite.viewmodel.GroupManagementState
import rj.qmce.lite.viewmodel.GroupManagementViewModel
import java.util.Locale

@Composable
fun GroupManagementScreen(
    groupCode: Long,
    vm: GroupManagementViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val state by vm.state.collectAsState()
    var pendingMute by remember(groupCode) { mutableStateOf<Boolean?>(null) }
    var showMembers by remember(groupCode) { mutableStateOf(false) }
    var showBulletin by remember(groupCode) { mutableStateOf(false) }

    LaunchedEffect(groupCode) {
        vm.load(groupCode)
    }

    if (showMembers) {
        GroupMemberManagementScreen(
            groupCode = groupCode,
            members = state.members,
            loading = state.membersLoading,
            error = state.membersError ?: state.memberActionError,
            busyUid = state.memberActionUid,
            canManage = state.canManage,
            onRefresh = { vm.load(groupCode, forceRefresh = true) },
            onClearError = {
                vm.clearMemberActionError()
                vm.load(groupCode, forceRefresh = true)
            },
            onKick = vm::kickMember,
            onBack = { showMembers = false },
        )
        return
    }

    if (showBulletin) {
        GroupBulletinEditorScreen(
            groupCode = groupCode,
            state = state,
            onRefresh = vm::refreshBulletins,
            onSave = vm::publishBulletin,
            onClearStatus = vm::clearBulletinStatus,
            onBack = { showBulletin = false },
        )
        return
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
                item(key = "group-management-members") {
                    Button(
                        onClick = { showMembers = true },
                        enabled = !state.busy && state.memberActionUid == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        icon = { Icon(Icons.Default.PersonRemove, contentDescription = null) },
                        secondaryLabel = { Text("移出成员需二次确认") },
                    ) { Text("成员管理") }
                }
                item(key = "group-management-bulletin") {
                    Button(
                        onClick = {
                            vm.clearBulletinStatus()
                            showBulletin = true
                        },
                        enabled = !state.busy && !state.bulletinSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        secondaryLabel = {
                            Text(if (state.bulletins.isEmpty()) "当前没有群公告" else "编辑最新群公告")
                        },
                    ) { Text("群公告") }
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
private fun GroupMemberManagementScreen(
    groupCode: Long,
    members: List<GroupMemberRepository.Member>,
    loading: Boolean,
    error: String?,
    busyUid: String?,
    canManage: Boolean,
    onRefresh: () -> Unit,
    onClearError: () -> Unit,
    onKick: (GroupMemberRepository.Member) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var query by remember(groupCode) { mutableStateOf("") }
    var pendingMember by remember(groupCode) {
        mutableStateOf<GroupMemberRepository.Member?>(null)
    }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val visibleMembers = remember(members, normalizedQuery) {
        members.filter { member ->
            normalizedQuery.isBlank() || listOf(
                member.displayName,
                member.nick,
                member.cardName,
                member.uid,
                member.uin.toString(),
            ).any { it.contains(normalizedQuery, ignoreCase = true) }
        }
    }

    pendingMember?.let { member ->
        GroupMemberKickConfirmScreen(
            member = member,
            busy = busyUid == member.uid,
            onConfirm = {
                onKick(member)
                pendingMember = null
            },
            onCancel = { pendingMember = null },
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
            item(key = "member-management-refresh") {
                androidx.wear.compose.material3.CompactButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .padding(vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新群成员")
                }
            }
            item(key = "member-management-header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("成员管理", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "移出成员后需要重新加群；管理员不能操作群主或其他管理员。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item(key = "member-management-search") {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                "搜索昵称、群名片、QQ号或 UID",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        inner()
                    },
                )
            }
            if (loading && members.isEmpty()) {
                item(key = "member-management-loading") {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(vertical = 12.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (members.isEmpty()) {
                item(key = "member-management-empty") {
                    Text(
                        "暂无可管理成员",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = visibleMembers,
                    key = { member ->
                        "manage-member:${member.uid.ifBlank { "uin:${member.uin}" }}:${member.entryIndex}"
                    },
                ) { member ->
                    val targetRole = member.role.uppercase(Locale.ROOT)
                    val targetProtected = targetRole == "OWNER" || targetRole == "ADMIN"
                    Button(
                        onClick = { pendingMember = member },
                        enabled = canManage && busyUid == null && !targetProtected && member.uid.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        secondaryLabel = {
                            Text(
                                when {
                                    targetProtected -> "受保护成员"
                                    member.uid.isBlank() -> "UID 不可用"
                                    busyUid == member.uid -> "处理中…"
                                    else -> "点击后确认移出"
                                },
                            )
                        },
                    ) {
                        Text(member.displayName, maxLines = 1)
                    }
                }
            }
            error?.takeIf(String::isNotBlank)?.let { message ->
                item(key = "member-management-error") {
                    Button(
                        onClick = onClearError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Text(message) }
                }
            }
        }
    }
}

@Composable
private fun GroupMemberKickConfirmScreen(
    member: GroupMemberRepository.Member,
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
            item(key = "member-kick-confirm-warning") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(
                        "移出 ${member.displayName}？",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "成员将离开本群；此操作由服务端执行，失败不会从本地列表移除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item(key = "member-kick-confirm-action") {
                Button(
                    onClick = onConfirm,
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(if (busy) "处理中…" else "确认移出") }
            }
            item(key = "member-kick-confirm-cancel") {
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

@Composable
private fun GroupBulletinEditorScreen(
    groupCode: Long,
    state: GroupManagementState,
    onRefresh: () -> Unit,
    onSave: (String, String?, Boolean) -> Unit,
    onClearStatus: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val current = state.bulletins.firstOrNull()
    var draft by remember(groupCode, current?.feedId) {
        mutableStateOf(current?.text.orEmpty())
    }
    var pinned by remember(groupCode, current?.feedId) {
        mutableStateOf(current?.pinned == true)
    }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "bulletin-editor-refresh") {
                androidx.wear.compose.material3.CompactButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .padding(vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新群公告")
                }
            }
            item(key = "bulletin-editor-title") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("编辑群公告", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "只有群主或管理员可以发布；失败时不会清空草稿。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item(key = "bulletin-editor-input") {
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        if (state.bulletinError != null || state.bulletinMessage != null) {
                            onClearStatus()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (draft.isBlank()) {
                            Text(
                                "输入群公告内容…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        inner()
                    },
                )
            }
            item(key = "bulletin-editor-pinned") {
                SwitchButton(
                    checked = pinned,
                    onCheckedChange = { pinned = it },
                    enabled = !state.bulletinSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("置顶公告") },
                    secondaryLabel = { Text(if (pinned) "发布后置顶" else "发布后不置顶") },
                )
            }
            item(key = "bulletin-editor-save") {
                Button(
                    onClick = { onSave(draft, current?.feedId, pinned) },
                    enabled = draft.isNotBlank() && !state.bulletinSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                ) { Text(if (state.bulletinSaving) "提交中…" else "发布群公告") }
            }
            state.bulletinMessage?.let { message ->
                item(key = "bulletin-editor-message") {
                    Text(
                        message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            state.bulletinError?.let { message ->
                item(key = "bulletin-editor-error") {
                    Text(
                        message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (state.bulletinLoading) {
                item(key = "bulletin-editor-loading") {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(vertical = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (state.bulletins.isNotEmpty()) {
                item(key = "bulletin-editor-existing-title") {
                    Text(
                        "当前公告",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(
                    items = state.bulletins,
                    key = { bulletin -> "bulletin:${bulletin.feedId}" },
                ) { bulletin ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            if (bulletin.pinned) "置顶公告" else "群公告",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(bulletin.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                item(key = "bulletin-editor-empty") {
                    Text(
                        "当前没有群公告",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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
