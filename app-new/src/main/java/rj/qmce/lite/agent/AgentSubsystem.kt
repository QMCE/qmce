package rj.qmce.lite.agent

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import rj.qmce.lite.util.QmceLog
import rj.qmce.lite.viewmodel.SettingsViewModel

/**
 * App-scoped Agent subsystem (mirrors OtaUpdateSession).
 * Owns the engine scope and ties lifecycle (login/logout) to session state.
 */
object AgentSubsystem {

    private const val TAG = "QMCE-Agent"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var initialized = false

    @Volatile
    private var enabled = true

    /** True when the subsystem is enabled and the user is logged in. */
    val isActive: Boolean get() = initialized && enabled

    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            AgentToolRegistrar.ensure()
            initialized = true
            QmceLog.d(TAG, "subsystem ensured")
        }
    }

    /** Called from QmceNotifyLifecycle.onLoggedIn. */
    fun onLoggedIn(context: Context) {
        val enabled = isEnabled(context)
        this.enabled = enabled
        if (!enabled) return
        AgentEventBus.ensure()
        QmceLog.d(TAG, "logged in, agent enabled")
    }

    /** Called from QmceNotifyLifecycle.onLoggedOut. */
    fun onLoggedOut() {
        AgentEngine.cancel()
        AgentEventBus.stop()
        AgentTimer.clearAll()
        ApprovalController.cancelAll()
        AgentSession.reset()
        rj.qmce.lite.agent.predict.MessagePredictionController.reset()
        QmceLog.d(TAG, "logged out, agent reset")
    }

    fun isEnabled(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(SettingsViewModel.KEY_AGENT_ENABLED, true)
    }

    fun sendUserMessage(text: String) {
        if (!enabled) return
        // A new user message supersedes any in-flight run (monitor/timer waiting).
        AgentEngine.cancel()
        AgentSession.addUserMessage(text)
        AgentEngine.start(scope)
    }

    fun scope(): CoroutineScope = scope
}
