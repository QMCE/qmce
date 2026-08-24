package rj.qmce.lite.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.R
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import rj.qmce.lite.notify.QmceMessageNotifier
import rj.qmce.lite.notify.QmceRecentContactText
import rj.qmce.lite.ui.MainActivity
import rj.qmce.lite.viewmodel.SettingsViewModel

internal object QmceComplicationSupport {
    fun complicationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).getBoolean(SettingsViewModel.KEY_WEAR_COMPLICATIONS, true)
    }

    fun launchMain(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                setPackage(BuildConfig.APPLICATION_ID)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun openGroupPicker(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).apply {
                setPackage(BuildConfig.APPLICATION_ID)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(QmceMessageNotifier.EXTRA_OPEN_TILE_GROUP_PICKER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun openChat(
        context: Context,
        peerUid: String,
        peerUin: Long,
        chatType: Int,
        name: String,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            setPackage(BuildConfig.APPLICATION_ID)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(QmceMessageNotifier.EXTRA_OPEN_CHAT, true)
            putExtra(QmceMessageNotifier.EXTRA_PEER_UID, peerUid)
            putExtra(QmceMessageNotifier.EXTRA_PEER_UIN, peerUin)
            putExtra(QmceMessageNotifier.EXTRA_CHAT_TYPE, chatType)
            putExtra(QmceMessageNotifier.EXTRA_PEER_NICKNAME, name)
        }
        return PendingIntent.getActivity(
            context,
            QmceMessageNotifier.notifyId(peerUid, chatType),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun buildLaunch(
        context: Context,
        type: ComplicationType,
    ): ComplicationData? {
        val tap = launchMain(context)
        val desc = PlainComplicationText.Builder("打开 QMCE").build()
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                PlainComplicationText.Builder("打开消息").build(),
                desc,
            ).setTitle(PlainComplicationText.Builder("QMCE").build())
                .setTapAction(tap)
                .build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder("消息").build(),
                desc,
            ).setTitle(PlainComplicationText.Builder("QMCE").build())
                .setTapAction(tap)
                .setMonochromaticImage(appMonoImage(context))
                .build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                appMonoImage(context),
                desc,
            ).setTapAction(tap).build()
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(
                    Icon.createWithResource(context, R.drawable.ic_launcher_qq),
                    SmallImageType.ICON,
                ).build(),
                desc,
            ).setTapAction(tap).build()
            else -> null
        }
    }

    fun buildMessage(
        context: Context,
        type: ComplicationType,
        title: String,
        text: String,
        tap: PendingIntent,
        contentDescription: String = "$title $text",
    ): ComplicationData? {
        val desc = PlainComplicationText.Builder(contentDescription).build()
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                PlainComplicationText.Builder(text.take(40)).build(),
                desc,
            ).setTitle(PlainComplicationText.Builder(title.take(24)).build())
                .setTapAction(tap)
                .build()
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(text.take(12)).build(),
                desc,
            ).setTitle(PlainComplicationText.Builder(title.take(12)).build())
                .setTapAction(tap)
                .build()
            else -> null
        }
    }

    fun latestMessageSummary(context: Context): Triple<String, String, PendingIntent>? {
        val recent = KernelBridge.getRecentContactService() ?: return null
        val list = runCatching { SdkCompat.getRecentContactFromCache(recent, 0) }
            .getOrNull()
            .orEmpty()
        val contact = list.firstOrNull { it.chatType == 1 || it.chatType == 2 } ?: return null
        val name = QmceRecentContactText.displayName(contact)
        val abstract = QmceRecentContactText.abstractText(contact)
        val uid = contact.peerUid.orEmpty().ifBlank { contact.peerUin.toString() }
        val tap = openChat(context, uid, contact.peerUin, contact.chatType, name)
        return Triple(name, abstract, tap)
    }

    private fun appMonoImage(context: Context): MonochromaticImage =
        MonochromaticImage.Builder(
            Icon.createWithResource(context, R.drawable.ic_launcher_qq),
        ).build()
}
