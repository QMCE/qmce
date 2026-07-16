@file:OptIn(androidx.wear.compose.foundation.ExperimentalWearFoundationApi::class)

package rj.qmce.lite.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.curvedText
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.compose.material3.timeTextSeparator
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.R
import rj.qmce.lite.data.LoginPrefs
import rj.qmce.lite.data.OnlineStatus
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.ui.screens.AboutScreen
import rj.qmce.lite.ui.screens.AppearanceSettingsScreen
import rj.qmce.lite.ui.screens.ChatDetailScreen
import rj.qmce.lite.ui.screens.ChatInputScreen
import rj.qmce.lite.ui.screens.ChatMembersScreen
import rj.qmce.lite.ui.screens.ChatSettingsScreen
import rj.qmce.lite.ui.screens.ContactPickerScreen
import rj.qmce.lite.ui.screens.InteractionSettingsScreen
import rj.qmce.lite.ui.screens.LocalImagePickerScreen
import rj.qmce.lite.ui.screens.LoginScreen
import rj.qmce.lite.ui.screens.LogoutConfirmationScreen
import rj.qmce.lite.ui.screens.MainScreen
import rj.qmce.lite.ui.screens.PacketToolScreen
import rj.qmce.lite.ui.screens.QZoneCommentScreen
import rj.qmce.lite.ui.screens.QZoneComposerScreen
import rj.qmce.lite.ui.screens.QZoneFeedDetailScreen
import rj.qmce.lite.ui.screens.SettingsClearChatCacheScreen
import rj.qmce.lite.ui.screens.SettingsScreen
import rj.qmce.lite.ui.screens.StorageSettingsScreen
import rj.qmce.lite.ui.screens.SyncDataSettingsScreen
import rj.qmce.lite.ui.screens.VoiceRecordScreen
import rj.qmce.lite.ui.theme.QmceTheme
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import rj.qmce.lite.viewmodel.ChatListViewModel
import rj.qmce.lite.viewmodel.ChatSettingsViewModel
import rj.qmce.lite.viewmodel.ContactsViewModel
import rj.qmce.lite.viewmodel.MyViewModel
import rj.qmce.lite.viewmodel.PacketToolViewModel
import rj.qmce.lite.viewmodel.QZoneViewModel
import rj.qmce.lite.viewmodel.SettingsViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
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
    var qZoneDraft by remember { mutableStateOf("") }
    var qZoneUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var qZonePickerUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var qZoneCommentTarget by remember { mutableStateOf<QZoneViewModel.FeedItem?>(null) }
    var qZoneCommentDraft by remember { mutableStateOf("") }
    var qZoneDetailTarget by remember { mutableStateOf<QZoneViewModel.FeedItem?>(null) }
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
            Log.e("QMCE","Not logged in")
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
        QmceTheme(
            autoScale = settings.autoScale,
            manualScale = settings.manualScale,
        ) {
            SplashScreen()
        }
        return
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
    ) {
        val themeColors = androidx.wear.compose.material3.MaterialTheme.colorScheme
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
                                        themeColors.tertiary
                                    else
                                        themeColors.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        ) {
        if (isLoggedIn) {
            val navController = checkNotNull(appNavController)
            val chatDetailVm: ChatDetailViewModel = viewModel()
            val chatSettingsVm: ChatSettingsViewModel = viewModel()
            val chatListVm: ChatListViewModel = viewModel()
            val contactsVm: ContactsViewModel = viewModel()
            val qZoneVm: QZoneViewModel = viewModel()
            val myVm: MyViewModel = viewModel()
            val packetToolVm: PacketToolViewModel = viewModel()
            val imagePermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                if (hasQZoneGalleryAccess(context)) {
                    navController.navigate("qzoneImagePicker") { launchSingleTop = true }
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
                        onOpenSettings = {
                            navController.navigate("settings") { launchSingleTop = true }
                        },
                        onOpenLogoutConfirmation = {
                            navController.navigate("logoutConfirmation") { launchSingleTop = true }
                        },
                        onOpenQZoneComposer = {
                            navController.navigate("qzoneComposer") { launchSingleTop = true }
                        },
                        onOpenQZoneDetail = { feed ->
                            qZoneDetailTarget = feed
                            navController.navigate("qzoneFeedDetail") { launchSingleTop = true }
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
                            onOpenVoiceRecorder = { navController.navigate("voiceRecord") { launchSingleTop = true } },
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
                        onOpenAppearance = {
                            navController.navigate("appearanceSettings") { launchSingleTop = true }
                        },
                        onOpenInteraction = {
                            navController.navigate("interactionSettings") { launchSingleTop = true }
                        },
                        onOpenSyncData = {
                            navController.navigate("syncDataSettings") { launchSingleTop = true }
                        },
                        onOpenStorage = {
                            navController.navigate("storageSettings") { launchSingleTop = true }
                        },
                        onOpenAbout = {
                            navController.navigate("about") { launchSingleTop = true }
                        },
                    )
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
                composable("syncDataSettings") {
                    SyncDataSettingsScreen(
                        runtime = runtime,
                        chatListVm = chatListVm,
                        contactsVm = contactsVm,
                        qZoneVm = qZoneVm,
                        myVm = myVm,
                        onOpenPacketTool = {
                            navController.navigate("packetToolSettings") { launchSingleTop = true }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable("storageSettings") {
                    StorageSettingsScreen(
                        onOpenClearCache = {
                            navController.navigate("settingsClearChatCache") { launchSingleTop = true }
                        },
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
                                navController.navigate("qzoneComment") { launchSingleTop = true }
                            },
                            onBack = {
                                qZoneDetailTarget = null
                                navController.popBackStack()
                            },
                        )
                    }
                }
                composable("qzoneComposer") {
                    val qZoneComposerUris = (qZoneUris + qZonePickerUris).distinctBy(Uri::toString)
                    QZoneComposerScreen(
                        draft = qZoneDraft,
                        selectedUris = qZoneComposerUris,
                        onDraftChange = { qZoneDraft = it },
                        onPickMedia = {
                            if (hasQZoneGalleryAccess(context)) {
                                navController.navigate("qzoneImagePicker") { launchSingleTop = true }
                            } else {
                                imagePermissionLauncher.launch(qZoneGalleryPermissions())
                            }
                        },
                        onPublish = {
                            qZoneVm.publishImages(context, qZoneDraft, qZoneComposerUris)
                            qZoneDraft = ""
                            qZoneUris = emptyList()
                            qZonePickerUris = emptyList()
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
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
                    )
                }
                composable("qzoneComment") {
                    qZoneCommentTarget?.let { feed ->
                        QZoneCommentScreen(
                            feed = feed,
                            draft = qZoneCommentDraft,
                            onDraftChange = { qZoneCommentDraft = it },
                            onSend = {
                                qZoneVm.comment(feed.feedId, qZoneCommentDraft)
                                qZoneCommentDraft = ""
                                qZoneCommentTarget = null
                                navController.popBackStack()
                            },
                            onBack = {
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
                            myVm.clearChatCache()
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
            LoginScreen(onLoginSuccess = { uin, account ->
                LoginPrefs.saveAccount(context, account)
                QmceApplication.markLoginEstablished()
                val restarted = QmceApplication.restartAfterLogin(context)
                Log.d("QMCE", "ui: login persisted uin=$uin, scheduled fresh start=$restarted")
            })
        }
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
            Spacer(Modifier.height(16.dp))
            androidx.wear.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
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
