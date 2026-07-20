package rj.qmce.lite.data.reporting

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import android.os.Handler
import android.os.Looper

@Composable
fun rememberOfficialReportTarget(key: Any): View {
    val context = LocalContext.current
    return remember(key) {
        View(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setWillNotDraw(true)
        }
    }
}

@Composable
fun OfficialReportTargetAnchor(
    target: View,
    modifier: Modifier = Modifier,
    elementId: String? = null,
    params: Map<String, *> = emptyMap<String, Any?>(),
    reuseIdentifier: String? = null,
    reportImpression: Boolean = false,
    reportAllExposures: Boolean = false,
) {
    var impressionReported by remember(target) { mutableStateOf(false) }

    DisposableEffect(target, elementId, params, reuseIdentifier, reportImpression) {
        val handler = Handler(Looper.getMainLooper())
        var attempts = 0
        val retry = object : Runnable {
            override fun run() {
                if (target.isAttachedToWindow && target.visibility == View.VISIBLE) {
                    val configured = elementId.isNullOrBlank() || OfficialReportBridge.configureViewElement(
                            target = target,
                            elementId = elementId,
                            params = params,
                            reuseIdentifier = reuseIdentifier,
                            reportAllExposures = reportAllExposures,
                        )
                    if (configured) {
                        if (reportImpression && !impressionReported && !elementId.isNullOrBlank()) {
                            impressionReported = OfficialReportBridge.reportViewElementImpression(
                                target = target,
                                elementId = elementId,
                                params = params,
                                reuseIdentifier = reuseIdentifier,
                            )
                        }
                        return
                    }
                }
                if (attempts++ < 40) {
                    handler.postDelayed(this, 250L)
                }
            }
        }
        handler.post(retry)
        onDispose { handler.removeCallbacks(retry) }
    }

    AndroidView(
        factory = { target },
        modifier = modifier,
        update = { view ->
            if (!elementId.isNullOrBlank()) {
                OfficialReportBridge.configureViewElement(
                    target = view,
                    elementId = elementId,
                    params = params,
                    reuseIdentifier = reuseIdentifier,
                    reportAllExposures = reportAllExposures,
                )
            }
        },
    )
}

@Composable
fun OfficialReportTargetBox(
    key: Any,
    modifier: Modifier = Modifier,
    elementId: String? = null,
    params: Map<String, *> = emptyMap<String, Any?>(),
    reuseIdentifier: String? = null,
    reportImpression: Boolean = false,
    reportAllExposures: Boolean = false,
    content: @Composable (View) -> Unit,
) {
    val target = rememberOfficialReportTarget(key)
    Box(modifier = modifier) {
        content(target)
        OfficialReportTargetAnchor(
            target = target,
            modifier = Modifier
                .matchParentSize()
                .zIndex(-1f),
            elementId = elementId,
            params = params,
            reuseIdentifier = reuseIdentifier,
            reportImpression = reportImpression,
            reportAllExposures = reportAllExposures,
        )
    }
}
