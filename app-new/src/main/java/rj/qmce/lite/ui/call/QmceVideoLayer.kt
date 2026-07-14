package rj.qmce.lite.ui.call

import android.content.Context
import com.tencent.av.camera.CameraObserver
import com.tencent.av.camera.CameraUtils
import com.tencent.av.opengl.ui.GLViewGroup
import com.tencent.qav.QavSDK
import com.tencent.qav.observer.ObserverDispatcher

/**
 * 自有双画面 GL 布局。
 *
 * QAV 仅提供底层的 GL 视频 View；远端全屏与本地预览的位置、层级和生命周期全部由 QMCE 管理。
 */
internal class QmceVideoLayer(
    private val viewContext: Context,
) : GLViewGroup(viewContext) {
    private val localPreview = QmceQavVideoView(viewContext)
    private val remotePreview = QmceQavVideoView(viewContext)
    private var localAttached = false
    private var remoteAttached = false
    private var channelReady = false
    private val cameraObserver = object : CameraObserver() {
        override fun c(succeeded: Boolean) {
            if (succeeded && localAttached) {
                updateLocalMirror()
            }
        }
    }

    init {
        s(localPreview)
        s(remotePreview)
        localPreview.r(1)
        remotePreview.r(1)
        layoutViews(remotePreview, localPreview)
        QavSDK.c().a(cameraObserver)
    }

    fun bind(selfUin: String, peerUin: String) {
        localPreview.bind(selfUin, selfUin, 1)
        remotePreview.bind(peerUin, selfUin, 1)
    }

    fun updateMediaState(
        localHasVideo: Boolean,
        remoteHasVideo: Boolean,
        channelReady: Boolean,
    ) {
        val previousChannelReady = this.channelReady
        this.channelReady = channelReady

        localPreview.t(localHasVideo)
        if (localHasVideo) {
            if (!localAttached || previousChannelReady != channelReady) {
                localPreview.open(!channelReady)
                localAttached = true
            }
            localPreview.r(0)
            updateLocalMirror()
        } else {
            if (localAttached) {
                localPreview.close()
                localAttached = false
            }
            localPreview.r(1)
            localPreview.setMirrorEnabled(false)
        }

        remotePreview.t(remoteHasVideo)
        if (remoteHasVideo) {
            if (!remoteAttached) {
                remotePreview.open(false)
                remoteAttached = true
            }
            remotePreview.r(0)
        } else {
            if (remoteAttached) {
                remotePreview.close()
                remoteAttached = false
            }
            remotePreview.r(1)
        }

        when {
            remoteHasVideo -> layoutViews(localPreview, remotePreview)
            localHasVideo -> layoutViews(remotePreview, localPreview)
            else -> layoutViews(remotePreview, localPreview)
        }
    }

    fun release() {
        runCatching {
            val dispatcher = ObserverDispatcher.b()
            synchronized(dispatcher) {
                dispatcher.a.remove(cameraObserver)
                dispatcher.b.remove(cameraObserver)
            }
        }
        if (localAttached) {
            localPreview.close()
            localAttached = false
        }
        if (remoteAttached) {
            remotePreview.close()
            remoteAttached = false
        }
    }

    private fun layoutViews(smallView: QmceQavVideoView, fullView: QmceQavVideoView) {
        val metrics = viewContext.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val inset = dp(8)
        val previewSize = dp(72)

        smallView.g(
            width - previewSize - inset,
            inset,
            width - inset,
            previewSize + inset,
        )
        fullView.g(0, 0, width, height)
        setZOrder(smallView, 1)
        setZOrder(fullView, 0)
    }

    private fun setZOrder(view: QmceQavVideoView, zOrder: Int) {
        val oldZOrder = view.h
        if (oldZOrder == zOrder) return
        view.h = zOrder
        view.j?.a(view, zOrder, oldZOrder)
    }

    private fun updateLocalMirror() {
        localPreview.setMirrorEnabled(
            runCatching { CameraUtils.b(viewContext).c() }.getOrDefault(false),
        )
    }

    private fun dp(value: Int): Int =
        (value * viewContext.resources.displayMetrics.density).toInt()
}
