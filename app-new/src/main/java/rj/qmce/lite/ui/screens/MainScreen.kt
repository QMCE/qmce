package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.*
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
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
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    onOpenChat: (RecentContactInfo) -> Unit,
    onOpenChatFromContacts: (String, String, String) -> Unit, // uid, uin, name
) {
    val pagerState = rememberPagerState(pageCount = { 4 })

    LaunchedEffect(uin, runtime) {
        if (runtime == null) return@LaunchedEffect
        qZoneVm.init(runtime)
        chatListVm.loadContacts(runtime)
        contactsVm.loadBuddies(runtime, forceRefresh = true)
        qZoneVm.loadFeeds(forceRefresh = true)
    }

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
                    onLogout = onLogout,
                    onOpenChat = onOpenChat,
                    vm = chatListVm,
                )
                1 -> ContactsScreen(
                    vm = contactsVm,
                    onOpenChat = onOpenChatFromContacts,
                )
                2 -> QZoneScreen(vm = qZoneVm)
                3 -> MyScreen(
                    uin = uin,
                    chatListVm = chatListVm,
                    onOpenSettings = onOpenSettings,
                    onLogout = onLogout,
                    vm = myVm,
                )
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
    if (!settings.showTimeText)
    {
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
