package rj.qmce.lite.notify

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object QmceNotificationChannels {
    const val GROUP_MESSAGES = "qmce_group_messages"
    const val C2C = "qmce_msg_c2c"
    const val GROUP = "qmce_msg_group"
    const val CONTACT = "qmce_contact"
    const val KEEPALIVE = "qmce_keepalive"
    const val CALL = "qmce_call"
    const val OTA = "qmce_ota_progress"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_MESSAGES, "消息通知"),
        )
        fun channel(id: String, name: String, importance: Int, group: String? = GROUP_MESSAGES) {
            val ch = NotificationChannel(id, name, importance)
            if (group != null) ch.group = group
            nm.createNotificationChannel(ch)
        }
        channel(C2C, "私聊消息", NotificationManager.IMPORTANCE_DEFAULT)
        channel(GROUP, "群消息", NotificationManager.IMPORTANCE_DEFAULT)
        channel(CONTACT, "好友与群系统通知", NotificationManager.IMPORTANCE_DEFAULT)
        channel(CALL, "通话", NotificationManager.IMPORTANCE_HIGH)
        channel(KEEPALIVE, "后台保活", NotificationManager.IMPORTANCE_LOW, group = null)
        channel(OTA, "应用更新下载", NotificationManager.IMPORTANCE_LOW, group = null)
    }
}
