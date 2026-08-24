package rj.qmce.lite.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import rj.qmce.lite.notify.QmceNotificationChannels
import rj.qmce.lite.ui.MainActivity
import rj.qmce.lite.util.QmceDevice
import rj.qmce.lite.util.QmceLog
import rj.qmce.lite.viewmodel.SettingsViewModel
import java.io.File

object OtaProgressNotifier {
    private const val TAG = "QmceOta"
    const val NOTIFICATION_ID = 73001

    fun showProgress(context: Context, percent: Int, indeterminate: Boolean = false) {
        val app = context.applicationContext
        QmceNotificationChannels.ensure(app)
        val launch = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(app, QmceNotificationChannels.OTA)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下载更新")
            .setContentText(if (indeterminate) "下载中…" else "$percent%")
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, percent.coerceIn(0, 100), indeterminate)
        maybePromote(app, builder)
        runCatching {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, builder.build())
        }.onFailure { QmceLog.w(TAG, "ota progress notify failed", it) }
    }

    fun showCompleted(context: Context, apkFile: File) {
        val app = context.applicationContext
        QmceNotificationChannels.ensure(app)
        val uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            apkFile,
        )
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID + 1,
            install,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(app, QmceNotificationChannels.OTA)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载完成")
            .setContentText("点按安装更新")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
        runCatching {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, builder.build())
        }.onFailure { QmceLog.w(TAG, "ota complete notify failed", it) }
    }

    fun showFailed(context: Context, reason: String) {
        val app = context.applicationContext
        QmceNotificationChannels.ensure(app)
        val builder = NotificationCompat.Builder(app, QmceNotificationChannels.OTA)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("更新下载失败")
            .setContentText(reason.take(80))
            .setAutoCancel(true)
            .setOngoing(false)
        runCatching {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, builder.build())
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    private fun maybePromote(context: Context, builder: NotificationCompat.Builder) {
        if (!QmceDevice.isWear(context)) return
        val prefs = context.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val wantLive = prefs.getBoolean(SettingsViewModel.KEY_LIVE_UPDATES, false) &&
            Build.VERSION.SDK_INT >= 36
        if (!wantLive) return
        runCatching {
            val m = builder.javaClass.methods.firstOrNull {
                it.name == "setRequestPromotedOngoing" && it.parameterTypes.size == 1
            }
            m?.invoke(builder, true)
        }.onFailure { QmceLog.d(TAG, "promoted ongoing unavailable: ${it.message}") }
    }
}
