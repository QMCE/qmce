package rj.qmce.lite.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tencent.qqnt.kernel.invorker.IExpandNotificationListener
import com.tencent.qqnt.kernel.nativeinterface.DeleteRecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.IKernelRecentContactListener
import com.tencent.qqnt.kernel.nativeinterface.NotificationCommonInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactExtra
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactListChangedInfo
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import rj.qmce.lite.viewmodel.SettingsViewModel
import java.lang.reflect.Proxy
import java.util.ArrayList
import java.util.HashMap

/**
 * Posts system notifications driven by official
 * [IExpandNotificationListener] / [IKernelRecentContactListener] paths.
 */
object QmceMessageNotifier {
    private const val TAG = "QmceMessageNotifier"
    private const val MAX_REGISTER_ATTEMPTS = 10

    private var expandListener: IExpandNotificationListener? = null
    private var kernelListener: IKernelRecentContactListener? = null
    private var registerAttempts = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null
    private val postedKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    const val EXTRA_PEER_UID = "PEER_UID"
    const val EXTRA_PEER_UIN = "PEER_UIN"
    const val EXTRA_CHAT_TYPE = "CHAT_TYPE"
    const val EXTRA_PEER_NICKNAME = "PEER_NICKNAME"
    const val EXTRA_OPEN_CHAT = "open_chatfragment"
    const val EXTRA_OPEN_NOTIFY_CENTER = "open_notification_center"
    const val EXTRA_OPEN_TILE_GROUP_PICKER = "open_tile_group_picker"

    fun peerKey(contact: RecentContactInfo): String =
        contact.peerUid?.takeIf { it.isNotBlank() }
            ?: contact.peerUin.takeIf { it > 0L }?.toString().orEmpty()

    fun peerKey(peerUid: String, peerUin: Long = 0L): String =
        peerUid.takeIf { it.isNotBlank() }
            ?: peerUin.takeIf { it > 0L }?.toString().orEmpty()

    fun notifyTag(chatType: Int, peer: String): String = "msg:$chatType:$peer"

    fun start(context: Context) {
        val app = context.applicationContext
        QmceNotificationChannels.ensure(app)
        stop()
        registerAttempts = 0
        tryRegister(app)
    }

    fun stop() {
        retryRunnable?.let { mainHandler.removeCallbacks(it) }
        retryRunnable = null
        val recent = KernelBridge.getRecentContactService()
        if (recent != null) {
            if (expandListener != null) {
                SdkCompat.clearExpandNotificationListener(recent)
            }
            kernelListener?.let { SdkCompat.removeKernelRecentContactListener(recent, it) }
        }
        expandListener = null
        kernelListener = null
        registerAttempts = 0
    }

    fun cancelForChat(context: Context, peerUid: String, chatType: Int) {
        val peer = peerKey(peerUid)
        val tag = notifyTag(chatType, peer)
        val id = notifyId(peer, chatType)
        NotificationManagerCompat.from(context).cancel(tag, id)
        postedKeys.remove("$tag#$id")
    }

    fun cancelAllMessageNotifications(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        val keys = synchronized(postedKeys) { postedKeys.toList().also { postedKeys.clear() } }
        keys.forEach { key ->
            val parts = key.split('#', limit = 2)
            if (parts.size == 2) {
                runCatching { nm.cancel(parts[0], parts[1].toIntOrNull() ?: 0) }
            }
        }
        Log.i(TAG, "cancelled ${keys.size} tracked message notifications")
    }

    private fun tryRegister(app: Context) {
        val recent = KernelBridge.getRecentContactService()
        if (recent == null) {
            scheduleRetry(app)
            return
        }
        val expand = Proxy.newProxyInstance(
            IExpandNotificationListener::class.java.classLoader,
            arrayOf(IExpandNotificationListener::class.java),
        ) { proxyInstance, method, args ->
            when (method.name) {
                "a" -> {
                    val contact = args?.getOrNull(0) as? RecentContactInfo
                    if (contact != null) {
                        Log.i(TAG, "expand callback chatType=${contact.chatType}")
                        onContactNotification(app, contact)
                    }
                    null
                }
                "hashCode" -> System.identityHashCode(proxyInstance)
                "equals" -> proxyInstance === args?.getOrNull(0)
                "toString" -> "QMCE-ExpandNotificationListener"
                else -> null
            }
        } as IExpandNotificationListener

        val kernel = object : IKernelRecentContactListener {
            override fun onDeletedContactsNotify(
                deleted: ArrayList<DeleteRecentContactInfo>?,
            ) = Unit

            override fun onMsgUnreadCountUpdate(
                unreadMap: HashMap<String, Int>?,
            ) = Unit

            override fun onRecentContactListChanged(
                sortedIds: ArrayList<Long>?,
                changedList: ArrayList<RecentContactInfo>?,
                extra: RecentContactExtra?,
            ) = Unit

            override fun onRecentContactListChangedVer2(
                infos: ArrayList<RecentContactListChangedInfo>?,
                listType: Int,
            ) = Unit

            override fun onRecentContactNotification(
                list: ArrayList<RecentContactInfo>?,
                commonInfo: NotificationCommonInfo?,
                seq: Int,
            ) {
                val size = list?.size ?: 0
                Log.i(TAG, "onRecentContactNotification size=$size seq=$seq")
                list?.forEach { onContactNotification(app, it) }
            }
        }

        val expandOk = SdkCompat.setExpandNotificationListener(recent, expand)
        val kernelOk = SdkCompat.addKernelRecentContactListener(recent, kernel)
        if (expandOk) expandListener = expand
        if (kernelOk) kernelListener = kernel
        Log.i(TAG, "listeners registered expand=$expandOk kernel=$kernelOk")
        if (!expandOk && !kernelOk) {
            scheduleRetry(app)
        }
    }

    private fun scheduleRetry(app: Context) {
        if (registerAttempts >= MAX_REGISTER_ATTEMPTS) {
            Log.w(TAG, "register gave up after $MAX_REGISTER_ATTEMPTS attempts")
            return
        }
        registerAttempts++
        val delayMs = when (registerAttempts) {
            1 -> 1000L
            2 -> 2000L
            else -> 5000L
        }
        Log.i(TAG, "schedule register retry #$registerAttempts in ${delayMs}ms")
        val runnable = Runnable { tryRegister(app) }
        retryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun onContactNotification(context: Context, contact: RecentContactInfo) {
        val prefs = context.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        if (!prefs.getBoolean(SettingsViewModel.KEY_NOTIFY_ENABLED, true)) {
            Log.i(TAG, "gate: notify disabled")
            return
        }
        val chatType = contact.chatType
        when (chatType) {
            1 -> if (!prefs.getBoolean(SettingsViewModel.KEY_NOTIFY_C2C, true)) {
                Log.i(TAG, "gate: c2c disabled")
                return
            }
            2 -> if (!prefs.getBoolean(SettingsViewModel.KEY_NOTIFY_GROUP, true)) {
                Log.i(TAG, "gate: group disabled")
                return
            }
            else -> {
                Log.i(TAG, "gate: unsupported chatType=$chatType")
                return
            }
        }
        val muted = contact.isMsgDisturb ||
            (contact.shieldFlag != 0L && contact.shieldFlag != 1L)
        if (muted) {
            Log.i(
                TAG,
                "gate: muted chatType=$chatType shieldFlag=${contact.shieldFlag} disturb=${contact.isMsgDisturb}",
            )
            return
        }
        val peer = peerKey(contact)
        if (peer.isBlank()) {
            Log.i(TAG, "gate: empty peer")
            return
        }
        if (QmceForegroundSession.matches(peer, chatType)) {
            cancelForChat(context, peer, chatType)
            Log.i(TAG, "gate: foreground session")
            return
        }
        if (!canPost(context)) {
            Log.i(TAG, "gate: no POST_NOTIFICATIONS")
            return
        }

        val title = QmceRecentContactText.displayName(contact)
        val text = QmceRecentContactText.abstractText(contact).ifBlank { "新消息" }
        val id = notifyId(peer, chatType)
        val tag = notifyTag(chatType, peer)
        postNow(
            context,
            contact,
            peer,
            title,
            text,
            QmceMessageNotificationBuilder.Visuals(null, null, null),
            tag,
            id,
        )
        QmceMessageNotificationBuilder.loadVisualsAsync(context, contact) { visuals ->
            mainHandler.post {
                postNow(context, contact, peer, title, text, visuals, tag, id)
            }
        }
        QmceWearSurfaces.requestDataRefresh(context)
    }

    private fun postNow(
        context: Context,
        contact: RecentContactInfo,
        peerUid: String,
        title: String,
        text: String,
        visuals: QmceMessageNotificationBuilder.Visuals,
        tag: String,
        id: Int,
    ) {
        val prior = extractPriorMessages(context, tag, id)
        val notification = QmceMessageNotificationBuilder.build(
            context = context,
            contact = contact,
            peerUid = peerUid,
            title = title,
            text = text,
            visuals = visuals,
            priorMessages = prior,
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(tag, id, notification)
            postedKeys.add("$tag#$id")
            Log.i(TAG, "posted tag=$tag id=$id chatType=${contact.chatType} title=$title")
        }.onFailure { Log.w(TAG, "notify failed", it) }
    }

    private fun extractPriorMessages(
        context: Context,
        tag: String,
        id: Int,
    ): List<NotificationCompat.MessagingStyle.Message> {
        val notification = runCatching {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm?.activeNotifications
                ?.firstOrNull { it.id == id && it.tag == tag }
                ?.notification
        }.getOrNull() ?: return emptyList()
        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)
            ?: return emptyList()
        return style.messages.orEmpty()
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun notifyId(peerUid: String, chatType: Int): Int {
        var h = 17
        h = 31 * h + chatType
        h = 31 * h + peerUid.hashCode()
        return h and 0x7fffffff
    }
}
