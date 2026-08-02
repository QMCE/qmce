package rj.qmce.lite.ui.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentActivity
import rj.qmce.lite.data.call.CallMode
import rj.qmce.lite.data.call.CallPhase
import rj.qmce.lite.data.call.QmceCallController
import rj.qmce.lite.data.reporting.LocalOfficialReportHost
import rj.qmce.lite.notify.QmcePromotedOngoing
import rj.qmce.lite.ui.theme.QmceTheme
import rj.qmce.lite.util.QmceDevice
import rj.qmce.lite.viewmodel.SettingsViewModel

open class QmceCallActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        QmceCallController.onServiceStarted(applicationContext, intent)
        if (QmceCallController.state.value.phase == CallPhase.Incoming) {
            showOverLockScreen()
        }
        val reportHost = FrameLayout(this).apply {
            id = View.generateViewId()
        }
        // 通话页是独立 Activity，不走 MainActivity 的 QmceTheme 参数：
        // 直接读 SharedPreferences 补传"边缘安全区"设置，保证与主界面一致。
        val callPrefs = getSharedPreferences(
            SettingsViewModel.PREFERENCES_NAME,
            android.content.Context.MODE_PRIVATE,
        )
        val edgeSafeAreaEnabled = callPrefs.getBoolean(
            SettingsViewModel.KEY_EDGE_SAFE_AREA_ENABLED,
            true,
        )
        val edgeSafeAreaScale = callPrefs.getFloat(
            SettingsViewModel.KEY_EDGE_SAFE_AREA_SCALE,
            1.0f,
        )
        val composeView = ComposeView(this).apply {
            id = View.generateViewId()
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        reportHost.addView(composeView)
        composeView.setContent {
            CompositionLocalProvider(
                LocalOfficialReportHost provides reportHost,
            ) {
                QmceTheme(
                    edgeSafeAreaEnabled = edgeSafeAreaEnabled,
                    edgeSafeAreaScale = edgeSafeAreaScale,
                ) {
                    QmceCallScreen(onFinish = ::finish)
                }
            }
        }
        setContentView(reportHost)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        QmceCallController.onServiceStarted(applicationContext, intent)
        if (QmceCallController.state.value.phase == CallPhase.Incoming) {
            showOverLockScreen()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        handleLeaveForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            handleLeaveForeground()
            maybeShowVoiceOngoing()
        }
    }

    override fun onResume() {
        super.onResume()
        QmcePromotedOngoing.cancel(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!QmceDevice.isWear(this)) {
            return super.dispatchKeyEvent(event)
        }
        val state = QmceCallController.state.value
        if (state.phase !in setOf(
                CallPhase.Outgoing,
                CallPhase.Connecting,
                CallPhase.Active,
                CallPhase.Incoming,
            )
        ) {
            return super.dispatchKeyEvent(event)
        }
        // Wear stem keys: KEYCODE_STEM_1=265, STEM_2=266, STEM_3=267 (API constants vary).
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                265, 266, KeyEvent.KEYCODE_ENTER -> {
                    QmceCallController.hangUp()
                    finish()
                    return true
                }
                else -> {
                    // Swallow other hardware keys during call on Wear.
                    if (event.keyCode != KeyEvent.KEYCODE_BACK) {
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        QmcePromotedOngoing.cancel(this)
        QmceCallController.onActivityDestroyed(
            isFinishing = isFinishing,
            isChangingConfigurations = isChangingConfigurations,
        )
        super.onDestroy()
    }

    private fun handleLeaveForeground() {
        val prefs = getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, MODE_PRIVATE)
        val state = QmceCallController.state.value
        if (state.phase !in setOf(CallPhase.Outgoing, CallPhase.Connecting, CallPhase.Active)) {
            return
        }
        when (state.mode) {
            CallMode.Video -> {
                if (!prefs.getBoolean(SettingsViewModel.KEY_VIDEO_STRICT_FOREGROUND, true)) return
                // Prefer bring-to-front; hang up if activity is finishing path elsewhere.
                open(this)
            }
            CallMode.Voice -> {
                if (!prefs.getBoolean(SettingsViewModel.KEY_VOICE_BACKGROUND, true)) {
                    QmceCallController.hangUp()
                    finish()
                }
            }
        }
    }

    private fun maybeShowVoiceOngoing() {
        val prefs = getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, MODE_PRIVATE)
        val state = QmceCallController.state.value
        if (state.mode != CallMode.Voice) return
        if (!prefs.getBoolean(SettingsViewModel.KEY_VOICE_BACKGROUND, true)) return
        if (state.phase !in setOf(CallPhase.Outgoing, CallPhase.Connecting, CallPhase.Active)) return
        val name = state.peer?.name?.takeIf { it.isNotBlank() } ?: "语音通话"
        QmcePromotedOngoing.showVoiceOngoing(this, name, "语音通话中")
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    companion object {
        fun open(context: Context) {
            context.startActivity(
                Intent(context, QmceCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
}
