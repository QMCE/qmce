package rj.qmce.lite.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import rj.qmce.lite.notify.QmceNotificationChannels
import rj.qmce.lite.ui.MainActivity
import rj.qmce.lite.viewmodel.SettingsViewModel

class QmceKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        QmceNotificationChannels.ensure(this)
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, QmceNotificationChannels.KEEPALIVE)
            .setContentTitle("QMCE 运行中")
            .setContentText("保持消息连接")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    companion object {
        private const val NOTIFICATION_ID = 71001

        fun startIfEnabled(context: Context) {
            val prefs = context.applicationContext
                .getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(SettingsViewModel.KEY_KEEP_ALIVE, false)) return
            val intent = Intent(context, QmceKeepAliveService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, QmceKeepAliveService::class.java))
        }

        fun sync(context: Context, loggedIn: Boolean) {
            if (loggedIn) startIfEnabled(context) else stop(context)
        }
    }
}
