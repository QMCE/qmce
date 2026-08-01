package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.data.notify.UiFriendRequest
import rj.qmce.lite.data.notify.UiGroupNotice
import rj.qmce.lite.ui.wear.QmceEmptyOrErrorState
import rj.qmce.lite.ui.wear.QmceListHeader
import rj.qmce.lite.ui.wear.QmceLoadingState
import rj.qmce.lite.viewmodel.FriendNotifyState
import rj.qmce.lite.viewmodel.GroupNotifyState
import rj.qmce.lite.viewmodel.NotificationCenterViewModel

private enum class NotificationSubPage { None, FriendRequests, GroupNotices }

@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    vm: NotificationCenterViewModel = viewModel(),
) {
    var subPage by remember { mutableStateOf(NotificationSubPage.None) }

    BackHandler(enabled = subPage != NotificationSubPage.None) {
        when (subPage) {
            NotificationSubPage.FriendRequests -> {
                vm.leaveFriendRequests()
                subPage = NotificationSubPage.None
            }
            NotificationSubPage.GroupNotices -> {
                vm.leaveGroupNotices()
                subPage = NotificationSubPage.None
            }
            NotificationSubPage.None -> onBack()
        }
    }

    when (subPage) {
        NotificationSubPage.FriendRequests -> {
            val state by vm.friendState.collectAsState()
            DisposableEffect(Unit) {
                vm.enterFriendRequests()
                onDispose { vm.leaveFriendRequests() }
            }
            FriendRequestsScreen(
                state = state,
                onBack = {
                    vm.leaveFriendRequests()
                    subPage = NotificationSubPage.None
                },
                onApprove = { item, accept ->
                    vm.approveFriendRequest(item.uid, item.reqTime, accept)
                },
            )
        }
        NotificationSubPage.GroupNotices -> {
            val state by vm.groupState.collectAsState()
            DisposableEffect(Unit) {
                vm.enterGroupNotices()
                onDispose { vm.leaveGroupNotices() }
            }
            GroupNoticesScreen(
                state = state,
                onBack = {
                    vm.leaveGroupNotices()
                    subPage = NotificationSubPage.None
                },
                onOperate = { item, accept ->
                    vm.operateGroupNotice(item, accept)
                },
            )
        }
        NotificationSubPage.None -> NotificationCenterMain(
            onOpenFriendRequests = { subPage = NotificationSubPage.FriendRequests },
            onOpenGroupNotices = { subPage = NotificationSubPage.GroupNotices },
            onBack = onBack,
        )
    }
}

@Composable
private fun NotificationCenterMain(
    onOpenFriendRequests: () -> Unit,
    onOpenGroupNotices: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "title") {
                QmceListHeader(
                    text = "通知中心",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "friend-requests") {
                Button(
                    onClick = onOpenFriendRequests,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    icon = {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                    },
                ) {
                    Text("新朋友")
                }
            }
            item(key = "group-notices") {
                Button(
                    onClick = onOpenGroupNotices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    transformation = SurfaceTransformation(transformationSpec),
                    icon = {
                        Icon(Icons.Outlined.Group, contentDescription = null)
                    },
                ) {
                    Text("群通知")
                }
            }
        }
    }
}

@Composable
private fun FriendRequestsScreen(
    state: FriendNotifyState,
    onBack: () -> Unit,
    onApprove: (UiFriendRequest, Boolean) -> Unit,
) {
    when {
        state.loading -> QmceLoadingState(message = "加载新朋友...")
        state.error != null -> QmceEmptyOrErrorState(
            message = state.error,
            isError = true,
        )
        state.items.isEmpty() -> QmceEmptyOrErrorState(
            message = "暂无新朋友请求",
        )
        else -> NotifyListScaffold(
            title = "新朋友",
            onBack = onBack,
            actionMessage = state.actionMessage,
        ) { transformationSpec ->
            items(state.items, key = { "${it.uid}:${it.reqTime}" }) { item ->
                FriendRequestItem(
                    item = item,
                    acting = state.actingUid == item.uid,
                    onApprove = { accept -> onApprove(item, accept) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}

@Composable
private fun GroupNoticesScreen(
    state: GroupNotifyState,
    onBack: () -> Unit,
    onOperate: (UiGroupNotice, Boolean) -> Unit,
) {
    when {
        state.loading -> QmceLoadingState(message = "加载群通知...")
        state.error != null -> QmceEmptyOrErrorState(
            message = state.error,
            isError = true,
        )
        state.items.isEmpty() -> QmceEmptyOrErrorState(
            message = "暂无群通知",
        )
        else -> NotifyListScaffold(
            title = "群通知",
            onBack = onBack,
            actionMessage = state.actionMessage,
        ) { transformationSpec ->
            items(state.items, key = { it.seq }) { item ->
                GroupNoticeItem(
                    item = item,
                    acting = state.actingSeq == item.seq,
                    onOperate = { accept -> onOperate(item, accept) },
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}

@Composable
private fun NotifyListScaffold(
    title: String,
    onBack: () -> Unit,
    actionMessage: String?,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "header") {
                QmceListHeader(
                    text = title,
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            actionMessage?.takeIf { it.isNotBlank() }?.let { message ->
                item(key = "action-message") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContainerTransformation()
                                    applyContentTransformation()
                                }
                            }
                            .padding(bottom = 4.dp),
                    )
                }
            }
            content(transformationSpec)
        }
    }
}

@Composable
private fun FriendRequestItem(
    item: UiFriendRequest,
    acting: Boolean,
    onApprove: (Boolean) -> Unit,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val subtitle = when {
        acting -> "处理中..."
        item.message.isNotBlank() -> item.message
        item.pending -> "待处理"
        else -> "已处理"
    }

    Button(
        onClick = {
            if (item.pending && !acting) showDialog = true
        },
        enabled = item.pending && !acting,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        transformation = transformation,
        secondaryLabel = {
            Text(
                text = subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(
            text = item.nick,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (item.pending) {
        PendingNotifyDialog(
            visible = showDialog,
            onDismiss = { showDialog = false },
            title = item.nick,
            detail = item.message,
            acting = acting,
            onConfirm = { onApprove(true) },
            onReject = { onApprove(false) },
        )
    }
}

@Composable
private fun GroupNoticeItem(
    item: UiGroupNotice,
    acting: Boolean,
    onOperate: (Boolean) -> Unit,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val subtitle = when {
        acting -> "处理中..."
        item.pending -> item.subtitle.ifBlank { "待处理" }
        item.statusLabel.isNotBlank() -> item.statusLabel
        else -> item.subtitle
    }

    Button(
        onClick = {
            if (item.pending && !acting) showDialog = true
        },
        enabled = item.pending && !acting,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        transformation = transformation,
        secondaryLabel = {
            Text(
                text = subtitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(
            text = item.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (item.pending) {
        PendingNotifyDialog(
            visible = showDialog,
            onDismiss = { showDialog = false },
            title = item.title,
            detail = item.subtitle,
            acting = acting,
            onConfirm = { onOperate(true) },
            onReject = { onOperate(false) },
        )
    }
}

@Composable
private fun PendingNotifyDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String,
    detail: String?,
    acting: Boolean,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = detail?.takeIf { it.isNotBlank() }?.let { message ->
            {
                Text(
                    text = message,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        content = {
            item {
                Button(
                    onClick = {
                        if (!acting) {
                            onDismiss()
                            onConfirm()
                        }
                    },
                    enabled = !acting,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(if (acting) "处理中…" else "同意")
                }
            }
            item {
                Button(
                    onClick = {
                        if (!acting) {
                            onDismiss()
                            onReject()
                        }
                    },
                    enabled = !acting,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Text("拒绝")
                }
            }
        },
    )
}
