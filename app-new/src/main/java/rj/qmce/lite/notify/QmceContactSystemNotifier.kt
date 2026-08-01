package rj.qmce.lite.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import rj.qmce.lite.data.notify.ContactNotifyRepository
import rj.qmce.lite.data.notify.GroupNotifyRepository
import rj.qmce.lite.data.notify.UiFriendRequest
import rj.qmce.lite.data.notify.UiGroupNotice
import rj.qmce.lite.ui.MainActivity
import rj.qmce.lite.viewmodel.SettingsViewModel

/** QMCE enhancement: system notifications for friend/group pending items. */
object QmceContactSystemNotifier {
    private const val TAG = "QmceContactSysNotify"
    private val seenFriend = mutableSetOf<String>()
    private val seenGroup = mutableSetOf<String>()
    private var appContext: Context? = null
    private var friendPrimed = false
    private var groupPrimed = false

    private val friendRepo = ContactNotifyRepository { items ->
        val ctx = appContext ?: return@ContactNotifyRepository
        val pending = items.filter { it.pending }
        if (!friendPrimed) {
            pending.forEach { seenFriend.add(friendKey(it)) }
            friendPrimed = true
            return@ContactNotifyRepository
        }
        pending.forEach { postFriend(ctx, it) }
    }
    private val groupRepo = GroupNotifyRepository { items ->
        val ctx = appContext ?: return@GroupNotifyRepository
        val pending = items.filter { it.pending }
        if (!groupPrimed) {
            pending.forEach { seenGroup.add(groupKey(it)) }
            groupPrimed = true
            return@GroupNotifyRepository
        }
        pending.forEach { postGroup(ctx, it) }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        friendPrimed = false
        groupPrimed = false
        seenFriend.clear()
        seenGroup.clear()
        QmceNotificationChannels.ensure(context)
        runCatching { friendRepo.start() }
            .onFailure { Log.w(TAG, "friend repo start failed", it) }
        runCatching { groupRepo.start() }
            .onFailure { Log.w(TAG, "group repo start failed", it) }
    }

    fun stop() {
        runCatching { friendRepo.stop() }
        runCatching { groupRepo.stop() }
        appContext = null
        friendPrimed = false
        groupPrimed = false
        seenFriend.clear()
        seenGroup.clear()
    }

    private fun enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        return prefs.getBoolean(SettingsViewModel.KEY_NOTIFY_ENABLED, true) &&
            prefs.getBoolean(SettingsViewModel.KEY_NOTIFY_CONTACT, true)
    }

    private fun friendKey(item: UiFriendRequest) = "f:${item.uid}:${item.reqTime}"
    private fun groupKey(item: UiGroupNotice) = "g:${item.seq}:${item.groupCode}"

    private fun postFriend(context: Context, item: UiFriendRequest) {
        if (!enabled(context)) return
        val key = friendKey(item)
        if (!seenFriend.add(key)) return
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            setPackage(rj.qmce.lite.BuildConfig.APPLICATION_ID)
            putExtra(QmceMessageNotifier.EXTRA_OPEN_NOTIFY_CENTER, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = item.message.ifBlank { "请求添加你为好友" }
        val n = NotificationCompat.Builder(context, QmceNotificationChannels.CONTACT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(item.nick.ifBlank { "新朋友" })
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(0x62000000 or (key.hashCode() and 0xffff), n)
        }.onFailure { Log.w(TAG, "friend notify failed", it) }
    }

    private fun postGroup(context: Context, item: UiGroupNotice) {
        if (!enabled(context)) return
        val key = groupKey(item)
        if (!seenGroup.add(key)) return
        if (!canPost(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            setPackage(rj.qmce.lite.BuildConfig.APPLICATION_ID)
            putExtra(QmceMessageNotifier.EXTRA_OPEN_NOTIFY_CENTER, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, QmceNotificationChannels.CONTACT)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(item.title.ifBlank { "群通知" })
            .setContentText(item.subtitle.ifBlank { "有待处理的群通知" })
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(0x63000000 or (key.hashCode() and 0xffff), n)
        }.onFailure { Log.w(TAG, "group notify failed", it) }
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
