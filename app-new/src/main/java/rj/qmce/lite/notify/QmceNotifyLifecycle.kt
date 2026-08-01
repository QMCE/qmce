package rj.qmce.lite.notify

import android.content.Context
import android.util.Log
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.service.QmceKeepAliveService

/** Starts/stops message notify, contact notify, refresh, and optional FGS around login. */
object QmceNotifyLifecycle {
    private const val TAG = "QmceNotifyLifecycle"

    fun onLoggedIn(context: Context) {
        val app = context.applicationContext
        QmceNotificationChannels.ensure(app)
        reinforceMsf()
        QmceKeepAliveService.sync(app, loggedIn = true)
        QmceMessageNotifier.start(app)
        QmceContactSystemNotifier.start(app)
        QmceMessageRefreshScheduler.start(app, isLoggedIn = true)
        Log.d(TAG, "logged-in services started")
    }

    fun onLoggedOut(context: Context) {
        val app = context.applicationContext
        QmceMessageNotifier.stop()
        QmceContactSystemNotifier.stop()
        QmceMessageRefreshScheduler.stop()
        QmceKeepAliveService.sync(app, loggedIn = false)
        QmceForegroundSession.setActiveChat(null, null)
        QmceForegroundSession.appInForeground = false
        Log.d(TAG, "logged-out services stopped")
    }

    fun reinforceMsf() {
        runCatching {
            val msg = KernelBridge.getMsgService()
            msg?.switchForeGround(object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "switchForeGround code=$code err=$errMsg")
                }
            })
            msg?.startMsgSync()
        }.onFailure { Log.w(TAG, "MSF reinforce failed", it) }
        runCatching {
            val ctx = com.tencent.qphone.base.util.BaseApplication.getContext()
            ctx.sendBroadcast(
                android.content.Intent("com.tencent.mobileqq.msf.startmsf")
                    .setPackage(rj.qmce.lite.BuildConfig.APPLICATION_ID),
            )
        }.onFailure { Log.d(TAG, "startmsf broadcast skipped: ${it.message}") }
    }
}
