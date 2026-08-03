package rj.qmce.lite.data.call

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.tencent.aidl.IQavBusinessCallback
import com.tencent.aidl.IQavInterface
import com.tencent.qqnt.qav_component_impl.qav.bussiness.QavBussinessCtrl
import com.tencent.qqnt.qav_component_impl.qav.session.QavC2CSession
import com.tencent.qqnt.watch.contact.api.IContactRuntimeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mqq.app.MobileQQ
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.ui.call.QmceCallActivity

enum class CallMode(val isOnlyAudio: Boolean) {
    Voice(true),
    Video(false),
}

enum class CallPhase {
    Idle,
    Outgoing,
    Incoming,
    Connecting,
    Active,
    Ending,
    Ended,
}

data class CallPeer(
    val uin: String,
    val uid: String,
    val name: String,
)

data class CallUiState(
    val phase: CallPhase = CallPhase.Idle,
    val peer: CallPeer? = null,
    val mode: CallMode = CallMode.Voice,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val hasLocalVideo: Boolean = false,
    val hasRemoteVideo: Boolean = false,
    val channelReady: Boolean = false,
    val connectedAtElapsedRealtime: Long? = null,
    val endMessage: String? = null,
)

sealed interface CallStartResult {
    data object Requested : CallStartResult
    data class Rejected(val message: String) : CallStartResult
    data class Failed(val message: String) : CallStartResult
}

object QmceCallController {
    private const val TAG = "QMCE-Call"

    private data class PendingOutgoing(
        val generation: Long,
        val peer: CallPeer,
        val mode: CallMode,
    )

    private val _state = MutableStateFlow(CallUiState())
    val state = _state.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var qavBinder: IQavInterface? = null

    @Volatile
    private var serviceBindingActive = false

    @Volatile
    private var activeGeneration = 0L

    private var businessCallback: IQavBusinessCallback? = null
    private var pendingOutgoing: PendingOutgoing? = null
    private var pendingAcceptGeneration: Long? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? IQavInterface
            if (binder == null) {
                Log.e(TAG, "QAV service returned an incompatible binder: $service")
                end("通话服务不可用")
                return
            }
            qavBinder = binder
            if (
                _state.value.phase == CallPhase.Ended &&
                pendingOutgoing == null &&
                pendingAcceptGeneration == null
            ) {
                releaseServiceBinding(stopService = false)
                return
            }
            hydrateFromBinder(binder)
            val phase = _state.value.phase
            if (
                (phase == CallPhase.Incoming || phase == CallPhase.Idle) &&
                runCatching { binder.A() }.getOrDefault(true)
            ) {
                end(if (phase == CallPhase.Incoming) "通话邀请已失效" else "通话已结束")
                return
            }
            installBusinessCallback(binder)
            consumePendingActions(binder)
            syncFromBinder(binder)
            Log.d(TAG, "QAV service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            qavBinder = null
            businessCallback = null
            Log.w(TAG, "QAV service disconnected")
            if (_state.value.phase in activePhases || _state.value.phase == CallPhase.Incoming) {
                end("通话服务已断开")
            } else {
                releaseServiceBinding(stopService = false)
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            qavBinder = null
            businessCallback = null
            Log.w(TAG, "QAV service binding died")
            if (_state.value.phase in activePhases || _state.value.phase == CallPhase.Incoming) {
                end("通话服务已断开")
            } else {
                releaseServiceBinding(stopService = false)
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            qavBinder = null
            businessCallback = null
            end("通话服务不可用")
        }
    }

    fun startOutgoing(
        context: Context,
        mode: CallMode,
        peerUid: String,
        peerUin: String,
        peerName: String,
    ): CallStartResult {
        val appContext = context.applicationContext
        applicationContext = appContext
        val runtime = QmceApplication.ensureRuntime()
        if (!hasUsableLoginSession(runtime)) {
            return CallStartResult.Rejected("登录状态不可用")
        }
        val resolvedUin = resolvePeerUin(peerUid, peerUin)
            ?: return CallStartResult.Rejected("无法获取对方 QQ 号")
        val peer = CallPeer(
            uin = resolvedUin,
            uid = peerUid,
            name = peerName.ifBlank { resolvedUin },
        )

        return runCatching {
            ensureEngine()
            val currentPhase = _state.value.phase
            if (currentPhase == CallPhase.Idle || currentPhase == CallPhase.Ended) {
                clearStaleQavSession(_state.value.peer?.uin)
            }
            val existing = QavBussinessCtrl.t().i.b
            if (existing != null) {
                if (!existing.b() && !existing.c()) {
                    return CallStartResult.Rejected("已有通话进行中")
                }
                clearStaleQavSession(
                    _state.value.peer?.uin ?: existing.e.takeIf { it > 0L }?.toString(),
                )
            }
            if (_state.value.phase == CallPhase.Idle || _state.value.phase == CallPhase.Ended) {
                releaseServiceBinding(stopService = false)
            }

            activeGeneration += 1
            pendingAcceptGeneration = null
            pendingOutgoing = PendingOutgoing(activeGeneration, peer, mode)
            _state.value = CallUiState(
                phase = CallPhase.Outgoing,
                peer = peer,
                mode = mode,
                isSpeakerOn = true,
                hasLocalVideo = !mode.isOnlyAudio,
            )

            startCallService(appContext, peer, mode, isReceiver = false)
            QmceCallActivity.open(appContext)
            Log.d(TAG, "outgoing ${mode.name.lowercase()} call queued: $resolvedUin")
            CallStartResult.Requested
        }.getOrElse { error ->
            Log.e(TAG, "start outgoing call failed", error)
            end("发起通话失败：${error.javaClass.simpleName}")
            CallStartResult.Failed("发起通话失败：${error.javaClass.simpleName}")
        }
    }

    fun onServiceStarted(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        applicationContext = appContext
        runCatching { ensureEngine() }
            .onFailure { Log.e(TAG, "initialize call engine failed", it) }

        val peerUin = intent?.getStringExtra(EXTRA_PEER_UIN).orEmpty()
        if (peerUin.isNotBlank()) {
            val current = _state.value
            if (current.phase == CallPhase.Idle || current.phase == CallPhase.Ended) {
                activeGeneration += 1
            }
            val peerUid = intent?.getStringExtra(EXTRA_PEER_UID).orEmpty()
            val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty()
            val isOnlyAudio = when {
                intent == null -> true
                intent.hasExtra(EXTRA_ONLY_AUDIO) ->
                    intent.getBooleanExtra(EXTRA_ONLY_AUDIO, true)

                intent.hasExtra(EXTRA_ACTIVITY_ONLY_AUDIO) ->
                    intent.getBooleanExtra(EXTRA_ACTIVITY_ONLY_AUDIO, true)

                else -> true
            }
            val isReceiver = intent?.getBooleanExtra(EXTRA_IS_RECEIVER, true) ?: true
            _state.value = current.copy(
                phase = if (isReceiver) CallPhase.Incoming else current.phase,
                peer = CallPeer(
                    uin = peerUin,
                    uid = peerUid,
                    name = peerName.ifBlank { peerUin },
                ),
                mode = if (isOnlyAudio) CallMode.Voice else CallMode.Video,
                endMessage = null,
            )
            if (isReceiver) {
                QmceIncomingCallAlert.start(appContext)
            }
        }

        runCatching { bindCallService(appContext) }
            .onFailure {
                Log.e(TAG, "bind call service from activity failed", it)
                end("通话服务不可用")
            }
    }

    fun acceptIncoming() {
        val current = _state.value
        val peer = current.peer ?: return
        QmceIncomingCallAlert.stop()
        val generation = activeGeneration
        val binder = qavBinder
        if (binder == null) {
            pendingAcceptGeneration = generation
            applicationContext?.let { context ->
                runCatching { bindCallService(context) }
                    .onFailure {
                        Log.e(TAG, "bind service for accept failed", it)
                        end("接听失败：${it.javaClass.simpleName}")
                    }
            }
            _state.value = current.copy(phase = CallPhase.Connecting)
            return
        }
        performAccept(binder, generation, peer, current.mode)
    }

    fun silenceIncomingAlert() {
        QmceIncomingCallAlert.stop()
    }

    fun rejectIncoming() {
        val peer = _state.value.peer ?: return
        QmceIncomingCallAlert.stop()
        runCatching {
            qavBinder?.q(peer.uin, REJECT_REASON_DECLINED)
                ?: QavBussinessCtrl.t().v(peer.uin, REJECT_REASON_DECLINED)
        }.onFailure { Log.w(TAG, "reject incoming call failed", it) }
        end("已拒绝")
    }

    fun hangUp() {
        val current = _state.value
        val peer = current.peer ?: return
        if (current.phase == CallPhase.Ending || current.phase == CallPhase.Ended) return
        QmceIncomingCallAlert.stop()
        val generation = activeGeneration
        _state.value = current.copy(phase = CallPhase.Ending)
        runCatching {
            qavBinder?.K(peer.uin, CLOSE_REASON_HANG_UP)
                ?: QavBussinessCtrl.t().q(peer.uin, CLOSE_REASON_HANG_UP)
        }.onFailure { error ->
            Log.w(TAG, "hang up call failed", error)
            end("通话已结束")
        }
        mainHandler.postDelayed({
            if (generation == activeGeneration && _state.value.phase == CallPhase.Ending) {
                clearStaleQavSession(peer.uin)
                end("通话已结束")
            }
        }, HANGUP_FALLBACK_DELAY_MS)
    }

    fun toggleMute() {
        val current = _state.value
        if (current.phase !in activePhases) return
        runCatching {
            val localAudioEnabled = qavBinder?.z()
                ?: error("QAV binder unavailable")
            _state.value = _state.value.copy(isMuted = !localAudioEnabled)
        }.onFailure { Log.w(TAG, "toggle microphone failed", it) }
    }

    fun toggleSpeaker() {
        val current = _state.value
        if (current.phase !in activePhases) return
        val enabled = !current.isSpeakerOn
        runCatching {
            QavBussinessCtrl.t().x(if (enabled) AUDIO_ROUTE_SPEAKER else AUDIO_ROUTE_EARPIECE)
        }
            .onFailure { Log.w(TAG, "switch audio route failed", it) }
        _state.value = current.copy(isSpeakerOn = enabled)
    }

    fun toggleVideo() {
        val current = _state.value
        if (current.mode != CallMode.Video || current.phase !in activePhases) return
        runCatching {
            val binder = qavBinder ?: error("QAV binder unavailable")
            binder.M()
            binder.o()
            updateFromEngine()
        }.onFailure { Log.w(TAG, "toggle local video failed", it) }
    }

    fun switchCamera() {
        if (_state.value.mode != CallMode.Video) return
        runCatching { QavBussinessCtrl.t().k.a() }
            .onFailure { Log.w(TAG, "switch camera failed", it) }
    }

    fun onActivityDestroyed(isFinishing: Boolean, isChangingConfigurations: Boolean) {
        if (!isFinishing || isChangingConfigurations) return
        when (_state.value.phase) {
            CallPhase.Incoming -> rejectIncoming()
            CallPhase.Outgoing, CallPhase.Connecting, CallPhase.Active, CallPhase.Ending -> hangUp()
            CallPhase.Ended -> resetEndedState()
            CallPhase.Idle -> Unit
        }
    }

    fun resetEndedState() {
        if (_state.value.phase == CallPhase.Ended) {
            clearStaleQavSession(_state.value.peer?.uin)
            _state.value = CallUiState()
        }
    }

    fun requiredPermissions(mode: CallMode): Array<String> = if (mode == CallMode.Video) {
        arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.CAMERA,
        )
    } else {
        arrayOf(android.Manifest.permission.RECORD_AUDIO)
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        runCatching { ensureEngine() }
            .onFailure { Log.e(TAG, "initialize QAV engine failed", it) }
    }

    fun currentSelfUin(): String {
        val runtime = QmceApplication.ensureRuntime()
        return listOf(
            runCatching { runtime?.currentAccountUin }.getOrNull(),
            runCatching { runtime?.currentUin }.getOrNull(),
            runCatching { MobileQQ.sMobileQQ?.lastLoginUin }.getOrNull(),
        ).firstOrNull { !it.isNullOrBlank() && it.toLongOrNull()?.let { uin -> uin > 0L } == true }
            .orEmpty()
    }

    fun onChannelReady(peerUin: String? = null) {
        updateFromEngine(
            peerUin = peerUin,
            forcePhase = CallPhase.Active,
            channelReady = true,
        )
    }

    private fun ensureEngine() {
        QavBussinessCtrl.t()
    }

    private fun installBusinessCallback(binder: IQavInterface) {
        businessCallback?.let { oldCallback ->
            runCatching { binder.E(oldCallback) }
        }
        val generation = activeGeneration
        val callback = object : IQavBusinessCallback.Stub() {
            override fun m(fromUin: String?) {
                runOnMain {
                    if (isCurrentCallback(generation, fromUin)) {
                        onChannelReady(fromUin)
                    }
                }
            }

            override fun Q(localHasAudio: Boolean) {
                runOnMain {
                    if (generation == activeGeneration) {
                        _state.value = _state.value.copy(isMuted = !localHasAudio)
                    }
                }
            }

            override fun x(localHasVideo: Boolean, remoteHasVideo: Boolean) {
                runOnMain {
                    if (generation == activeGeneration) {
                        _state.value = _state.value.copy(
                            hasLocalVideo = localHasVideo,
                            hasRemoteVideo = remoteHasVideo,
                        )
                    }
                }
            }

            override fun d(time: String?) = Unit

            override fun e(fromUin: String?, reason: Int) {
                runOnMain {
                    if (isCurrentCallback(generation, fromUin)) {
                        finishFromEngine(fromUin.orEmpty(), reason)
                    }
                }
            }
        }
        businessCallback = callback
        runCatching {
            binder.H(callback)
            binder.o()
        }.onFailure { Log.e(TAG, "register QAV business callback failed", it) }
    }

    private fun consumePendingActions(binder: IQavInterface) {
        val outgoing = pendingOutgoing
        if (outgoing != null && outgoing.generation == activeGeneration) {
            pendingOutgoing = null
            runCatching {
                binder.f(outgoing.peer.uin, outgoing.mode.isOnlyAudio)
                binder.o()
                updateFromEngine(forcePhase = CallPhase.Outgoing)
                Log.d(TAG, "official outgoing call started: ${outgoing.peer.uin}")
            }.onFailure {
                Log.e(TAG, "official outgoing call start failed", it)
                end("发起通话失败：${it.javaClass.simpleName}")
            }
        }

        val acceptGeneration = pendingAcceptGeneration
        if (acceptGeneration != null && acceptGeneration == activeGeneration) {
            pendingAcceptGeneration = null
            val current = _state.value
            current.peer?.let { peer ->
                performAccept(binder, acceptGeneration, peer, current.mode)
            }
        }
    }

    private fun performAccept(
        binder: IQavInterface,
        generation: Long,
        peer: CallPeer,
        mode: CallMode,
    ) {
        if (generation != activeGeneration) return
        runCatching {
            binder.F(peer.uin, mode.isOnlyAudio)
            binder.o()
            _state.value = _state.value.copy(
                phase = CallPhase.Connecting,
                channelReady = false,
            )
        }.onFailure { error ->
            Log.e(TAG, "accept incoming call failed", error)
            end("接听失败：${error.javaClass.simpleName}")
        }
    }

    private fun hydrateFromBinder(binder: IQavInterface) {
        val outgoing = pendingOutgoing
        if (outgoing != null && outgoing.generation == activeGeneration) return
        val info = runCatching { binder.p() }.getOrNull() ?: return
        val peerUin = info.peerUin.orEmpty()
        if (peerUin.isBlank()) return

        val current = _state.value
        if (current.phase == CallPhase.Idle && current.peer == null) {
            activeGeneration += 1
        }
        val currentPeer = current.peer
        val peer = CallPeer(
            uin = peerUin,
            uid = info.peerUid.orEmpty().ifBlank { currentPeer?.uid.orEmpty() },
            name = info.peerNickname.orEmpty().ifBlank { currentPeer?.name ?: peerUin },
        )
        _state.value = current.copy(
            peer = peer,
            mode = if (info.isOnlyAudio) CallMode.Voice else CallMode.Video,
            endMessage = null,
        )
    }

    private fun syncFromBinder(binder: IQavInterface) {
        runCatching {
            binder.o()
            val connected = binder.B()
            updateFromEngine(
                forcePhase = if (connected) CallPhase.Active else null,
                channelReady = if (connected) true else null,
            )
        }.onFailure { Log.w(TAG, "sync QAV binder state failed", it) }
    }

    private fun updateFromEngine(
        peerUin: String? = null,
        forcePhase: CallPhase? = null,
        remoteVideo: Boolean? = null,
        channelReady: Boolean? = null,
    ) {
        val session = runCatching { QavBussinessCtrl.t().i.b }.getOrNull() ?: return
        val current = _state.value
        if (current.phase == CallPhase.Ended) return
        val sessionUin = session.e.takeIf { it > 0L }?.toString()
            ?: peerUin?.takeIf { it.isNotBlank() }
            ?: current.peer?.uin.orEmpty()
        if (sessionUin.isBlank()) return
        if (peerUin != null && sessionUin.isNotBlank() && sessionUin != peerUin) return
        val peer = current.peer?.takeIf { it.uin == sessionUin } ?: CallPeer(
            uin = sessionUin,
            uid = "",
            name = sessionUin,
        )
        val phase = forcePhase ?: when (session.c) {
            QavC2CSession.SessionStatus.c -> CallPhase.Outgoing
            QavC2CSession.SessionStatus.d -> CallPhase.Incoming
            QavC2CSession.SessionStatus.e -> CallPhase.Connecting
            QavC2CSession.SessionStatus.f -> CallPhase.Active
            QavC2CSession.SessionStatus.h -> CallPhase.Ending
            QavC2CSession.SessionStatus.i -> CallPhase.Ended
            else -> current.phase
        }
        _state.value = current.copy(
            phase = phase,
            peer = peer,
            mode = current.mode,
            isMuted = session.j,
            hasLocalVideo = session.k,
            hasRemoteVideo = remoteVideo ?: session.l,
            channelReady = channelReady ?: current.channelReady,
            connectedAtElapsedRealtime = if (phase == CallPhase.Active) {
                current.connectedAtElapsedRealtime ?: SystemClock.elapsedRealtime()
            } else {
                current.connectedAtElapsedRealtime
            },
            endMessage = if (phase == CallPhase.Ended) current.endMessage ?: "通话已结束" else null,
        )
    }

    private fun finishFromEngine(peerUin: String, reason: Int) {
        val current = _state.value
        if (peerUin.isNotBlank() && current.peer?.uin != null && current.peer.uin != peerUin) return
        end(reasonText(reason))
    }

    private fun end(message: String) {
        QmceIncomingCallAlert.stop()
        val peerUin = _state.value.peer?.uin
        activeGeneration += 1
        val generation = activeGeneration
        pendingOutgoing = null
        pendingAcceptGeneration = null
        _state.value = _state.value.copy(
            phase = CallPhase.Ended,
            channelReady = false,
            endMessage = message,
        )
        mainHandler.post {
            if (generation == activeGeneration) {
                clearStaleQavSession(peerUin)
                releaseServiceBinding(stopService = false)
            }
        }
        mainHandler.postDelayed({
            if (
                generation == activeGeneration &&
                pendingOutgoing == null &&
                pendingAcceptGeneration == null &&
                _state.value.phase !in activePhases &&
                _state.value.phase != CallPhase.Incoming
            ) {
                stopCallService()
            }
        }, SERVICE_STOP_FALLBACK_DELAY_MS)
    }

    private fun isCurrentCallback(generation: Long, peerUin: String?): Boolean {
        if (generation != activeGeneration) return false
        val activePeer = _state.value.peer?.uin
        return peerUin.isNullOrBlank() || activePeer.isNullOrBlank() || activePeer == peerUin
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun resolvePeerUin(peerUid: String, fallbackUin: String): String? {
        val runtime = QmceApplication.ensureRuntime()
        val runtimeUin = runCatching {
            runtime
                ?.getRuntimeService(IContactRuntimeService::class.java, "")
                ?.getUinByUid(peerUid)
        }.getOrNull()
        if (runtimeUin != null && runtimeUin > 0L) {
            return runtimeUin.toString()
        }

        val profileUin = runCatching {
            KernelBridge.getKernelService()
                ?.getProfileService()
                ?.getUinByUid(TAG, arrayListOf(peerUid))
                ?.get(peerUid)
        }.getOrNull()
        if (profileUin != null && profileUin > 0L) {
            return profileUin.toString()
        }

        return fallbackUin.takeIf { it.toLongOrNull()?.let { uin -> uin > 0L } == true }
    }

    private fun hasUsableLoginSession(runtime: mqq.app.AppRuntime?): Boolean {
        if (runtime == null) return false

        val runtimeLoggedIn = runCatching { runtime.isLogin() }.getOrDefault(false)
        if (runtimeLoggedIn) return true

        val runtimeUin = runCatching { runtime.currentUin }.getOrNull()
        val mobileUin = runCatching { MobileQQ.sMobileQQ?.lastLoginUin }.getOrNull()
        val hasAccountUin = listOf(runtimeUin, mobileUin).any { uin ->
            uin?.toLongOrNull()?.let { it > 0L } == true
        }
        val kernelReady = KernelBridge.getKernelService() != null
        val usable = hasAccountUin && kernelReady
        Log.w(
            TAG,
            "runtime isLogin=false; runtimeUin=$runtimeUin, mobileUin=$mobileUin, " +
                    "kernelReady=$kernelReady, usable=$usable",
        )
        return usable
    }

    private fun startCallService(
        context: Context,
        peer: CallPeer,
        mode: CallMode,
        isReceiver: Boolean,
    ) {
        context.startService(
            Intent(context, com.tencent.service.QavManageService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PEER_UIN, peer.uin)
                putExtra(EXTRA_PEER_UID, peer.uid)
                putExtra(EXTRA_PEER_NAME, peer.name)
                putExtra(EXTRA_ONLY_AUDIO, mode.isOnlyAudio)
                putExtra(EXTRA_IS_RECEIVER, isReceiver)
            },
        )
    }

    private fun bindCallService(context: Context) {
        if (serviceBindingActive) return
        val bound = context.bindService(
            Intent(context, com.tencent.service.QavManageService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
        if (!bound) error("bindService returned false")
        serviceBindingActive = true
    }

    private fun releaseServiceBinding(stopService: Boolean) {
        val context = applicationContext
        val binder = qavBinder
        val callback = businessCallback
        qavBinder = null
        businessCallback = null
        if (binder != null && callback != null) {
            runCatching { binder.E(callback) }
                .onFailure { Log.w(TAG, "unregister QAV business callback failed", it) }
        }
        if (serviceBindingActive) {
            serviceBindingActive = false
            if (context != null) {
                runCatching { context.unbindService(serviceConnection) }
                    .onFailure { Log.w(TAG, "unbind QAV service failed", it) }
            }
        }
        if (stopService) {
            stopCallService()
        }
    }

    private fun clearStaleQavSession(peerUin: String?) {
        runCatching {
            val ctrl = QavBussinessCtrl.t()
            val sessionManager = ctrl.i
            val existing = sessionManager.b
                ?: peerUin?.takeIf { it.isNotBlank() }?.let { sessionManager.b(it) }
            if (existing == null) return@runCatching

            val resolvedPeerUin = peerUin?.takeIf { it.isNotBlank() }
                ?: existing.e.takeIf { it > 0L }?.toString()
            if (resolvedPeerUin.isNullOrBlank()) return@runCatching

            val phase = _state.value.phase
            val sessionEnded = existing.b() || existing.c()
            if (
                !sessionEnded &&
                phase !in activePhases &&
                phase != CallPhase.Incoming &&
                phase != CallPhase.Ending
            ) {
                runCatching {
                    qavBinder?.K(resolvedPeerUin, CLOSE_REASON_HANG_UP)
                        ?: ctrl.q(resolvedPeerUin, CLOSE_REASON_HANG_UP)
                }.onFailure { Log.w(TAG, "force hang up stale QAV session failed", it) }
            }
            runCatching { ctrl.r(resolvedPeerUin) }
                .onFailure { Log.w(TAG, "close stale QAV session failed", it) }
        }.onFailure { Log.w(TAG, "clear stale QAV session failed", it) }
    }

    private fun stopCallService() {
        val context = applicationContext ?: return
        runCatching {
            context.stopService(Intent(context, com.tencent.service.QavManageService::class.java))
        }.onFailure { Log.w(TAG, "stop QAV service failed", it) }
    }

    private fun reasonText(reason: Int): String = when (reason) {
        REJECT_REASON_DECLINED -> "对方已拒绝"
        CLOSE_REASON_HANG_UP -> "通话已结束"
        else -> "通话已结束"
    }

    private val activePhases = setOf(CallPhase.Outgoing, CallPhase.Connecting, CallPhase.Active)

    const val ACTION_START = "rj.qmce.lite.call.START"
    const val EXTRA_PEER_UIN = "key_peer_uin"
    const val EXTRA_PEER_UID = "key_peer_uid"
    const val EXTRA_PEER_NAME = "key_peer_nick"
    const val EXTRA_ONLY_AUDIO = "key_is_only_audio"
    const val EXTRA_IS_RECEIVER = "key_is_receiver"

    private const val EXTRA_ACTIVITY_ONLY_AUDIO = "key_only_audio"
    private const val REJECT_REASON_DECLINED = 1
    private const val CLOSE_REASON_HANG_UP = 0
    private const val AUDIO_ROUTE_EARPIECE = 0
    private const val AUDIO_ROUTE_SPEAKER = 1
    private const val HANGUP_FALLBACK_DELAY_MS = 1_500L
    private const val SERVICE_STOP_FALLBACK_DELAY_MS = 2_000L
}
