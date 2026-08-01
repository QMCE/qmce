package rj.qmce.lite.notify

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import rj.qmce.lite.R
import rj.qmce.lite.ui.call.QmceCallActivity
import rj.qmce.lite.util.QmceDevice
import rj.qmce.lite.viewmodel.SettingsViewModel

/**
 * Wear voice ongoing surface: Live Updates (API 37+) when enabled, else OngoingActivity,
 * with plain ongoing notification fallback.
 */
object QmcePromotedOngoing {
    private const val TAG = "QmcePromotedOngoing"
    private const val NOTIFICATION_ID = 72001

    fun showVoiceOngoing(context: Context, title: String, text: String) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        if (!prefs.getBoolean(SettingsViewModel.KEY_VOICE_BACKGROUND, true)) {
            cancel(app)
            return
        }
        if (!QmceDevice.isWear(app) ||
            !prefs.getBoolean(SettingsViewModel.KEY_VOICE_ONGOING_SURFACE, true)
        ) {
            // Non-Wear / surface off: still show a minimal ongoing if background voice is on.
            postPlainOngoing(app, title, text, requestPromoted = false)
            return
        }
        QmceNotificationChannels.ensure(app)
        val wantLive = prefs.getBoolean(SettingsViewModel.KEY_LIVE_UPDATES, true) &&
            Build.VERSION.SDK_INT >= 37
        postPlainOngoing(app, title, text, requestPromoted = wantLive)
        if (!wantLive) {
            attachOngoingActivity(app, title, text)
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun postPlainOngoing(
        context: Context,
        title: String,
        text: String,
        requestPromoted: Boolean,
    ) {
        val launch = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            android.content.Intent(context, QmceCallActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, QmceNotificationChannels.CALL)
            .setSmallIcon(R.drawable.ic_launcher_qq)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(launch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (requestPromoted) {
            applyRequestPromotedOngoing(builder)
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }.onFailure { Log.w(TAG, "voice ongoing notify failed", it) }
    }

    private fun attachOngoingActivity(context: Context, title: String, text: String) {
        runCatching {
            val launch = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID + 1,
                android.content.Intent(context, QmceCallActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val status = Status.Builder()
                .addTemplate(text.ifBlank { title })
                .build()
            val builder = NotificationCompat.Builder(context, QmceNotificationChannels.CALL)
                .setSmallIcon(R.drawable.ic_launcher_qq)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(launch)
            val ongoing = OngoingActivity.Builder(context, NOTIFICATION_ID, builder)
                .setStaticIcon(R.drawable.ic_launcher_qq)
                .setTouchIntent(launch)
                .setStatus(status)
                .build()
            ongoing.apply(context)
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }.onFailure { Log.d(TAG, "OngoingActivity skipped: ${it.message}") }
    }

    private fun applyRequestPromotedOngoing(builder: NotificationCompat.Builder) {
        runCatching {
            val m = builder.javaClass.methods.firstOrNull {
                it.name == "setRequestPromotedOngoing" && it.parameterTypes.size == 1
            }
            m?.invoke(builder, true)
        }.onFailure { Log.d(TAG, "setRequestPromotedOngoing unavailable: ${it.message}") }
    }
}
