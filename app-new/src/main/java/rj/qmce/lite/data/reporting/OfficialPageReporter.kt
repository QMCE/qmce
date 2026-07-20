package rj.qmce.lite.data.reporting

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalOfficialReportHost = staticCompositionLocalOf<FrameLayout?> { null }

private const val RETRY_DELAY_MILLIS = 250L
private const val MAX_RETRY_ATTEMPTS = 40

@Composable
fun OfficialReportPage(
    pageId: String?,
    modifier: Modifier = Modifier,
    params: Map<String, *> = emptyMap<String, Any?>(),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) { content() }
    OfficialReportLifecycle(pageId, params)
}

@Composable
fun OfficialReportLifecycle(
    pageId: String?,
    params: Map<String, *> = emptyMap<String, Any?>(),
) {
    if (pageId.isNullOrBlank()) return

    val reportHost = LocalOfficialReportHost.current ?: return
    DisposableEffect(reportHost, pageId, params) {
        val session = OfficialPageSession(reportHost, pageId, params)
        session.start()
        onDispose { session.stop() }
    }
}

private class OfficialPageSession(
    private val pageView: FrameLayout,
    private val pageId: String,
    private val params: Map<String, *>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var reportedIn = false
    private var attempts = 0

    private val retry = object : Runnable {
        override fun run() {
            if (!active || reportedIn) return
            if (!pageView.isAttachedToWindow || pageView.visibility != View.VISIBLE) {
                retryLater()
                return
            }
            if (OfficialReportBridge.reportPageIn(pageView, pageId, params)) {
                reportedIn = true
                return
            }
            retryLater()
        }
    }

    fun start() {
        active = true
        attempts = 0
        handler.post(retry)
    }

    fun stop() {
        active = false
        handler.removeCallbacks(retry)
        if (reportedIn) {
            OfficialReportBridge.reportPageOut(pageView)
            OfficialReportBridge.destroyPage(pageView)
            reportedIn = false
        }
    }

    private fun retryLater() {
        if (!active || attempts++ >= MAX_RETRY_ATTEMPTS) return
        handler.postDelayed(retry, RETRY_DELAY_MILLIS)
    }
}
