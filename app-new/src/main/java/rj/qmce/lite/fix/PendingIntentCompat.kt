package rj.qmce.lite.fix

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * PendingIntent 工厂桥：为 ASM-patched qq-sdk.jar 字节码补上 S+ 强制要求的
 * FLAG_IMMUTABLE（见 proguard-rules.pro 中 rj.qmce.lite.fix.** keep 规则）。
 *
 * 底包里的 PendingIntent.getActivity/getBroadcast 等调用没有带
 * FLAG_IMMUTABLE/FLAG_MUTABLE，在 targetSdk >= 31 的机器上创建时直接抛
 * IllegalArgumentException（PendingIntent.checkPendingIntent）。补丁脚本把
 * 常量池中 android/app/PendingIntent.getX 的 Methodref owner 改指向本类
 * （方法名与描述符不变，字节码栈帧无需重算），由这里统一补上 IMMUTABLE 并
 * 保留原 flags 的其余位。
 */
object PendingIntentCompat {

    @JvmStatic
    fun getActivity(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    @JvmStatic
    fun getActivity(context: Context, requestCode: Int, intent: Intent, flags: Int, options: Bundle?): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE, options)

    @JvmStatic
    fun getActivities(context: Context, requestCode: Int, intents: Array<Intent>, flags: Int): PendingIntent =
        PendingIntent.getActivities(context, requestCode, intents, flags or PendingIntent.FLAG_IMMUTABLE)

    @JvmStatic
    fun getActivities(context: Context, requestCode: Int, intents: Array<Intent>, flags: Int, options: Bundle?): PendingIntent =
        PendingIntent.getActivities(context, requestCode, intents, flags or PendingIntent.FLAG_IMMUTABLE, options)

    @JvmStatic
    fun getBroadcast(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    @JvmStatic
    fun getService(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent =
        PendingIntent.getService(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    @JvmStatic
    fun getForegroundService(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent =
        PendingIntent.getForegroundService(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)
}
