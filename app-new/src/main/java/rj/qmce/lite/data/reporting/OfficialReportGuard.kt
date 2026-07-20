package rj.qmce.lite.data.reporting

import android.app.Application
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

internal object OfficialReportGuard {
    private val started = AtomicBoolean(false)

    fun begin(application: Application): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) return false
        return started.compareAndSet(false, true)
    }
}
