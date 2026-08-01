package rj.qmce.lite.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.ui.settingsVm
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import rj.qmce.lite.viewmodel.ChatListViewModel
import rj.qmce.lite.viewmodel.ContactsViewModel
import rj.qmce.lite.viewmodel.MyViewModel
import rj.qmce.lite.viewmodel.QZoneViewModel
@OptIn(
    androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class,
)
@Composable
fun MainScreen(
    chatListVm: ChatListViewModel,
    chatDetailVm: ChatDetailViewModel,
    contactsVm: ContactsViewModel,
    qZoneVm: QZoneViewModel,
    myVm: MyViewModel,
    uin: String,
    runtime: mqq.app.AppRuntime?,
    showTimeText: Boolean,
    showPageIndicator: Boolean,
    onPageChanged: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogoutConfirmation: () -> Unit,
    onForceExit: () -> Unit,
    onOpenQZoneComposer: () -> Unit,
    onOpenQZoneDetail: (rj.qmce.lite.viewmodel.QZoneViewModel.FeedItem) -> Unit,
    onLogout: () -> Unit,
    onOpenChat: (RecentContactInfo) -> Unit,
    onOpenChatFromContacts: (String, String, String, Int) -> Unit, // uid, uin, name, chatType
    onOpenGroupFromContacts: (ContactsViewModel.UiGroup) -> Unit,
    onOpenContactProfile: (ContactsViewModel.UiBuddy) -> Unit,
    onOpenNotificationCenter: () -> Unit = {},
) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    var kernelRetryNonce by remember(uin) { mutableIntStateOf(0) }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    LaunchedEffect(uin, runtime) {
        val activeRuntime = QmceApplication.ensureRuntime() ?: runtime ?: return@LaunchedEffect
        qZoneVm.init(activeRuntime)
    }

    LaunchedEffect(uin, runtime, kernelRetryNonce) {
        val activeRuntime = QmceApplication.ensureRuntime() ?: runtime ?: return@LaunchedEffect
        val timeouts = listOf(30_000L, 10_000L, 6_000L)
        timeouts.forEachIndexed { index, timeoutMillis ->
            val ready = withContext(Dispatchers.IO) {
                if (kernelRetryNonce == 0 && index == 0) {
                    KernelBridge.awaitCoreServices(timeoutMillis, activeRuntime)
                } else {
                    KernelBridge.retryCoreServices(timeoutMillis, activeRuntime)
                }
            }
            android.util.Log.d("QMCE", "MainScreen: core services ready=$ready attempt=${index + 1}")
            if (ready) {
                chatListVm.loadContacts(activeRuntime)
                contactsVm.loadBuddies(activeRuntime, forceRefresh = true)
                qZoneVm.loadFeeds(forceRefresh = true)
                return@LaunchedEffect
            }
            if (index < timeouts.lastIndex) {
                chatListVm.markWaitingForKernel()
                contactsVm.markWaitingForKernel()
                kotlinx.coroutines.delay(2_000L)
            }
        }
        chatListVm.markKernelInitFailed()
        contactsVm.markKernelInitFailed()
        // Kernel 失败时仍尝试空间（依赖 MSF ticket，不依赖 NT kernel）
        qZoneVm.loadFeeds(forceRefresh = true)
    }

    HorizontalPagerScaffold(
        pagerState = pagerState,
        // The app renders its own top-aligned indicator below (see TopPageIndicator), so the
        // scaffold's default bottom indicator is disabled here. HorizontalPagerScaffold is still
        // used to coordinate TimeText visibility with page-swipe gestures.
        pageIndicator = null,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showPageIndicator) TopPageIndicator(pagerState, pointsUp = !showTimeText)

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> ChatListScreen(
                        uin = uin,
                        runtime = runtime,
                        isPageVisible = page == pagerState.currentPage,
                        onLogout = onLogout,
                        onOpenChat = onOpenChat,
                        onRetryKernel = { kernelRetryNonce++ },
                        onOpenNotificationCenter = onOpenNotificationCenter,
                        vm = chatListVm,
                    )

                    1 -> ContactsScreen(
                        vm = contactsVm,
                        onOpenChat = { uid, uin, name ->
                            onOpenChatFromContacts(uid, uin, name, 1)
                        },
                        onOpenGroup = onOpenGroupFromContacts,
                        onOpenProfile = onOpenContactProfile,
                        onRetryKernel = { kernelRetryNonce++ },
                    )

                    2 -> QZoneScreen(
                        vm = qZoneVm,
                        onOpenComposer = onOpenQZoneComposer,
                        onOpenDetail = onOpenQZoneDetail,
                    )

                    3 -> MyScreen(
                        uin = uin,
                        onOpenSettings = onOpenSettings,
                        onOpenLogoutConfirmation = onOpenLogoutConfirmation,
                        onForceExit = onForceExit,
                        vm = myVm,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopPageIndicator(
    pagerState: androidx.wear.compose.foundation.pager.PagerState,
    pointsUp: Boolean,
) {
    val settings by settingsVm.settings.collectAsState()
    if (!settings.showTimeText) {
        HorizontalPageIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .graphicsLayer { scaleY = if (pointsUp) -1f else 1f },
        )
    }
}
