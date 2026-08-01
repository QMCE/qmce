package rj.qmce.lite.notify

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import rj.qmce.lite.viewmodel.SettingsViewModel

object QmceMessageRefreshScheduler {
    private const val TAG = "QmceMsgRefresh"
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var loggedIn = false

    private val tick = object : Runnable {
        override fun run() {
            val ctx = appContext ?: return
            val interval = intervalMillis(ctx)
            if (interval > 0L && loggedIn) {
                val keepAlive = ctx.getSharedPreferences(
                    SettingsViewModel.PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                ).getBoolean(SettingsViewModel.KEY_KEEP_ALIVE, true)
                if (QmceForegroundSession.appInForeground || keepAlive) {
                    refreshOnce(ctx)
                }
            }
            val next = intervalMillis(ctx).takeIf { it > 0L } ?: 60_000L
            handler.postDelayed(this, next)
        }
    }

    fun start(context: Context, isLoggedIn: Boolean) {
        appContext = context.applicationContext
        loggedIn = isLoggedIn
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun setLoggedIn(isLoggedIn: Boolean) {
        loggedIn = isLoggedIn
    }

    fun stop() {
        handler.removeCallbacks(tick)
        loggedIn = false
    }

    fun onSettingsChanged(context: Context) {
        appContext = context.applicationContext
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun intervalMillis(context: Context): Long {
        val mode = context.getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).getString(SettingsViewModel.KEY_MESSAGE_REFRESH_MODE, SettingsViewModel.REFRESH_PUSH_ONLY)
        return when (mode) {
            SettingsViewModel.REFRESH_15S -> 15_000L
            SettingsViewModel.REFRESH_30S -> 30_000L
            SettingsViewModel.REFRESH_1M -> 60_000L
            SettingsViewModel.REFRESH_5M -> 300_000L
            else -> 0L
        }
    }

    private fun refreshOnce(context: Context) {
        val recent = KernelBridge.getRecentContactService() ?: return
        runCatching {
            SdkCompat.getRecentContactFromCache(recent, 0)
            val anchor = com.tencent.qqnt.kernel.nativeinterface.AnchorPointContactInfo()
            SdkCompat.fetchRecentContactInfo(
                recent,
                anchor,
                false,
                0,
                50,
                object : com.tencent.qqnt.kernel.nativeinterface.IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        Log.d(TAG, "fetchRecentContactInfo code=$code err=$errMsg")
                    }
                },
            )
            KernelBridge.getMsgService()?.startMsgSync()
            QmceWearSurfaces.requestDataRefresh(context)
            Log.d(TAG, "lightweight recent refresh")
        }.onFailure { Log.w(TAG, "refresh failed", it) }
    }
}
