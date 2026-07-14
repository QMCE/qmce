package rj.qmce.lite.fix

import android.app.Application
import android.util.Log
import com.tencent.mobileqq.app.guard.GuardManager
import mqq.app.MobileQQ

class ForegroundGuardManager(application: Application, mode: Int) : GuardManager(application, mode) {
    override fun f(): Boolean = true
}

object ForegroundGuardInstaller {
    private const val TAG = "QMCE"

    @Volatile
    private var installed: ForegroundGuardManager? = null

    @Synchronized
    fun install(stage: String): Boolean {
        val current = GuardManager.c
        if (current is ForegroundGuardManager) {
            installed = current
            Log.d(TAG, "guard: $stage already installed id=${System.identityHashCode(current)}")
            return true
        }
        if (current != null && current === installed) {
            Log.d(TAG, "guard: $stage retained id=${System.identityHashCode(current)}")
            return true
        }

        val application = current?.m ?: MobileQQ.sMobileQQ
        if (application == null) {
            Log.w(TAG, "guard: $stage skipped; application is null")
            return false
        }
        val replacement = runCatching { ForegroundGuardManager(application, 0) }
            .onFailure { error -> Log.w(TAG, "guard: $stage construction rejected", error) }
            .getOrNull() ?: return false
        GuardManager.c = replacement
        installed = replacement
        Log.i(
            TAG,
            "guard: $stage ${if (current == null) "created" else "replaced"} " +
                "${current?.let(System::identityHashCode)} -> ${System.identityHashCode(replacement)}, " +
                "foreground=${replacement.f()}"
        )
        return true
    }
}
