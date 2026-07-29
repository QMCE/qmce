package rj.qmce.lite.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mqq.app.AppRuntime
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.ui.components.ChatItem
import rj.qmce.lite.ui.theme.LocalQmceAdaptive
import rj.qmce.lite.ui.wear.QmceEmptyOrErrorState
import rj.qmce.lite.ui.wear.QmceLoadingState
import rj.qmce.lite.viewmodel.ChatListViewModel

private const val KERNEL_INIT_ACTION = "com.tencent.mobileqq.action.ON_KERNEL_INIT_COMPLETE"

@OptIn(FlowPreview::class)
@Composable
fun ChatListScreen(
    uin: String,
    runtime: AppRuntime?,
    isPageVisible: Boolean,
    onLogout: () -> Unit,
    onOpenChat: (RecentContactInfo) -> Unit,
    onRetryKernel: () -> Unit,
    onOpenNotificationCenter: () -> Unit = {},
    vm: ChatListViewModel = viewModel()
) {
    val context = LocalContext.current
    val contactsSnapshot by vm.contacts.collectAsState()
    val contacts = contactsSnapshot.contacts
    val isRefreshing by vm.isRefreshing.collectAsState()
    val statusText by vm.statusText.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val scope = rememberCoroutineScope()
    val transformationSpec = rememberTransformationSpec()
    val latestContacts by rememberUpdatedState(contacts)
    val reportUid = runCatching { runtime?.currentUid.orEmpty() }.getOrDefault("")

    DisposableEffect(runtime) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                Log.d("QMCE", "ChatList: ON_KERNEL_INIT_COMPLETE received, loading contacts")
                vm.loadContacts(runtime)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(KERNEL_INIT_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    LaunchedEffect(listState, uin, runtime, isPageVisible) {
        if (!isPageVisible) return@LaunchedEffect
        repeat(40) {
            if (OfficialReportBridge.isReadyForEvents()) return@repeat
            delay(250L)
        }
        if (!OfficialReportBridge.isReadyForEvents()) return@LaunchedEffect

        val activeExposures = linkedMapOf<String, ActiveChatExposure>()
        val firstExposures = hashSetOf<String>()
        try {
            snapshotFlow {
                val currentContacts = latestContacts
                listState.layoutInfo.visibleItems.mapNotNull { visibleItem ->
                    currentContacts.getOrNull(visibleItem.index)?.let(::chatListExposureKey)
                }
            }
                .map { keys -> keys.distinct() }
                .distinctUntilChanged()
                .debounce(100L)
                .collect { visibleKeys ->
                    val now = System.currentTimeMillis()
                    val visibleSet = visibleKeys.toSet()
                    val currentContacts = latestContacts
                        .mapNotNull { contact ->
                            chatListExposureKey(contact)?.let { it to contact }
                        }
                        .toMap()

                    activeExposures.keys.toList()
                        .filterNot(visibleSet::contains)
                        .forEach { key ->
                            val exposure = activeExposures.remove(key) ?: return@forEach
                            OfficialReportBridge.reportChatListItemExposureEnd(
                                contact = exposure.contact,
                                homeUin = uin,
                                uid = reportUid,
                                exposureDurationMs = now - exposure.startedAtMs,
                            )
                        }

                    visibleKeys.forEach { key ->
                        if (activeExposures.containsKey(key)) return@forEach
                        val contact = currentContacts[key] ?: return@forEach
                        if (OfficialReportBridge.reportChatListItemExposure(
                                contact = contact,
                                homeUin = uin,
                                uid = reportUid,
                                firstExposure = key !in firstExposures,
                            )
                        ) {
                            activeExposures[key] = ActiveChatExposure(contact, now)
                            firstExposures += key
                        }
                    }
                }
        } finally {
            val now = System.currentTimeMillis()
            activeExposures.values.forEach { exposure ->
                OfficialReportBridge.reportChatListItemExposureEnd(
                    contact = exposure.contact,
                    homeUin = uin,
                    uid = reportUid,
                    exposureDurationMs = now - exposure.startedAtMs,
                )
            }
        }
    }

    when {
        contacts.isNotEmpty() -> {
            ScreenScaffold(
                scrollState = listState,
                edgeButtonSpacing = LocalQmceAdaptive.current.edgeButtonSpacing,
                edgeButton = {
                    EdgeButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.fillMaxWidth(),
                        buttonSize = EdgeButtonSize.Large,
                    ) {
                        Icon(
                            Icons.Default.ExpandLess,
                            contentDescription = "滚动到顶部",
                        )
                    }
                },
            ) { contentPadding ->
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                ) {
                    item(key = "notification-bell") {
                        Button(
                            onClick = onOpenNotificationCenter,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            icon = {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = "通知中心",
                                )
                            },
                        ) {
                            Text("通知中心")
                        }
                    }
                    items(
                        items = contacts,
                        key = { contact ->
                            "chat:${chatListExposureKey(contact) ?: contact.hashCode()}"
                        },
                    ) { contact ->
                        ChatItem(
                            contact = contact,
                            reportParams = OfficialReportBridge.chatListItemElementParams(
                                contact = contact,
                                homeUin = uin,
                                uid = reportUid,
                            ),
                            reuseIdentifier = chatListExposureKey(contact),
                            onClick = { target ->
                                OfficialReportBridge.reportChatListItemClick(
                                    target = target,
                                    contact = contact,
                                    homeUin = uin,
                                    uid = reportUid,
                                )
                                onOpenChat(contact)
                            },
                            modifier = Modifier.transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        )
                    }
                }
            }
        }

        statusText.contains("失败") || statusText.contains("不可用") -> {
            QmceEmptyOrErrorState(
                message = statusText.ifBlank { "会话加载失败" },
                actionLabel = "重试",
                onAction = onRetryKernel,
                isError = true,
            )
        }

        statusText.isBlank() || statusText.contains("加载") || statusText.contains("等待") -> {
            QmceLoadingState(message = statusText.ifBlank { "加载会话…" })
        }

        else -> {
            QmceEmptyOrErrorState(
                message = statusText.ifBlank { "暂无会话" },
                actionLabel = "刷新",
                onAction = { vm.refreshContacts() },
            )
        }
    }
}

private data class ActiveChatExposure(
    val contact: RecentContactInfo,
    val startedAtMs: Long,
)

private fun chatListExposureKey(contact: RecentContactInfo): String? {
    return contact.contactId.takeIf { it > 0L }?.toString()
        ?: contact.id?.takeIf { it.isNotBlank() }
        ?: contact.peerUid?.takeIf { it.isNotBlank() }
}
