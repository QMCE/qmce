@file:OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)

package rj.qmce.lite.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import android.util.Log
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material3.curvedText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.R
import rj.qmce.lite.data.LoginPrefs
import rj.qmce.lite.data.OnlineStatus
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.ui.screens.ChatDetailScreen
import rj.qmce.lite.ui.screens.ChatInfoScreen
import rj.qmce.lite.ui.screens.ChatInputScreen
import rj.qmce.lite.ui.screens.ChatMembersScreen
import rj.qmce.lite.ui.screens.ChatSettingsScreen
import rj.qmce.lite.ui.screens.ContactPickerScreen
import rj.qmce.lite.ui.screens.LoginScreen
import rj.qmce.lite.ui.screens.MainScreen
import rj.qmce.lite.ui.screens.PacketToolScreen
import rj.qmce.lite.ui.screens.SettingsScreen
import rj.qmce.lite.ui.screens.VoiceRecordScreen
import rj.qmce.lite.ui.theme.QmceTheme
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import rj.qmce.lite.viewmodel.ChatListViewModel
import rj.qmce.lite.viewmodel.ChatSettingsViewModel
import rj.qmce.lite.viewmodel.ContactsViewModel
import rj.qmce.lite.viewmodel.QZoneViewModel
import rj.qmce.lite.viewmodel.MyViewModel
import rj.qmce.lite.viewmodel.PacketToolViewModel
import rj.qmce.lite.viewmodel.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QmceTheme {
                WearApp()
                LocalDensity
            }
        }
    }
}
lateinit var settingsVm: SettingsViewModel

@Composable
private fun WearApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isLoggedIn by remember { mutableStateOf(false) }
    var loggedUin by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var runtime by remember { mutableStateOf<mqq.app.AppRuntime?>(null) }
    var selectedContact by remember { mutableStateOf<RecentContactInfo?>(null) }
    settingsVm = viewModel()
    val settings by settingsVm.settings.collectAsState()

    val logoutReason by QmceApplication.logoutReason.collectAsState()

    // OnlineStatus state
    var onlineDesc by remember { mutableStateOf<String?>(null) }
    var onlineKnown by remember { mutableStateOf(false) }

    LaunchedEffect(logoutReason) {
        if (logoutReason != null) {
            selectedContact = null
            loggedUin = ""
            isLoggedIn = false
            onlineDesc = null
            onlineKnown = false
            Log.w("QMCE", "ui: returned to login after official logout=$logoutReason")
        }
    }

    DisposableEffect(isLoggedIn) {
        if (isLoggedIn) {
            val ps = KernelBridge.getKernelService()?.profileService
            if (ps != null && loggedUin.isNotEmpty()) {
                OnlineStatus.start(ps, loggedUin)
            }
            val observer = {
                onlineDesc = OnlineStatus.describe()
                onlineKnown = OnlineStatus.known()
            }
            OnlineStatus.addObserver(observer)
            observer()
            onDispose { OnlineStatus.removeObserver(observer) }
        } else {
            onlineDesc = null
            onlineKnown = false
            Log.e("QMCE","Why it can be here?")
            onDispose {}
        }
    }

    LaunchedEffect(Unit) {
        val rt = withContext(Dispatchers.IO) {
            val r = QmceApplication.ensureRuntime()
            val saved = LoginPrefs.loadAccount(context)
            if (saved != null) {
                val uin = saved.uin
                val result = KernelBridge.bindLoggedInAccount(uin, saved)
                if (result == "ok") {
                    KernelBridge.awaitCoreServices(runtimeOverride = r)
                    withContext(Dispatchers.Main) { loggedUin = uin; isLoggedIn = true }
                } else {
                    withContext(Dispatchers.Main) { LoginPrefs.clear(context) }
                }
            }
            r
        }
        withContext(Dispatchers.Main) {
            runtime = QmceApplication.ensureRuntime() ?: rt
            ready = true
        }
    }

    if (!ready) {
        SplashScreen()
        return
    }

    AppScaffold(
        timeText = {
            if (settings.showTimeText) {
                val showStatus = settings.showOnlineStatus && isLoggedIn && onlineKnown
                TimeText(
                    maxSweepAngle = if (showStatus) 140f else TimeTextDefaults.MaxSweepAngle,
                    content = { time ->
                        timeTextCurvedText(time)
                        if (showStatus) {
                            timeTextSeparator()
                            curvedText(
                                text = onlineDesc ?: "离线",
                                color = if (OnlineStatus.isOnline())
                                    Color(0xFF3CCB5A)
                                else
                                    Color(0xFFB0B0B0),
                            )
                        }
                    },
                )
            }
        },
    ) {
        if (isLoggedIn) {
            val navController = rememberSwipeDismissableNavController()
            val chatDetailVm: ChatDetailViewModel = viewModel()
            val chatSettingsVm: ChatSettingsViewModel = viewModel()
            val chatListVm: ChatListViewModel = viewModel()
            val contactsVm: ContactsViewModel = viewModel()
            val qZoneVm: QZoneViewModel = viewModel()
            val myVm: MyViewModel = viewModel()
            val packetToolVm: PacketToolViewModel = viewModel()
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = "main"
            ) {
                composable("main") {
                    MainScreen(
                        chatListVm = chatListVm,
                        chatDetailVm = chatDetailVm,
                        contactsVm = contactsVm,
                        qZoneVm = qZoneVm,
                        myVm = myVm,
                        uin = loggedUin,
                        runtime = runtime,
                        showTimeText = settings.showTimeText,
                        showPageIndicator = settings.showPageIndicator,
                        onOpenSettings = {
                            navController.navigate("settings") { launchSingleTop = true }
                        },
                        onLogout = {
                            (context.applicationContext as? QmceApplication)?.clearLocalLoginState()
                                ?: LoginPrefs.clear(context)
                            selectedContact = null
                            isLoggedIn = false
                            loggedUin = ""
                        },
                        onOpenChat = { contact ->
                            selectedContact = contact
                            navController.navigate("chat") { launchSingleTop = true }
                        },
                        onOpenChatFromContacts = { uid, uin, name ->
                            // 从联系人页面点击，构造一个最小 RecentContactInfo
                            val fakeContact = RecentContactInfo().apply {
                                peerUid = uid
                                peerUin = uin.toLongOrNull() ?: 0L
                                peerName = name
                                chatType = 1
                                id = uin
                            }
                            selectedContact = fakeContact
                            navController.navigate("chat") { launchSingleTop = true }
                        }
                    )
                }
                composable("chat") {
                    val contact = selectedContact
                    if (contact != null) {
                        ChatDetailScreen(
                            runtime = runtime,
                            peerUid = contact.peerUid ?: "",
                            peerUin = contact.peerUin.takeIf { it > 0L }?.toString()
                                ?: contact.id.orEmpty(),
                            chatType = contact.chatType,
                            peerName = contact.peerName ?: contact.id ?: "",
                            avatarPath = contact.avatarPath.orEmpty(),
                            avatarUrl = contact.avatarUrl.orEmpty(),
                            myUin = loggedUin,
                            onBack = { navController.popBackStack() },
                            onOpenInput = { navController.navigate("chatInput") { launchSingleTop = true } },
                            onOpenContactPicker = { navController.navigate("contactPicker") { launchSingleTop = true } },
                            onOpenPacketTool = { navController.navigate("packetToolChat") { launchSingleTop = true } },
                            onOpenMembers = { navController.navigate("chatMembers") { launchSingleTop = true } },
                            onOpenChatSettings = { navController.navigate("chatSettings") { launchSingleTop = true } },
                            vm = chatDetailVm
                        )
                    }
                }
                composable("contactPicker") {
                    ContactPickerScreen(
                        title = "转发给",
                        runtime = runtime,
                        contactsVm = contactsVm,
                        onSelect = { uid, uin, name ->
                            chatDetailVm.consumePendingForward(1, uid)
                            navController.popBackStack()
                        },
                        onBack = {
                            chatDetailVm.clearPendingForward()
                            navController.popBackStack()
                        },
                    )
                }
                composable("chatInput") {
                    val editingText by chatDetailVm.editingText.collectAsState()
                    ChatInputScreen(
                        vm = chatDetailVm,
                        peerUid = chatDetailVm.currentPeerUid,
                        chatType = chatDetailVm.currentChatType,
                        editingText = editingText,
                        onSend = { text -> chatDetailVm.sendText(text) },
                        onSendEdited = { text -> chatDetailVm.sendEditedText(text) },
                        peerUin = chatDetailVm.currentPeerUin,
                        onSendMixed = { mixedText, uriMap, atMap -> chatDetailVm.sendMixed(context, mixedText, uriMap, atMap) },
                        onSendFile = { uri -> chatDetailVm.sendFile(context, uri) },
                        onSendVideo = { uri -> chatDetailVm.sendVideo(context, uri) },
                        onOpenVoiceRecorder = { navController.navigate("voiceRecord") { launchSingleTop = true } },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("chatMembers") {
                    val contact = selectedContact
                    if (contact != null) {
                        ChatMembersScreen(
                            groupCode = contact.peerUin.takeIf { it > 0L } ?: contact.id?.toLongOrNull() ?: 0L,
                            vm = chatDetailVm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable("chatSettings") {
                    val contact = selectedContact
                    if (contact != null) {
                        val peerUin = contact.peerUin.takeIf { it > 0L } ?: contact.id?.toLongOrNull() ?: 0L
                        ChatSettingsScreen(
                            contact = contact,
                            peerUid = contact.peerUid.orEmpty(),
                            peerUin = peerUin,
                            displayName = contact.peerName.orEmpty().ifBlank { contact.id.orEmpty() },
                            vm = chatSettingsVm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
                composable("voiceRecord") {
                    VoiceRecordScreen(
                        onSendVoice = { file, durationMillis, formatType ->
                            chatDetailVm.sendVoice(file, durationMillis, formatType)
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("packetToolChat") {
                    val contact = selectedContact
                    PacketToolScreen(
                        peerUid = contact?.peerUid ?: "",
                        peerName = contact?.peerName ?: "",
                        chatType = contact?.chatType ?: 0,
                        vm = packetToolVm,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        runtime = runtime,
                        chatListVm = chatListVm,
                        contactsVm = contactsVm,
                        qZoneVm = qZoneVm,
                        myVm = myVm,
                        settingsVm = settingsVm,
                        onOpenPacketTool = {
                            navController.navigate("packetToolSettings") { launchSingleTop = true }
                        },
                    )
                }
                composable("packetToolSettings") {
                    PacketToolScreen(
                        vm = packetToolVm,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        } else {
            LoginScreen(onLoginSuccess = { uin, account ->
                LoginPrefs.saveAccount(context, account)
                QmceApplication.markLoginEstablished()
                val restarted = QmceApplication.restartAfterLogin(context)
                Log.d("QMCE", "ui: login persisted uin=$uin, scheduled fresh start=$restarted")
            })
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.wear.compose.material3.MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.qqpro_ic_fg),
                contentDescription = "QQ",
                modifier = Modifier.size(72.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(16.dp))
            androidx.wear.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
