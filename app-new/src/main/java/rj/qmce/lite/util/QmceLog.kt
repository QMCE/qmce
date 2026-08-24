package rj.qmce.lite.util

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import rj.qmce.lite.BuildConfig

/**
 * v/d/i gated by [verboseEnabled] (prefs; Debug default on, Release default off).
 * w/e/[important] always emitted.
 */
object QmceLog {
    private val verbose = AtomicBoolean(BuildConfig.DEBUG)

    fun setVerboseEnabled(enabled: Boolean) {
        verbose.set(enabled)
    }

    fun isVerboseEnabled(): Boolean = verbose.get()

    fun v(tag: String, msg: String, tr: Throwable? = null) {
        if (!verbose.get()) return
        if (tr != null) Log.v(tag, msg, tr) else Log.v(tag, msg)
    }

    fun d(tag: String, msg: String, tr: Throwable? = null) {
        if (!verbose.get()) return
        if (tr != null) Log.d(tag, msg, tr) else Log.d(tag, msg)
    }

    fun i(tag: String, msg: String, tr: Throwable? = null) {
        if (!verbose.get()) return
        if (tr != null) Log.i(tag, msg, tr) else Log.i(tag, msg)
    }

    /** Always emitted (maps to Log.w). Use for login/OTA/logout critical paths in Release. */
    fun important(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
    }
}
