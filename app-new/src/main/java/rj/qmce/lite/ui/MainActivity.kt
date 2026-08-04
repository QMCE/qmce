@file:OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)

package rj.qmce.lite.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import mqq.app.Constants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.curvedText
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.R
import rj.qmce.lite.data.LoginPrefs
import rj.qmce.lite.data.OnlineStatus
import rj.qmce.lite.data.chat.GroupMemberRepository
import rj.qmce.lite.data.chat.MessageNavigationSnapshot
import rj.qmce.lite.data.emotion.EmotionRepository
import rj.qmce.lite.data.reporting.LocalOfficialReportHost
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportLifecycle
import rj.qmce.lite.data.reporting.OfficialReportPage
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.ui.screens.AboutHubScreen
import rj.qmce.lite.ui.screens.AboutScreen
import rj.qmce.lite.ui.screens.AppearanceSettingsScreen
import rj.qmce.lite.ui.screens.BackgroundSettingsScreen
import rj.qmce.lite.ui.screens.CallSettingsScreen
import rj.qmce.lite.ui.screens.ChatDetailScreen
import rj.qmce.lite.ui.screens.ChatComposerMenuScreen
import rj.qmce.lite.ui.screens.ChatInputScreen
import rj.qmce.lite.ui.screens.EmotionPickerScreen
import rj.qmce.lite.ui.screens.ChatMembersScreen
import rj.qmce.lite.ui.screens.ChatSettingsScreen
import rj.qmce.lite.ui.screens.ContactPickerScreen
import rj.qmce.lite.ui.screens.DataSettingsScreen
import rj.qmce.lite.ui.screens.DeveloperToolsSettingsScreen
import rj.qmce.lite.ui.screens.ForceExitConfirmationScreen
import rj.qmce.lite.ui.screens.IntelligenceSettingsScreen
import rj.qmce.lite.ui.screens.InteractionSettingsScreen
import rj.qmce.lite.ui.screens.LocalImagePickerScreen
import rj.qmce.lite.ui.screens.LoginScreen
import rj.qmce.lite.ui.screens.LogoutConfirmationScreen
import rj.qmce.lite.ui.screens.MainScreen
import rj.qmce.lite.notify.QmceDeepLinks
import rj.qmce.lite.notify.QmceForegroundSession
import rj.qmce.lite.notify.QmceMessageNotificationBuilder
import rj.qmce.lite.notify.QmceMessageNotifier
import rj.qmce.lite.notify.QmceNotifyLifecycle
import rj.qmce.lite.notify.QmceRecentViewedChats
import rj.qmce.lite.ui.screens.NotificationCenterScreen
import rj.qmce.lite.ui.screens.NotificationSettingsScreen
import rj.qmce.lite.ui.screens.PacketToolScreen
import rj.qmce.lite.ui.screens.TileGroupPickerScreen
import rj.qmce.lite.ui.screens.QZoneCommentScreen
import rj.qmce.lite.ui.screens.QZoneComposerScreen
import rj.qmce.lite.ui.screens.QZoneFeedDetailScreen
import rj.qmce.lite.ui.screens.ProfileScreen
import rj.qmce.lite.ui.screens.GroupInfoScreen
import rj.qmce.lite.ui.screens.GroupManagementScreen
import rj.qmce.lite.ui.screens.GroupMemberProfileScreen
import rj.qmce.lite.ui.screens.SettingsClearChatCacheScreen
import rj.qmce.lite.ui.screens.SettingsScreen
import rj.qmce.lite.ui.screens.VoiceRecordScreen
import rj.qmce.lite.ui.theme.QmceTheme
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import rj.qmce.lite.viewmodel.ChatListViewModel
import rj.qmce.lite.viewmodel.ChatSettingsViewModel
import rj.qmce.lite.viewmodel.ContactsViewModel
import rj.qmce.lite.viewmodel.GroupInfoViewModel
import rj.qmce.lite.viewmodel.GroupManagementViewModel
import rj.qmce.lite.viewmodel.MyViewModel
import rj.qmce.lite.viewmodel.StorageViewModel
import rj.qmce.lite.viewmodel.PacketToolViewModel
import rj.qmce.lite.viewmodel.QZoneViewModel
import rj.qmce.lite.viewmodel.SettingsViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reportHost = FrameLayout(this).apply {
            id = View.generateViewId()
        }
        val composeView = ComposeView(this).apply {
            id = View.generateViewId()
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        reportHost.addView(composeView)
        composeView.setContent {
            CompositionLocalProvider(
                LocalOfficialReportHost provides reportHost,
            ) {
                WearApp()
            }
        }
        setContentView(reportHost)
        QmceDeepLinks.notifyNewIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        QmceDeepLinks.notifyNewIntent()
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
    var selectedProfileBuddy by remember { mutableStateOf<ContactsViewModel.UiBuddy?>(null) }
    var selectedGroupMember by remember { mutableStateOf<GroupMemberRepository.Member?>(null) }
    var qZoneDraft by remember { mutableStateOf("") }
    var qZoneUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var qZonePickerUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var qZoneCommentTarget by remember { mutableStateOf<QZoneViewModel.FeedItem?>(null) }
    var qZoneCommentDraft by remember { mutableStateOf("") }
    var qZoneDetailTarget by remember { mutableStateOf<QZoneViewModel.FeedItem?>(null) }
    var loginPageId by remember { mutableStateOf<String?>(OfficialReportBridge.PageIds.WELCOME) }
    settingsVm = viewModel()
    val settings by settingsVm.settings.collectAsState()
    val loginEnterScope = rememberCoroutineScope()

    val logoutReason by QmceApplication.logoutReason.collectAsState()

    // OnlineStatus state
    var onlineDesc by remember { mutableStateOf<String?>(null) }
    var onlineKnown by remember { mutableStateOf(false) }
    var onlineTermKind by remember { mutableStateOf<OnlineStatus.TermKind?>(null) }

    LaunchedEffect(logoutReason) {
        val reason = logoutReason ?: return@LaunchedEffect
        val toastText = when (reason) {
            Constants.LogoutReason.expired,
            Constants.LogoutReason.suspend,
            -> "登录已过期，请重新登录"
            Constants.LogoutReason.kicked,
            Constants.LogoutReason.secKicked,
            Constants.LogoutReason.forceLogout,
            -> "账号已在其他设备登录，请重新登录"
            else -> "请重新登录"
        }
        Toast.makeText(context, toastText, Toast.LENGTH_LONG).show()
        selectedContact = null
        selectedGroupMember = null
        loggedUin = ""
        isLoggedIn = false
        loginPageId = OfficialReportBridge.PageIds.WELCOME
        onlineDesc = null
        onlineKnown = false
        onlineTermKind = null
        QmceApplication.consumeLogoutReason()
        Log.w("QMCE", "ui: returned to login after official logout=$reason")
    }

    DisposableEffect(isLoggedIn, loggedUin) {
        if (isLoggedIn) {
            val ps = KernelBridge.getKernelService()?.getProfileService()
            if (ps != null && loggedUin.isNotEmpty()) {
                OnlineStatus.start(ps, loggedUin)
            }
            val observer = {
                onlineDesc = OnlineStatus.describe()
                onlineKnown = OnlineStatus.known()
                onlineTermKind = OnlineStatus.termKind()
            }
            OnlineStatus.addObserver(observer)
            observer()
            OnlineStatus.refreshStatusInfo()
            onDispose { OnlineStatus.removeObserver(observer) }
        } else {
            onlineDesc = null
            onlineKnown = false
            onlineTermKind = null
            Log.e("QMCE", "Not logged in")
            onDispose {}
        }
    }

    LaunchedEffect(Unit) {
        val rt = withContext(Dispatchers.IO) {
            val r = QmceApplication.ensureRuntime()
            val saved = LoginPrefs.loadAccount(context)
            if (saved != null) {
                val uin = saved.uin
                QmceApplication.beginLoginTransition()
                val result = KernelBridge.bindLoggedInAccount(uin, saved)
                if (result == "ok" || result == "kernel-not-ready") {
                    if (result == "kernel-not-ready") {
                        Log.w(
                            "QMCE",
                            "bind: account ok but kernel session not ready; awaiting services",
                        )
                    }
                    var ready = KernelBridge.awaitCoreServices(
                        timeoutMillis = 30_000,
                        runtimeOverride = r,
                    )
                    if (!ready) {
                        ready = KernelBridge.retryCoreServices(
                            timeoutMillis = 15_000,
                            runtimeOverride = r,
                        )
                    }
                    withContext(Dispatchers.Main) {
                        QmceApplication.markLoginEstablished()
                        loggedUin = uin
                        isLoggedIn = true
                        runtime = QmceApplication.ensureRuntime() ?: r
                        Log.d("QMCE", "ui: cold restore enter uin=$uin coreReady=$ready")
                    }
                } else {
                    Log.e("QMCE", "bindLoggedInAccount failed: $result")
                    QmceApplication.endLoginTransition()
                    withContext(Dispatchers.Main) { LoginPrefs.clear(context) }
                }
            }
            r
        }
        withContext(Dispatchers.Main) {
            if (runtime == null) {
                runtime = QmceApplication.ensureRuntime() ?: rt
            }
            ready = true
        }
    }

    if (!ready) {
        QmceTheme(
            autoScale = settings.autoScale,
            manualScale = settings.manualScale,
            fontScale = settings.fontScale,
            edgeSafeAreaEnabled = settings.edgeSafeAreaEnabled,
            edgeSafeAreaScale = settings.edgeSafeAreaScale,
        ) {
            OfficialReportPage(OfficialReportBridge.PageIds.SPLASH) {
                SplashScreen(
                    restoringLogin = LoginPrefs.loadAccount(context) != null,
                )
            }
        }
        return
    }

    val chatDetailVm: ChatDetailViewModel = viewModel()
    var currentNavRoute by remember { mutableStateOf<String?>(null) }
    val unreadBadgeText by chatDetailVm.unreadBadgeText.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) currentNavRoute = null
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> QmceForegroundSession.appInForeground = true
                Lifecycle.Event.ON_STOP -> QmceForegroundSession.appInForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            QmceNotifyLifecycle.onLoggedIn(context)
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Best-effort: user can also grant from notification settings.
            }
        } else {
            QmceNotifyLifecycle.onLoggedOut(context)
        }
    }

    val appNavController = if (isLoggedIn) {
        rememberSwipeDismissableNavController()
    } else {
        null
    }

    QmceTheme(
        navController = appNavController,
        autoScale = settings.autoScale,
        manualScale = settings.manualScale,
        fontScale = settings.fontScale,
        edgeSafeAreaEnabled = settings.edgeSafeAreaEnabled,
        edgeSafeAreaScale = settings.edgeSafeAreaScale,
    ) {
        val themeColors = androidx.wear.compose.material3.MaterialTheme.colorScheme
        AppScaffold(
            timeText = {
                if (settings.showTimeText) {
                    val showUnreadBadge = isLoggedIn &&
                        currentNavRoute == "chat" &&
                        !unreadBadgeText.isNullOrBlank()
                    val showStatus = settings.showOnlineStatus && isLoggedIn && onlineKnown && !showUnreadBadge
                    val statusColor = if (OnlineStatus.isOnline()) {
                        themeColors.tertiary
                    } else {
                        themeColors.onSurfaceVariant
                    }
                    val statusIcon = onlineTermKind?.let(::termKindIcon)
                        ?: Icons.Default.PhoneAndroid
                    TimeText(
                        maxSweepAngle = if (showStatus || showUnreadBadge) 140f else TimeTextDefaults.MaxSweepAngle,
                        content = { time ->
                            timeTextCurvedText(time)
                            if (showUnreadBadge) {
                                timeTextSeparator()
                                curvedText(
                                    text = unreadBadgeText.orEmpty(),
                                    color = themeColors.primary,
                                )
                            } else if (showStatus) {
                                timeTextSeparator()
                                curvedComposable {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = onlineDesc
                                            ?: if (OnlineStatus.isOnline()) "在线" else "离线",
                                        tint = statusColor,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        ) {
            if (isLoggedIn) {
                val navController = checkNotNull(appNavController)
                val chatSettingsVm: ChatSettingsViewModel = viewModel()
                val groupInfoVm: GroupInfoViewModel = viewModel()
                val groupManagementVm: GroupManagementViewModel = viewModel()
                val groupManagementState by groupManagementVm.state.collectAsState()
                val chatListVm: ChatListViewModel = viewModel()
                val contactsVm: ContactsViewModel = viewModel()
                val qZoneVm: QZoneViewModel = viewModel()
                val myVm: MyViewModel = viewModel()
                val storageVm: StorageViewModel = viewModel()
                val packetToolVm: PacketToolViewModel = viewModel()
                var mainPage by remember { mutableStateOf(0) }
                var nestedOfficialPageId by remember { mutableStateOf<String?>(null) }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(navBackStackEntry?.destination?.route) {
                    currentNavRoute = navBackStackEntry?.destination?.route
                    nestedOfficialPageId = null
                }
                val officialPageId = nestedOfficialPageId ?: officialPageId(
                    route = navBackStackEntry?.destination?.route,
                    mainPage = mainPage,
                )
                val imagePermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    if (hasQZoneGalleryAccess(context)) {
                        navController.navigate("qzoneImagePicker") { launchSingleTop = true }
                    }
                }
                OfficialReportLifecycle(officialPageId)
                val deepLinkTick by QmceDeepLinks.tick.collectAsState()
                LaunchedEffect(isLoggedIn, deepLinkTick, currentNavRoute) {
                    if (!isLoggedIn) return@LaunchedEffect
                    val activity = context as? MainActivity ?: return@LaunchedEffect
                    val intent = activity.intent ?: return@LaunchedEffect
                    val openNotify = intent.getBooleanExtra(
                        QmceMessageNotifier.EXTRA_OPEN_NOTIFY_CENTER,
                        false,
                    )
                    val openChat = intent.getBooleanExtra(
                        QmceMessageNotifier.EXTRA_OPEN_CHAT,
                        false,
                    )
                    val openTilePicker = intent.getBooleanExtra(
                        QmceMessageNotifier.EXTRA_OPEN_TILE_GROUP_PICKER,
                        false,
                    )
                    if (!openNotify && !openChat && !openTilePicker) return@LaunchedEffect
                    intent.removeExtra(QmceMessageNotifier.EXTRA_OPEN_NOTIFY_CENTER)
                    intent.removeExtra(QmceMessageNotifier.EXTRA_OPEN_CHAT)
                    intent.removeExtra(QmceMessageNotifier.EXTRA_OPEN_TILE_GROUP_PICKER)
                    if (openNotify) {
                        navController.navigate("notificationCenter") { launchSingleTop = true }
                        return@LaunchedEffect
                    }
                    if (openTilePicker) {
                        navController.navigate("tileGroupPicker") { launchSingleTop = true }
                        return@LaunchedEffect
                    }
                    val peerUid = intent.getStringExtra(QmceMessageNotifier.EXTRA_PEER_UID)
                        ?.takeIf { it.isNotBlank() }
                        ?: return@LaunchedEffect
                    val chatType = intent.getIntExtra(QmceMessageNotifier.EXTRA_CHAT_TYPE, 1)
                    val peerUin = intent.getLongExtra(QmceMessageNotifier.EXTRA_PEER_UIN, 0L)
                    val nick = intent.getStringExtra(QmceMessageNotifier.EXTRA_PEER_NICKNAME)
                        .orEmpty()
                    selectedContact = RecentContactInfo().apply {
                        this.peerUid = peerUid
                        this.peerUin = peerUin
                        this.chatType = chatType
                        peerName = nick.ifBlank { peerUid }
                        id = if (chatType == 2) peerUid else peerUin.toString()
                    }
                    QmceMessageNotifier.cancelForChat(context, peerUid, chatType)
                    navController.navigate("chat") { launchSingleTop = true }
                }
                LaunchedEffect(currentNavRoute, selectedContact?.peerUid, selectedContact?.chatType) {
                    if (currentNavRoute == "chat") {
                        val contact = selectedContact
                        QmceForegroundSession.setActiveChat(contact?.peerUid, contact?.chatType)
                        contact?.peerUid?.let { uid ->
                            QmceMessageNotifier.cancelForChat(context, uid, contact.chatType)
                            val title = contact.peerName?.takeIf { it.isNotBlank() }
                                ?: contact.remark?.takeIf { it.isNotBlank() }
                                ?: uid
                            QmceRecentViewedChats.record(
                                context = context,
                                peerUid = uid,
                                peerUin = contact.peerUin,
                                chatType = contact.chatType,
                                title = title,
                            )
                            QmceMessageNotificationBuilder.syncShortcutsForRecent(context)
                        }
                    } else {
                        QmceForegroundSession.setActiveChat(null, null)
                    }
                }
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
                                onPageChanged = { mainPage = it },
                                onOpenSettings = {
                                    navController.navigate("settings") { launchSingleTop = true }
                                },
                                onOpenLogoutConfirmation = {
                                    navController.navigate("logoutConfirmation") {
                                        launchSingleTop = true
                                    }
                                },
                                onForceExit = {
                                    navController.navigate("forceExitConfirmation") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenQZoneComposer = {
                                    navController.navigate("qzoneComposer") { launchSingleTop = true }
                                },
                                onOpenQZoneDetail = { feed ->
                                    qZoneDetailTarget = feed
                                    navController.navigate("qzoneFeedDetail") {
                                        launchSingleTop = true
                                    }
                                },
                                onLogout = {
                                    (context.applicationContext as? QmceApplication)
                                        ?.clearLocalLoginState()
                                        ?: LoginPrefs.clear(context)
                                    selectedContact = null
                                    isLoggedIn = false
                                    loggedUin = ""
                                },
                                onOpenChat = { contact ->
                                    selectedContact = contact
                                    navController.navigate("chat") { launchSingleTop = true }
                                },
                                onOpenChatFromContacts = { uid, uin, name, chatType ->
                                    val fakeContact = RecentContactInfo().apply {
                                        peerUid = uid
                                        peerUin = uin.toLongOrNull() ?: 0L
                                        peerName = name
                                        this.chatType = chatType
                                        id = if (chatType == 2) uid else uin
                                    }
                                    selectedContact = fakeContact
                                    navController.navigate("chat") { launchSingleTop = true }
                                },
                                onOpenGroupFromContacts = { group ->
                                    val fakeContact = RecentContactInfo().apply {
                                        peerUid = group.groupCode.toString()
                                        peerUin = group.groupCode
                                        peerName = group.groupName
                                        chatType = 2
                                        id = group.groupCode.toString()
                                    }
                                    selectedContact = fakeContact
                                    navController.navigate("chat") { launchSingleTop = true }
                                },
                                onOpenContactProfile = { buddy ->
                                    selectedProfileBuddy = buddy
                                    navController.navigate("contactProfile") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenNotificationCenter = {
                                    navController.navigate("notificationCenter") {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                    composable("notificationCenter") {
                        NotificationCenterScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("contactProfile") {
                        selectedProfileBuddy?.let { buddy ->
                            ProfileScreen(
                                buddy = buddy,
                                onOpenChat = {
                                    val fakeContact = RecentContactInfo().apply {
                                        peerUid = buddy.uid
                                        peerUin = buddy.uin
                                        peerName = buddy.displayName()
                                        chatType = 1
                                        id = buddy.uin.toString()
                                        avatarPath = buddy.avatarPath
                                        avatarUrl = buddy.avatarUrls.firstOrNull().orEmpty()
                                    }
                                    selectedProfileBuddy = null
                                    selectedContact = fakeContact
                                    navController.navigate("chat") { launchSingleTop = true }
                                },
                                onBack = {
                                    selectedProfileBuddy = null
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                    composable("chat") {
                        val contact = selectedContact
                        if (contact != null) {
                            if (contact.chatType == rj.qmce.lite.agent.AgentSession.CHAT_TYPE) {
                                AgentChatRoute(
                                    onOpenInput = {
                                        navController.navigate("agentChatInput") {
                                            launchSingleTop = true
                                        }
                                    },
                                    onBack = {
                                        selectedContact = null
                                        navController.popBackStack()
                                    },
                                )
                            } else {
                                ChatDetailScreen(
                                    runtime = runtime,
                                    peerUid = contact.peerUid ?: "",
                                    peerUin = contact.peerUin.takeIf { it > 0L }?.toString()
                                        ?: contact.id.orEmpty(),
                                    chatType = contact.chatType,
                                    peerName = contact.peerName ?: contact.id ?: "",
                                    avatarPath = contact.avatarPath.orEmpty(),
                                    avatarUrl = contact.avatarUrl.orEmpty(),
                                    messageNavigation = MessageNavigationSnapshot.fromRecentContact(contact),
                                    myUin = loggedUin,
                                    onBack = { navController.popBackStack() },
                                    onOpenInput = {
                                        navController.navigate("chatInput") {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenComposerMenu = {
                                        navController.navigate("chatComposerMenu") {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenSingleEmotion = {
                                        navController.navigate("singleEmotionPicker") {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenVoiceRecorder = {
                                        navController.navigate("voiceRecord") {
                                            launchSingleTop = true
                                        }
                                    },
                                    onOpenContactPicker = {
                                        navController.navigate("contactPicker") {
                                            launchSingleTop = true
                                        }
                                    },
                                onOpenPacketTool = {
                                    navController.navigate("packetToolChat") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenMembers = {
                                    navController.navigate("chatMembers") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenGroupInfo = {
                                    if (contact.chatType == 2) {
                                        navController.navigate("groupInfo") {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onOpenChatSettings = {
                                    if (contact.chatType == 2) {
                                        groupManagementVm.load(
                                            contact.peerUin.takeIf { it > 0L }
                                                ?: contact.id?.toLongOrNull() ?: 0L,
                                        )
                                    }
                                    navController.navigate("chatSettings") {
                                        launchSingleTop = true
                                    }
                                },
                                onReportingPageChanged = { nestedOfficialPageId = it },
                                vm = chatDetailVm
                            )
                            }
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
                    composable("chatComposerMenu") {
                        ChatComposerMenuScreen(
                            onOpenText = {
                                navController.navigate("chatInput") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenVoice = {
                                navController.navigate("voiceRecord") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenMedia = {
                                navController.navigate("chatInputMedia") {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("chatInput") {
                        ChatInputRoute(
                            chatDetailVm = chatDetailVm,
                            context = context,
                            openToolsOnLaunch = false,
                            navController = navController,
                            onReportingPageChanged = { nestedOfficialPageId = it },
                        )
                    }
                    composable("agentChatInput") {
                        AgentChatInputRoute(
                            chatDetailVm = chatDetailVm,
                            navController = navController,
                        )
                    }
                    composable("agentVoiceRecord") {
                        AgentVoiceRecordRoute(
                            chatDetailVm = chatDetailVm,
                            navController = navController,
                        )
                    }
                    composable("chatInputMedia") {
                        ChatInputRoute(
                            chatDetailVm = chatDetailVm,
                            context = context,
                            openToolsOnLaunch = true,
                            navController = navController,
                            onReportingPageChanged = { nestedOfficialPageId = it },
                        )
                    }
                    composable("singleEmotionPicker") {
                        EmotionPickerScreen(
                            context = context,
                            onSelectSystemFace = { face ->
                                chatDetailVm.sendSingleEmotion(context, face)
                                navController.popBackStack()
                            },
                            onSelectMarketFace = { face ->
                                chatDetailVm.sendSingleEmotion(context, face)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("groupInfo") {
                        val contact = selectedContact
                        if (contact != null && contact.chatType == 2) {
                            val groupCode = contact.peerUin.takeIf { it > 0L }
                                ?: contact.id?.toLongOrNull() ?: 0L
                            GroupInfoScreen(
                                groupCode = groupCode,
                                avatarPath = contact.avatarPath.orEmpty(),
                                avatarUrl = contact.avatarUrl.orEmpty(),
                                vm = groupInfoVm,
                                onOpenMembers = {
                                    navController.navigate("chatMembers") {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenManagement = {
                                    navController.navigate("groupManagement") {
                                        launchSingleTop = true
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable("chatMembers") {
                        val contact = selectedContact
                        if (contact != null) {
                            ChatMembersScreen(
                                groupCode = contact.peerUin.takeIf { it > 0L }
                                    ?: contact.id?.toLongOrNull() ?: 0L,
                                vm = chatDetailVm,
                                onOpenMember = { member ->
                                    selectedGroupMember = member
                                    navController.navigate("groupMemberProfile") {
                                        launchSingleTop = true
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable("groupMemberProfile") {
                        val contact = selectedContact
                        val member = selectedGroupMember
                        if (contact != null && member != null) {
                            GroupMemberProfileScreen(
                                groupCode = contact.peerUin.takeIf { it > 0L }
                                    ?: contact.id?.toLongOrNull() ?: 0L,
                                member = member,
                                onBack = {
                                    selectedGroupMember = null
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                    composable("groupManagement") {
                        val contact = selectedContact
                        if (contact != null && contact.chatType == 2) {
                            GroupManagementScreen(
                                groupCode = contact.peerUin.takeIf { it > 0L }
                                    ?: contact.id?.toLongOrNull() ?: 0L,
                                vm = groupManagementVm,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable("chatSettings") {
                        val contact = selectedContact
                        if (contact != null) {
                            val peerUin =
                                contact.peerUin.takeIf { it > 0L } ?: contact.id?.toLongOrNull()
                                ?: 0L
                            ChatSettingsScreen(
                                contact = contact,
                                peerUid = contact.peerUid.orEmpty(),
                                peerUin = peerUin,
                                displayName = contact.peerName.orEmpty()
                                    .ifBlank { contact.id.orEmpty() },
                                vm = chatSettingsVm,
                                groupManagementState = groupManagementState,
                                onOpenGroupManagement = {
                                    navController.navigate("groupManagement") {
                                        launchSingleTop = true
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable("voiceRecord") {
                        VoiceRecordScreen(
                            onSendVoice = { file, durationMillis, formatType ->
                                chatDetailVm.sendVoice(file, durationMillis, formatType)
                            },
                            onTranscribedText = { text ->
                                chatDetailVm.setPendingVoiceText(text)
                                val cameFromInput =
                                    navController.previousBackStackEntry?.destination?.route == "chatInput"
                                navController.popBackStack()
                                if (!cameFromInput) {
                                    navController.navigate("chatInput") {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            isGroup = chatDetailVm.currentChatType == 2,
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
                            onOpenAppearance = {
                                navController.navigate("appearanceSettings") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenInteraction = {
                                navController.navigate("interactionSettings") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenNotifications = {
                                navController.navigate("notificationSettings") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenBackground = {
                                navController.navigate("backgroundSettings") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenCall = {
                                navController.navigate("callSettings") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenData = {
                                navController.navigate("dataSettings") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenIntelligence = {
                                navController.navigate("intelligenceSettings") {
                                    launchSingleTop = true
                                }
                            },
                            onOpenAboutHub = {
                                navController.navigate("aboutHub") {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    composable("notificationSettings") {
                        NotificationSettingsScreen(
                            settingsVm = settingsVm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("backgroundSettings") {
                        BackgroundSettingsScreen(
                            settingsVm = settingsVm,
                            onOpenWatchlist = {
                                navController.navigate("tileGroupPicker") {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("callSettings") {
                        CallSettingsScreen(
                            settingsVm = settingsVm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("dataSettings") {
                        DataSettingsScreen(
                            runtime = runtime,
                            chatListVm = chatListVm,
                            contactsVm = contactsVm,
                            qZoneVm = qZoneVm,
                            myVm = myVm,
                            storageVm = storageVm,
                            onOpenClearCache = {
                                navController.navigate("settingsClearChatCache") {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("intelligenceSettings") {
                        IntelligenceSettingsScreen(
                            settingsVm = settingsVm,
                            onOpenAgentDetails = {
                                navController.navigate("agentSettings") {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("aboutHub") {
                        AboutHubScreen(
                            settingsVm = settingsVm,
                            onOpenAbout = {
                                navController.navigate("about") { launchSingleTop = true }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    // Legacy redirects
                    composable("syncDataSettings") {
                        LaunchedEffect(Unit) {
                            navController.navigate("dataSettings") {
                                launchSingleTop = true
                                popUpTo("syncDataSettings") { inclusive = true }
                            }
                        }
                    }
                    composable("storageSettings") {
                        LaunchedEffect(Unit) {
                            navController.navigate("dataSettings") {
                                launchSingleTop = true
                                popUpTo("storageSettings") { inclusive = true }
                            }
                        }
                    }
                    composable("aiSettings") {
                        LaunchedEffect(Unit) {
                            navController.navigate("intelligenceSettings") {
                                launchSingleTop = true
                                popUpTo("aiSettings") { inclusive = true }
                            }
                        }
                    }
                    composable("checkUpdate") {
                        LaunchedEffect(Unit) {
                            navController.navigate("aboutHub") {
                                launchSingleTop = true
                                popUpTo("checkUpdate") { inclusive = true }
                            }
                        }
                    }
                    composable("diagnostics") {
                        LaunchedEffect(Unit) {
                            navController.navigate("aboutHub") {
                                launchSingleTop = true
                                popUpTo("diagnostics") { inclusive = true }
                            }
                        }
                    }
                    composable("tileWatchlist") {
                        LaunchedEffect(Unit) {
                            navController.navigate("tileGroupPicker") {
                                launchSingleTop = true
                                popUpTo("tileWatchlist") { inclusive = true }
                            }
                        }
                    }
                    composable("tileGroupPicker") {
                        val isWearDevice = rj.qmce.lite.util.QmceDevice.isWear(context)
                        LaunchedEffect(isWearDevice) {
                            if (!isWearDevice) navController.popBackStack()
                        }
                        if (isWearDevice) {
                            TileGroupPickerScreen(
                                runtime = runtime,
                                contactsVm = contactsVm,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable("appearanceSettings") {
                        AppearanceSettingsScreen(
                            settingsVm = settingsVm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("interactionSettings") {
                        InteractionSettingsScreen(
                            settingsVm = settingsVm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("developerToolsSettings") {
                        DeveloperToolsSettingsScreen(
                            onOpenPacketTool = {
                                navController.navigate("packetToolSettings") {
                                    launchSingleTop = true
                                }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("agentSettings") {
                        rj.qmce.lite.agent.ui.AgentSettingsScreen(
                            settingsVm = settingsVm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("qzoneFeedDetail") {
                        qZoneDetailTarget?.let { initialFeed ->
                            QZoneFeedDetailScreen(
                                feedId = initialFeed.feedId,
                                initialFeed = initialFeed,
                                vm = qZoneVm,
                                onOpenComment = { feed ->
                                    qZoneCommentTarget = feed
                                    qZoneCommentDraft = ""
                                    navController.navigate("qzoneComment") {
                                        launchSingleTop = true
                                    }
                                },
                                onBack = {
                                    qZoneDetailTarget = null
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                    composable("qzoneComposer") {
                        val publishState by qZoneVm.publishState.collectAsState()
                        val qZoneComposerUris =
                            (qZoneUris + qZonePickerUris).distinctBy(Uri::toString)
                        QZoneComposerScreen(
                            draft = qZoneDraft,
                            selectedUris = qZoneComposerUris,
                            publishState = publishState,
                            onDraftChange = { qZoneDraft = it },
                            onPickMedia = {
                                if (hasQZoneGalleryAccess(context)) {
                                    navController.navigate("qzoneImagePicker") {
                                        launchSingleTop = true
                                    }
                                } else {
                                    imagePermissionLauncher.launch(qZoneGalleryPermissions())
                                }
                            },
                            onPublish = {
                                qZoneVm.publishImages(context, qZoneDraft, qZoneComposerUris)
                            },
                            onPublishSucceeded = {
                                qZoneDraft = ""
                                qZoneUris = emptyList()
                                qZonePickerUris = emptyList()
                                qZoneVm.clearPublishState()
                                navController.popBackStack()
                            },
                            onBack = {
                                qZoneVm.clearPublishState()
                                navController.popBackStack()
                            },
                        )
                    }
                    composable("qzoneImagePicker") {
                        LocalImagePickerScreen(
                            existingUris = qZoneUris.mapTo(linkedSetOf()) { it.toString() },
                            selectedUris = qZonePickerUris.map(Uri::toString),
                            onSelectionChange = { uris -> qZonePickerUris = uris },
                            onDismiss = { navController.popBackStack() },
                            onConfirm = { uris ->
                                qZoneUris = (qZoneUris + uris).distinctBy(Uri::toString)
                                qZonePickerUris = emptyList()
                                navController.popBackStack()
                            },
                            reportQZoneElements = true,
                        )
                    }
                    composable("qzoneComment") {
                        qZoneCommentTarget?.let { feed ->
                            val replyTarget by qZoneVm.commentReplyTarget.collectAsState()
                            val commentSendState by qZoneVm.commentSendState.collectAsState()
                            QZoneCommentScreen(
                                feed = feed,
                                draft = qZoneCommentDraft,
                                replyTarget = replyTarget,
                                sendState = commentSendState,
                                onDraftChange = { qZoneCommentDraft = it },
                                onReply = { comment -> qZoneVm.prepareCommentReply(feed.feedId, comment) },
                                onCancelReply = qZoneVm::clearCommentReplyTarget,
                                onSendSucceeded = {
                                    qZoneCommentDraft = ""
                                    qZoneCommentTarget = null
                                    qZoneVm.clearCommentSendState()
                                    navController.popBackStack()
                                },
                                onSend = {
                                    qZoneVm.comment(feed.feedId, qZoneCommentDraft, replyTarget)
                                },
                                onBack = {
                                    qZoneVm.clearCommentReplyTarget()
                                    qZoneVm.clearCommentSendState()
                                    qZoneCommentDraft = ""
                                    qZoneCommentTarget = null
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                    composable("settingsClearChatCache") {
                        SettingsClearChatCacheScreen(
                            onConfirm = {
                                storageVm.clearAllCache()
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("logoutConfirmation") {
                        LogoutConfirmationScreen(
                            onConfirm = {
                                (context.applicationContext as? QmceApplication)?.clearLocalLoginState()
                                    ?: LoginPrefs.clear(context)
                                selectedContact = null
                                isLoggedIn = false
                                loggedUin = ""
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("forceExitConfirmation") {
                        ForceExitConfirmationScreen(
                            onConfirm = { QmceApplication.forceExit(context) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("about") {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable("packetToolSettings") {
                        PacketToolScreen(
                            vm = packetToolVm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            } else {
                OfficialReportPage(loginPageId) {
                    LoginScreen(
                        onPageIdChanged = { loginPageId = it },
                        onLoginSuccess = { uin, account ->
                            // Align with cold-start: save → await/retry core → mark → enter.
                            loginEnterScope.launch {
                                withContext(Dispatchers.IO) {
                                    LoginPrefs.saveAccount(context, account)
                                    val rt = QmceApplication.ensureRuntime()
                                    var coreReady = KernelBridge.areCoreServicesReady()
                                    if (!coreReady) {
                                        coreReady = KernelBridge.awaitCoreServices(
                                            timeoutMillis = 30_000,
                                            runtimeOverride = rt,
                                        )
                                    }
                                    if (!coreReady) {
                                        coreReady = KernelBridge.retryCoreServices(
                                            timeoutMillis = 15_000,
                                            runtimeOverride = rt,
                                        )
                                    }
                                    if (!coreReady) {
                                        Log.w(
                                            "QMCE",
                                            "ui: login enter with kernel not ready uin=$uin",
                                        )
                                    }
                                    QmceApplication.markLoginEstablished()
                                    withContext(Dispatchers.Main) {
                                        runtime = QmceApplication.ensureRuntime() ?: rt
                                        loggedUin = uin
                                        isLoggedIn = true
                                        Log.d(
                                            "QMCE",
                                            "ui: login in-process enter uin=$uin coreReady=$coreReady",
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
            rj.qmce.lite.ui.ota.OtaUpdateDialogHost()
            rj.qmce.lite.agent.ui.AgentApprovalDialogHost()
        }
    }
}

private fun officialPageId(route: String?, mainPage: Int): String? = when (route) {
    "main" -> when (mainPage) {
        0 -> OfficialReportBridge.PageIds.MESSAGE
        1 -> OfficialReportBridge.PageIds.CONTACTS
        2 -> OfficialReportBridge.PageIds.DYNAMIC_INFORMATION
        3 -> OfficialReportBridge.PageIds.SETTINGS
        else -> null
    }

    "chat", "chatInput" -> OfficialReportBridge.PageIds.AIO
    "singleEmotionPicker" -> OfficialReportBridge.PageIds.EXPRESSION
    "voiceRecord" -> null
    "agentChat", "agentChatInput", "agentVoiceRecord", "agentSettings" -> null
    "qzoneFeedDetail", "qzoneComment" -> OfficialReportBridge.PageIds.DYNAMIC_INFORMATION
    "qzoneComposer" -> OfficialReportBridge.PageIds.DYNAMIC_PUBLISH
    "qzoneImagePicker" -> OfficialReportBridge.PageIds.ALBUM_SELECTION
    "settingsClearChatCache" -> OfficialReportBridge.PageIds.CLEARS_MESSAGES
    "settings", "appearanceSettings", "interactionSettings", "notificationSettings",
    "backgroundSettings", "callSettings", "dataSettings", "intelligenceSettings", "aboutHub",
    "tileWatchlist", "tileGroupPicker", "syncDataSettings", "storageSettings", "packetToolSettings",
    "aiSettings", "checkUpdate", "diagnostics", "about", "developerToolsSettings" ->
        OfficialReportBridge.PageIds.SETTINGS
    "chatSettings" -> OfficialReportBridge.PageIds.SETTINGS
    else -> null
}

@Composable
private fun ChatInputRoute(
    chatDetailVm: rj.qmce.lite.viewmodel.ChatDetailViewModel,
    context: android.content.Context,
    openToolsOnLaunch: Boolean,
    navController: androidx.navigation.NavHostController,
    onReportingPageChanged: (String?) -> Unit,
) {
    val editingText by chatDetailVm.editingText.collectAsState()
    val pendingReplyTarget by chatDetailVm.pendingReplyTarget.collectAsState()
    ChatInputScreen(
        vm = chatDetailVm,
        peerUid = chatDetailVm.currentPeerUid,
        chatType = chatDetailVm.currentChatType,
        editingText = editingText,
        replyTarget = pendingReplyTarget,
        openToolsOnLaunch = openToolsOnLaunch,
        onConsumeReplyTarget = chatDetailVm::consumePendingReplyTarget,
        onSend = { text, target -> chatDetailVm.sendText(text, target) },
        onSendEdited = { text -> chatDetailVm.sendEditedText(text) },
        peerUin = chatDetailVm.currentPeerUin,
        onSendMixed = { mixedText, uriMap, atMap, emotionMap, target ->
            chatDetailVm.sendMixed(
                context,
                mixedText,
                uriMap,
                atMap,
                target,
                emotionMap,
            )
        },
        onSendVideo = { uri -> chatDetailVm.sendVideo(context, uri) },
        onOpenVoiceRecorder = {
            navController.navigate("voiceRecord") {
                launchSingleTop = true
            }
        },
        onReportingPageChanged = onReportingPageChanged,
        onBack = { navController.popBackStack() },
    )
}

@Composable
private fun SplashScreen(restoringLogin: Boolean = false) {
    val scheme = androidx.wear.compose.material3.MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colorResource(R.color.ic_launcher_qq_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_qq_splash),
                    contentDescription = "QQ",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            androidx.wear.compose.material3.Text(
                text = "QMCE",
                style = androidx.wear.compose.material3.MaterialTheme.typography.titleSmall,
                color = scheme.onBackground,
            )
            androidx.wear.compose.material3.Text(
                text = "QQ Max Compose Edition",
                style = androidx.wear.compose.material3.MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            androidx.wear.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            androidx.wear.compose.material3.Text(
                text = if (restoringLogin) "正在恢复登录…" else "正在启动…",
                style = androidx.wear.compose.material3.MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private fun qZoneGalleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun termKindIcon(kind: OnlineStatus.TermKind): ImageVector = when (kind) {
    OnlineStatus.TermKind.Phone -> Icons.Default.PhoneAndroid
    OnlineStatus.TermKind.Computer -> Icons.Default.Computer
    OnlineStatus.TermKind.Tablet -> Icons.Default.Tablet
    OnlineStatus.TermKind.Watch -> Icons.Default.Watch
    OnlineStatus.TermKind.Unknown -> Icons.Default.PhoneAndroid
}

private fun hasQZoneGalleryAccess(context: android.content.Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES,
        ) == PackageManager.PERMISSION_GRANTED || androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) == PackageManager.PERMISSION_GRANTED
    }

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES,
        ) == PackageManager.PERMISSION_GRANTED

    else -> androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED
}
