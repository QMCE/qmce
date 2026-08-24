package rj.qmce.lite.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.ui.MainActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object QmceMessageNotificationBuilder {
    private const val TAG = "QmceMessageNotif"
    private const val MAX_HISTORY = 5
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "qmce-notify-avatar").apply { isDaemon = true }
    }

    data class Visuals(
        val conversationIcon: Bitmap?,
        val senderIcon: Bitmap?,
        val imageUri: Uri?,
    )

    fun shortcutId(chatType: Int, peerUid: String): String = "qmce_${chatType}_$peerUid"

    fun chatIntent(
        context: Context,
        peerUid: String,
        peerUin: Long,
        chatType: Int,
        title: String,
    ): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        setPackage(BuildConfig.APPLICATION_ID)
        putExtra(QmceMessageNotifier.EXTRA_OPEN_CHAT, true)
        putExtra(QmceMessageNotifier.EXTRA_PEER_UID, peerUid)
        putExtra(QmceMessageNotifier.EXTRA_PEER_UIN, peerUin)
        putExtra(QmceMessageNotifier.EXTRA_CHAT_TYPE, chatType)
        putExtra(QmceMessageNotifier.EXTRA_PEER_NICKNAME, title)
    }

    fun build(
        context: Context,
        contact: RecentContactInfo,
        peerUid: String,
        title: String,
        text: String,
        visuals: Visuals,
        priorMessages: List<NotificationCompat.MessagingStyle.Message> = emptyList(),
    ): android.app.Notification {
        val chatType = contact.chatType
        val channel = if (chatType == 2) {
            QmceNotificationChannels.GROUP
        } else {
            QmceNotificationChannels.C2C
        }
        val intent = chatIntent(context, peerUid, contact.peerUin, chatType, title)
        val requestCode = QmceMessageNotifier.notifyId(peerUid, chatType)
        val pi = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Group: never attach sender avatar to Person (covers largeIcon / MessagingStyle avatar).
        val senderIcon = if (chatType == 2) {
            null
        } else {
            visuals.senderIcon ?: visuals.conversationIcon
        }
        val self = selfPerson()
        val sender = senderPerson(contact, title, chatType, senderIcon)
        val msgTimeMs = contact.msgTime.takeIf { it > 0L }?.let { t ->
            if (t < 10_000_000_000L) t * 1000L else t
        } ?: System.currentTimeMillis()
        val messageText = if (visuals.imageUri != null) " " else text
        val message = NotificationCompat.MessagingStyle.Message(messageText, msgTimeMs, sender)
        visuals.imageUri?.let { uri ->
            message.setData("image/jpeg", uri)
        }
        val style = NotificationCompat.MessagingStyle(self)
            .setGroupConversation(chatType == 2)
        if (chatType == 2) {
            style.conversationTitle = title
        }
        priorMessages.takeLast(MAX_HISTORY).forEach { style.addMessage(it) }
        style.addMessage(message)
        val allowShortcut = chatType == 2 ||
            QmceRecentViewedChats.contains(context, peerUid, chatType)
        val sid = shortcutId(chatType, peerUid)
        if (allowShortcut) {
            pushShortcut(context, sid, title, intent, visuals.conversationIcon)
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(style)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setNumber(contact.unreadCnt.toInt().coerceAtLeast(1))
        if (allowShortcut) {
            builder.setShortcutId(sid)
            if (Build.VERSION.SDK_INT >= 29) {
                builder.setLocusId(LocusIdCompat(sid))
            }
        }
        // largeIcon is conversation (group) avatar only — never sender.
        if (visuals.conversationIcon != null) {
            builder.setLargeIcon(visuals.conversationIcon)
        }
        return builder.build()
    }

    fun loadVisualsAsync(
        context: Context,
        contact: RecentContactInfo,
        onLoaded: (Visuals) -> Unit,
    ) {
        io.execute {
            val chatType = contact.chatType
            val conversation = loadConversationAvatar(contact, chatType)
                ?.let(QmceNotifyBitmaps::toCircle)
            val sender = if (chatType == 2) {
                null
            } else {
                conversation
            }
            val imageUri = runCatching {
                QmceNotifyMediaPreview.resolveImageContentUri(context, contact)
            }.onFailure {
                Log.w(TAG, "media preview failed", it)
            }.getOrNull()
            onLoaded(Visuals(conversation, sender, imageUri))
        }
    }

    fun syncShortcutsForRecent(context: Context) {
        val app = context.applicationContext
        val recent = QmceRecentViewedChats.load(app)
        val keepIds = recent.map { shortcutId(it.chatType, it.peerUid) }.toSet()
        val existing = runCatching {
            ShortcutManagerCompat.getDynamicShortcuts(app).map { it.id }
        }.getOrDefault(emptyList())
        val remove = existing.filterNot { it in keepIds }
        if (remove.isNotEmpty()) {
            runCatching { ShortcutManagerCompat.removeDynamicShortcuts(app, remove) }
        }
        recent.forEach { entry ->
            val intent = chatIntent(
                app,
                entry.peerUid,
                entry.peerUin,
                entry.chatType,
                entry.title,
            )
            pushShortcut(app, shortcutId(entry.chatType, entry.peerUid), entry.title, intent, null)
        }
    }

    private fun loadConversationAvatar(contact: RecentContactInfo, chatType: Int): Bitmap? {
        val peerCode = contact.peerUin.takeIf { it > 0L }
            ?: contact.peerUid?.toLongOrNull()?.takeIf { it > 0L }
        // Group: prefer official group qlogo so avatarPath (often last sender) never wins.
        if (chatType == 2 && peerCode != null) {
            val groupUrl = "https://p.qlogo.cn/gh/$peerCode/$peerCode/100"
            fetchBitmap(groupUrl)?.let { return it }
            Log.d(TAG, "group qlogo failed code=$peerCode, trying local/avatarUrl")
        }
        val path = contact.avatarPath?.removePrefix("file://")?.takeIf { it.isNotBlank() }
        if (path != null && chatType != 2) {
            val file = File(path)
            if (file.isFile) {
                val local = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                if (local != null) return local
            }
        }
        // For groups, only use avatarPath after qlogo failed (may still be wrong, last resort).
        if (chatType == 2 && path != null) {
            val file = File(path)
            if (file.isFile) {
                val local = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                if (local != null) return local
            }
        }
        val candidates = buildList {
            contact.avatarUrl?.takeIf { it.isNotBlank() }?.let(::add)
            if (chatType == 2 && peerCode != null) {
                add("https://p.qlogo.cn/gh/$peerCode/$peerCode/100")
            } else if (peerCode != null) {
                add("https://q1.qlogo.cn/g?b=qq&nk=$peerCode&s=100")
            }
        }.distinct()
        for (url in candidates) {
            val bitmap = fetchBitmap(url)
            if (bitmap != null) return bitmap
            Log.d(TAG, "avatar fetch failed chatType=$chatType code=$peerCode url=$url")
        }
        return null
    }

    private fun fetchBitmap(url: String): Bitmap? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500
            readTimeout = 2500
            instanceFollowRedirects = true
        }
        conn.inputStream.use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun selfPerson(): Person {
        val uin = runCatching {
            QmceApplication.ensureRuntime()?.currentUin
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "self"
        return Person.Builder()
            .setKey("self:$uin")
            .setName("我")
            .build()
    }

    private fun senderPerson(
        contact: RecentContactInfo,
        conversationTitle: String,
        chatType: Int,
        iconBitmap: Bitmap?,
    ): Person {
        val name = if (chatType == 2) {
            contact.sendRemarkName?.takeIf { it.isNotBlank() }
                ?: contact.sendMemberName?.takeIf { it.isNotBlank() }
                ?: contact.sendNickName?.takeIf { it.isNotBlank() }
                ?: "群成员"
        } else {
            conversationTitle
        }
        val key = if (chatType == 2) {
            contact.senderUid?.takeIf { it.isNotBlank() }
                ?: contact.senderUin.takeIf { it > 0L }?.toString()
                ?: "member"
        } else {
            contact.peerUid?.takeIf { it.isNotBlank() }
                ?: contact.peerUin.toString()
        }
        val builder = Person.Builder().setKey(key).setName(name).setImportant(true)
        if (iconBitmap != null) {
            builder.setIcon(IconCompat.createWithBitmap(iconBitmap))
        }
        return builder.build()
    }

    private fun pushShortcut(
        context: Context,
        id: String,
        label: String,
        intent: Intent,
        icon: Bitmap?,
    ) {
        runCatching {
            val shortcut = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(label.take(20).ifBlank { "会话" })
                .setLongLabel(label.ifBlank { "会话" })
                .setIntent(intent.setAction(Intent.ACTION_VIEW))
                .setLongLived(true)
                .setCategories(setOf("android.shortcut.conversation"))
                .apply {
                    if (icon != null) setIcon(IconCompat.createWithBitmap(icon))
                    if (Build.VERSION.SDK_INT >= 29) {
                        setLocusId(LocusIdCompat(id))
                    }
                }
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }
}
