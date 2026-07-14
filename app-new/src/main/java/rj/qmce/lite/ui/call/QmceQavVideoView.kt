package rj.qmce.lite.ui.call

import android.content.Context
import android.util.SparseArray
import com.tencent.av.opengl.ui.GLVideoView
import com.tencent.qav.QavSDK
import com.tencent.qav.controller.QavCtrl
import com.tencent.qav.controller.c2c.C2COperatorImpl
import com.tencent.qav.controller.c2c.IC2COperator

internal class QmceQavVideoView(
    context: Context,
) : GLVideoView(context, renderEndPointer()) {
    private var renderKey = ""
    private var localTarget = false

    fun bind(targetUin: String, selfUin: String, role: Int) {
        G = selfUin
        if (g == null) {
            g = SparseArray()
        }
        g.put(0, targetUin)
        g.put(1, role)
        renderKey = QavCtrl.a(targetUin, role)
        localTarget = targetUin == selfUin
    }

    fun open(previewOnly: Boolean) {
        runCatching {
            val currentOperator = currentOperator() ?: return@runCatching
            if (localTarget) {
                currentOperator.p(true, this)
                if (!previewOnly) {
                    currentOperator.f(true)
                }
            } else {
                currentOperator.r(renderKey, this)
            }
        }
    }

    fun close() {
        runCatching {
            val currentOperator = currentOperator() ?: return@runCatching
            if (localTarget) {
                currentOperator.q()
                currentOperator.f(false)
            } else if (renderKey.isNotBlank()) {
                currentOperator.e(renderKey)
            }
        }
    }

    fun setMirrorEnabled(enabled: Boolean) {
        s(enabled)
    }

    private fun currentOperator(): IC2COperator? =
        runCatching { QavSDK.c().b() }.getOrNull()

    private companion object {
        fun renderEndPointer(): Int = runCatching {
            val operator = QavSDK.c().b() as? C2COperatorImpl
            operator?.c?.getOnPeerFrameRenderEndFunctionPtr()?.toInt() ?: 0
        }.getOrDefault(0)
    }
}
