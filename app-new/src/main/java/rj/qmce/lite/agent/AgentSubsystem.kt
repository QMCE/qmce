package rj.qmce.lite.agent

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var currentUin: String? = null

    private val _active = MutableStateFlow(false)
    /** True when the subsystem is enabled and ready (logged in). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** True when the subsystem is enabled and the user is logged in. */
    val isActive: Boolean get() = initialized && enabled

    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            AgentToolRegistrar.ensure(context.applicationContext)
            initialized = true
            QmceLog.d(TAG, "subsystem ensured")
        }
    }

    /** Called from QmceNotifyLifecycle.onLoggedIn. */
    fun onLoggedIn(context: Context) {
        appContext = context.applicationContext
        val enabled = isEnabled(context)
        this.enabled = enabled
        val uin = runCatching {
            rj.qmce.lite.QmceApplication.ensureRuntime()?.currentUin.orEmpty()
        }.getOrDefault("").ifBlank { "default" }
        currentUin = uin
        _active.value = initialized && enabled
        if (!enabled) return
        AgentToolRegistrar.ensure(context.applicationContext)
        AgentEventBus.ensure()
        AgentSessionStore.load(context.applicationContext, uin)
        QmceLog.d(TAG, "logged in, agent enabled uin=$uin")
    }

    /** Called from QmceNotifyLifecycle.onLoggedOut. */
    fun onLoggedOut() {
        val ctx = appContext
        val uin = currentUin
        AgentEngine.cancel()
        AgentEventBus.stop()
        AgentTimer.clearAll()
        ApprovalController.cancelAll()
        if (ctx != null && uin != null) {
            AgentSessionStore.clear(ctx, uin)
        }
        AgentSession.reset()
        AgentSessionStore.cancelPending()
        rj.qmce.lite.agent.predict.MessagePredictionController.reset()
        currentUin = null
        _active.value = false
        QmceLog.d(TAG, "logged out, agent reset")
    }

    /** Apply enable toggle immediately (settings UI). */
    fun setEnabled(context: Context, enabled: Boolean) {
        appContext = context.applicationContext
        this.enabled = enabled
        _active.value = initialized && enabled
        if (enabled) {
            AgentToolRegistrar.ensure(context.applicationContext)
            AgentEventBus.ensure()
            val uin = currentUin ?: runCatching {
                rj.qmce.lite.QmceApplication.ensureRuntime()?.currentUin.orEmpty()
            }.getOrDefault("").ifBlank { null }
            if (uin != null) {
                currentUin = uin
                if (AgentSession.history.value.isEmpty() && AgentSession.uiMessages.value.isEmpty()) {
                    AgentSessionStore.load(context.applicationContext, uin)
                }
            }
            QmceLog.d(TAG, "agent enabled live")
        } else {
            AgentEngine.cancel()
            AgentEventBus.stop()
            AgentTimer.clearAll()
            ApprovalController.cancelAll()
            QmceLog.d(TAG, "agent disabled live")
        }
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

    fun persistenceUin(): String? = currentUin

    fun persistenceContext(): Context? = appContext
}
