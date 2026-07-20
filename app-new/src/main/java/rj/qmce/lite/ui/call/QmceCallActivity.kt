package rj.qmce.lite.ui.call

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.FragmentActivity
import android.widget.FrameLayout
import rj.qmce.lite.data.reporting.LocalOfficialReportHost
import rj.qmce.lite.data.call.CallPhase
import rj.qmce.lite.data.call.QmceCallController
import rj.qmce.lite.ui.theme.QmceTheme

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
                QmceTheme {
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

    override fun onDestroy() {
        QmceCallController.onActivityDestroyed(
            isFinishing = isFinishing,
            isChangingConfigurations = isChangingConfigurations,
        )
        super.onDestroy()
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
