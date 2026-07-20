package rj.qmce.lite.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mqq.app.AppRuntime
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.ui.components.ChatItem
import rj.qmce.lite.viewmodel.ChatListViewModel
import kotlin.time.Duration.Companion.seconds

private const val KERNEL_INIT_ACTION = "com.tencent.mobileqq.action.ON_KERNEL_INIT_COMPLETE"

@OptIn(FlowPreview::class)
@Composable
fun ChatListScreen(
    uin: String,
    runtime: AppRuntime?,
    isPageVisible: Boolean,
    onLogout: () -> Unit,
    onOpenChat: (RecentContactInfo) -> Unit,
    vm: ChatListViewModel = viewModel()
) {
    val context = LocalContext.current
    val contactsSnapshot by vm.contacts.collectAsState()
    val contacts = contactsSnapshot.contacts
    val isRefreshing by vm.isRefreshing.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val pullRefreshState = rememberPullToRefreshState()
    val latestContacts by rememberUpdatedState(contacts)
    val reportUid = runCatching { runtime?.currentUid.orEmpty() }.getOrDefault("")

    Log.d(
        "QMCE",
        "ChatListScreen: recompose, revision=${contactsSnapshot.revision}, contacts.size=${contacts.size}, top1=${
            contacts.firstOrNull()
                ?.let { "${it.id}:${it.msgTime}:${it.abstractContent?.firstOrNull()?.content}" }
        }"
    )

    DisposableEffect(runtime) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                Log.d("QMCE", "ChatList: ON_KERNEL_INIT_COMPLETE received, loading contacts")
                vm.loadContacts(runtime)
            }
        }
        val filter = IntentFilter(KERNEL_INIT_ACTION)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        Log.d("QMCE", "ChatList: registered ON_KERNEL_INIT_COMPLETE receiver")
        vm.loadContacts(runtime)
        val fallback = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(3.seconds)
            vm.loadContacts(runtime)
        }
        onDispose {
            Log.d("QMCE", "ChatListScreen: DisposableEffect disposed")
            fallback.cancel()
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

    if (contacts.isNotEmpty()) {
        ScreenScaffold(
            scrollState = listState,
            overscrollEffect = null,
        ) { contentPadding ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = vm::refreshContacts,
                modifier = Modifier.fillMaxSize(),
                state = pullRefreshState,
                indicator = {
                    WearPullRefreshIndicator(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                    )
                }, // fixme: sometimes wrong state (or caused by scrcpy?)
            ) {
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = contentPadding,
                    overscrollEffect = null,
                ) {
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

@Composable
private fun BoxScope.WearPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
) {
    val diameter = 38.dp
    val density = LocalDensity.current
    val distance = state.distanceFraction.coerceIn(0f, 1f)
    val translation = with(density) { diameter.toPx() * (distance - 1f) }
    Box(
        modifier = Modifier
            .align(androidx.compose.ui.Alignment.TopCenter)
            .graphicsLayer {
                alpha = distance
                translationY = translation
            }
            .size(diameter)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
        } else {
            CircularProgressIndicator(
                progress = { distance },
                modifier = Modifier.size(22.dp),
                strokeWidth = 3.dp,
            )
        }
    }
}
