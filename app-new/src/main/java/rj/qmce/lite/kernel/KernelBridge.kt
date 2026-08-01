package rj.qmce.lite.kernel

import rj.qmce.lite.util.QmceLog
import com.tencent.mobileqq.app.guard.GuardManager
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qphone.base.remote.SimpleAccount
import com.tencent.qqnt.kernel.api.IKernelCreateListener
import com.tencent.qqnt.kernel.api.IGroupService
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession
import com.tencent.qqnt.kernel.nativeinterface.MsfChangeReasonType
import com.tencent.qqnt.kernel.nativeinterface.MsfStatusType
import com.tencent.qqnt.msg.api.IMsgPushForegroundApi
import com.tencent.qqnt.watch.contact.api.IContactRuntimeService
import com.tencent.qqnt.watch.mainframe.api.IMsfConnHelper
import com.tencent.qqnt.watch.selftab.api.ISelfProfileRuntimeService
import mqq.app.AppRuntime
import mqq.app.Foreground
import mqq.app.MobileQQ
import rj.qmce.lite.QmceApplication
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "QMCE"

object KernelBridge {
    enum class CoreServicesPhase { Idle, Starting, Ready, Failed }

    data class CoreServicesStatus(
        val phase: CoreServicesPhase = CoreServicesPhase.Idle,
        val reason: String? = null,
        val attempt: Int = 0,
        val elapsedMillis: Long = 0L,
    )

    private val coreInitLock = Any()
    private var activeCoreInit: FutureTask<Boolean>? = null
    private var coreInitAttempt = 0
    private val _coreServicesStatus = MutableStateFlow(CoreServicesStatus())
    val coreServicesStatus: StateFlow<CoreServicesStatus> = _coreServicesStatus.asStateFlow()

    @Volatile
    private var foregroundCallbackRegistered = false

    // Some cold-start paths trigger awaitCoreServices()/startKernelSession without
    // first running bindLoggedInAccount(); keep these preconditions explicit.
    @Volatile
    private var nativeKernelLoaded = false

    @Volatile
    private var nativePatchEligible = false

    @Volatile
    private var accountModuleInjected = false

    @Volatile
    private var initialModuleInjected = false

    // 全局服务缓存
    @Volatile
    private var cachedKs: IKernelService? = null
    @Volatile
    private var cachedMsgService: com.tencent.qqnt.kernel.api.IMsgService? = null
    @Volatile
    private var cachedRecentService: com.tencent.qqnt.kernel.api.IRecentContactService? = null
    @Volatile
    private var cachedBuddyService: com.tencent.qqnt.kernel.api.IBuddyService? = null
    @Volatile
    private var cachedGroupService: com.tencent.qqnt.kernel.api.IGroupService? = null
    @Volatile
    private var officialMessageKernel: com.tencent.qqnt.kernel.api.IMsgService? = null
    @Volatile
    private var officialMessageService: com.tencent.qqnt.msg.api.IMsgService? = null
    private val officialMessageLock = Any()

    fun getKernelService(): IKernelService? = cachedKs
    fun getMsgService(): com.tencent.qqnt.kernel.api.IMsgService? = cachedMsgService
    fun getKernelMsgService(): IKernelMsgService? = runCatching {
        val kernelService = cachedKs ?: return@runCatching null
        val wrapperSession = kernelService.javaClass
            .getDeclaredField("wrapperSession")
            .apply { isAccessible = true }
            .get(kernelService) as? IQQNTWrapperSession
        wrapperSession?.getMsgService()
    }.getOrNull()
    fun getRecentContactService(): com.tencent.qqnt.kernel.api.IRecentContactService? =
        cachedRecentService

    fun getBuddyService(): com.tencent.qqnt.kernel.api.IBuddyService? = cachedBuddyService
    fun getGroupService(): com.tencent.qqnt.kernel.api.IGroupService? = cachedGroupService
    fun ensureOfficialMessageBridge(
        runtimeOverride: AppRuntime? = null,
    ): com.tencent.qqnt.msg.api.IMsgService? {
        val runtime = runtimeOverride ?: QmceApplication.ensureRuntime()
        val kernelService = cachedKs ?: runCatching {
            runtime?.getRuntimeService(IKernelService::class.java, "")
        }.getOrNull()
        val kernelMsgService = cachedMsgService ?: runCatching {
            kernelService?.getMsgService()
        }.getOrNull() ?: return null

        officialMessageService?.let { cached ->
            if (officialMessageKernel === kernelMsgService) return cached
        }

        return synchronized(officialMessageLock) {
            officialMessageService?.let { cached ->
                if (officialMessageKernel === kernelMsgService) return@synchronized cached
            }
            runCatching {
                val messageBridge = QRoute.api(com.tencent.qqnt.msg.api.IMsgService::class.java)
                messageBridge.init(kernelMsgService)
                officialMessageKernel = kernelMsgService
                officialMessageService = messageBridge
                QmceLog.d(TAG, "KernelBridge: official message bridge initialized service=$messageBridge")
                messageBridge
            }.onFailure { error ->
                QmceLog.w(TAG, "KernelBridge: official message bridge initialization failed", error)
            }.getOrNull()
        }
    }
    fun getSelfProfileService(): ISelfProfileRuntimeService? = runCatching {
        QmceApplication.ensureRuntime()
            ?.getRuntimeService(ISelfProfileRuntimeService::class.java, "")
    }.getOrNull()

    fun awaitCoreServices(
        timeoutMillis: Long = 30_000,
        runtimeOverride: AppRuntime? = null,
    ): Boolean {
        val task = synchronized(coreInitLock) {
            if (areCoreServicesReady()) {
                publishCoreStatus(CoreServicesPhase.Ready)
                return true
            }
            if (_coreServicesStatus.value.phase == CoreServicesPhase.Failed) return false
            activeCoreInit?.takeIf { !it.isDone } ?: FutureTask {
                awaitCoreServicesInternal(timeoutMillis, runtimeOverride)
            }.also { created ->
                activeCoreInit = created
                Thread(created, "QMCE-CoreInit").apply { isDaemon = true }.start()
            }
        }
        return runCatching {
            task.get(timeoutMillis + 1_000L, TimeUnit.MILLISECONDS)
        }.getOrElse { error ->
            QmceLog.w(TAG, "KernelBridge: wait for single-flight init failed", error)
            false
        }
    }

    /** Starts one new initialization cycle after an explicit user retry. */
    fun retryCoreServices(
        timeoutMillis: Long = 30_000,
        runtimeOverride: AppRuntime? = null,
    ): Boolean {
        synchronized(coreInitLock) {
            activeCoreInit?.cancel(true)
            lastForcedStartKs = null
            activeCoreInit = null
            _coreServicesStatus.value = CoreServicesStatus(CoreServicesPhase.Idle)
        }
        return awaitCoreServices(timeoutMillis, runtimeOverride)
    }

    /** Clear service caches after logout so the next login can await again. */
    fun resetAfterLogout() {
        synchronized(coreInitLock) {
            activeCoreInit?.cancel(true)
            activeCoreInit = null
            lastForcedStartKs = null
            coreInitAttempt = 0
            _coreServicesStatus.value = CoreServicesStatus(CoreServicesPhase.Idle)
        }
        cachedKs = null
        cachedMsgService = null
        cachedRecentService = null
        cachedBuddyService = null
        cachedGroupService = null
        officialMessageKernel = null
        officialMessageService = null
        foregroundReplayedSession = null
        QmceLog.d(TAG, "KernelBridge: resetAfterLogout cleared service caches")
    }

    private fun awaitCoreServicesInternal(
        timeoutMillis: Long,
        runtimeOverride: AppRuntime?,
    ): Boolean {
        val myTask = synchronized(coreInitLock) { activeCoreInit }
        val startedAt = System.currentTimeMillis()
        val attempt = synchronized(coreInitLock) { ++coreInitAttempt }
        publishCoreStatus(CoreServicesPhase.Starting, attempt = attempt)
        QmceLog.i(
            TAG,
            "KernelBridge: core init start attempt=$attempt pid=${android.os.Process.myPid()} " +
                "runtime=${runtimeOverride ?: QmceApplication.sAppRuntime} nativeLoaded=$nativeKernelLoaded " +
                "accountModuleInjected=$accountModuleInjected",
        )
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (Thread.interrupted() || synchronized(coreInitLock) { activeCoreInit !== myTask }) {
                QmceLog.i(TAG, "KernelBridge: core init interrupted/superseded attempt=$attempt")
                return false
            }
            val runtime = runtimeOverride ?: QmceApplication.ensureRuntime()
            val kernelService = cachedKs ?: runCatching {
                runtime?.getRuntimeService(IKernelService::class.java, "")
            }.getOrNull()
            if (kernelService != null) {
                if (readWrapperSession(kernelService) == null &&
                    lastForcedStartKs !== kernelService
                ) {
                    QmceLog.w(
                        TAG,
                        "KernelBridge: awaitCoreServices sees wrapperSession=null; forcing start",
                    )
                    patchServiceContent(kernelService, runtime)
                    startKernelSession(kernelService, runtime)
                    lastForcedStartKs = kernelService
                }
                cacheServices(kernelService)
                if (areCoreServicesReady()) {
                    publishCoreStatus(
                        CoreServicesPhase.Ready,
                        attempt = attempt,
                        elapsedMillis = System.currentTimeMillis() - startedAt,
                    )
                    QmceLog.i(TAG, "KernelBridge: core services ready state=${kernelState(kernelService)}")
                    return true
                }
            }
            try {
                Thread.sleep(250)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                QmceLog.i(TAG, "KernelBridge: core init sleep interrupted attempt=$attempt")
                return false
            }
        }
        if (Thread.interrupted() || synchronized(coreInitLock) { activeCoreInit !== myTask }) {
            QmceLog.i(TAG, "KernelBridge: core init superseded after timeout attempt=$attempt")
            return false
        }
        val ks = cachedKs
        // 允许后续重试再次 force start
        publishCoreStatus(
            CoreServicesPhase.Failed,
            reason = "core-services-unavailable",
            attempt = attempt,
            elapsedMillis = System.currentTimeMillis() - startedAt,
        )
        QmceLog.w(
            TAG,
            "KernelBridge: timed out waiting for core services; " +
                    "runtime=${runtimeOverride ?: QmceApplication.sAppRuntime}, " +
                    "ks=$ks, msg=$cachedMsgService, recent=$cachedRecentService, " +
                    "buddy=$cachedBuddyService, state=${ks?.let { kernelState(it) }}",
        )
        return false
    }

    private fun publishCoreStatus(
        phase: CoreServicesPhase,
        reason: String? = null,
        attempt: Int = _coreServicesStatus.value.attempt,
        elapsedMillis: Long = _coreServicesStatus.value.elapsedMillis,
    ) {
        _coreServicesStatus.value = CoreServicesStatus(phase, reason, attempt, elapsedMillis)
    }

    fun areCoreServicesReady(): Boolean {
        return cachedMsgService != null &&
                cachedRecentService != null &&
                cachedBuddyService != null
    }

    fun awaitGroupService(
        timeoutMillis: Long = 15_000,
        runtimeOverride: AppRuntime? = null,
    ): IGroupService? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val runtime = runtimeOverride ?: QmceApplication.ensureRuntime()
            val kernelService = cachedKs ?: runCatching {
                runtime?.getRuntimeService(IKernelService::class.java, "")
            }.getOrNull()
            if (kernelService != null) {
                cacheServices(kernelService)
                cachedGroupService?.let { return it }
            }
            Thread.sleep(250)
        }
        QmceLog.w(TAG, "KernelBridge: timed out waiting for group service")
        return cachedGroupService
    }

    /** bind 完成后由 waitForSession 调用，缓存各子 service */
    private fun cacheServices(ks: IKernelService) {
        cachedKs = ks
        completeExistingKernelInit(ks)
        val nextMsgService = runCatching { ks.getMsgService() }.getOrNull()
        if (cachedMsgService !== nextMsgService) {
            synchronized(officialMessageLock) {
                officialMessageKernel = null
                officialMessageService = null
            }
        }
        cachedMsgService = nextMsgService
        cachedRecentService = runCatching { ks.getRecentContactService() }.getOrNull()
        cachedBuddyService = runCatching { ks.getBuddyService() }.getOrNull()
        cachedGroupService = runCatching { ks.getGroupService() }.getOrNull()
        QmceLog.d(
            TAG,
            "KernelBridge: cached services — ks=$cachedKs, msg=$cachedMsgService, " +
                    "recent=$cachedRecentService, buddy=$cachedBuddyService, state=${kernelState(ks)}"
        )
    }

    /**
     * 某些冷启动路径会先创建 wrapperSession，再错过 KernelServiceImpl 的 session listener；
     * native 层已经能收消息，但 isNTStartFinish 仍是 false，官方 service getter 因此全部返回 null。
     *
     * 注意：initService() 在 startupSessionWrapper==null 时会把 hadStartNT CAS 成 true 却不设
     * isNTStartFinish，之后 initService 永久 no-op。因此仅在 startupSessionWrapper 非空时调它；
     * 若 hadStartNT 已卡死而 wrapper 已在，则强制 isNTStartFinish=true 解锁 getter。
     */
    private fun completeExistingKernelInit(ks: IKernelService) {
        runCatching {
            val impl = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelServiceImpl")
            val wrapperField = impl.getDeclaredField("wrapperSession").apply { isAccessible = true }
            val wrapper = wrapperField.get(ks)
            if (wrapper == null) {
                QmceLog.d(
                    TAG,
                    "KernelBridge: completeExistingKernelInit skipped; wrapperSession=null ks=$ks"
                )
                return@runCatching
            }
            val readyField = impl.getDeclaredField("isNTStartFinish").apply { isAccessible = true }
            val readyAtomic =
                readyField.get(ks) as? java.util.concurrent.atomic.AtomicBoolean ?: return@runCatching
            if (readyAtomic.get()) {
                QmceLog.d(TAG, "KernelBridge: existing kernel already ready ks=$ks wrapper=$wrapper")
                return@runCatching
            }
            val startupField =
                impl.getDeclaredField("startupSessionWrapper").apply { isAccessible = true }
            val startupWrapper = startupField.get(ks)
            val hadStartField =
                impl.getDeclaredField("hadStartNT").apply { isAccessible = true }
            val hadStart =
                (hadStartField.get(ks) as? java.util.concurrent.atomic.AtomicBoolean)?.get() == true

            if (startupWrapper == null) {
                QmceLog.w(
                    TAG,
                    "KernelBridge: wrapperSession present but startupSessionWrapper=null; " +
                            "skip initService (hadStartNT=$hadStart) to avoid permanent stall",
                )
                if (hadStart) {
                    readyAtomic.set(true)
                    QmceLog.w(
                        TAG,
                        "KernelBridge: forced isNTStartFinish=true after hadStartNT stall " +
                                "ks=$ks wrapper=$wrapper",
                    )
                }
                return@runCatching
            }

            QmceLog.w(
                TAG,
                "KernelBridge: existing wrapper session found with isNTStartFinish=false; completing init"
            )
            impl.getDeclaredMethod("initService").apply { isAccessible = true }.invoke(ks)
            var after = readyAtomic.get()
            if (!after && hadStart) {
                readyAtomic.set(true)
                after = true
                QmceLog.w(TAG, "KernelBridge: forced isNTStartFinish=true after initService no-op")
            }
            QmceLog.i(TAG, "KernelBridge: forced existing kernel init complete=$after wrapper=$wrapper")
        }.onFailure {
            QmceLog.e(TAG, "KernelBridge: forced existing kernel init failed", it)
        }
    }

    private fun readWrapperSession(ks: IKernelService?): Any? {
        if (ks == null) return null
        return runCatching {
            ks.javaClass.getDeclaredField("wrapperSession").apply { isAccessible = true }.get(ks)
        }.getOrNull()
    }

    private fun kernelState(ks: IKernelService): String {
        return runCatching {
            val impl = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelServiceImpl")
            val wrapper =
                impl.getDeclaredField("wrapperSession").apply { isAccessible = true }.get(ks)
            val ready =
                (impl.getDeclaredField("isNTStartFinish").apply { isAccessible = true }.get(ks)
                        as? java.util.concurrent.atomic.AtomicBoolean)?.get()
            val hadStart =
                (impl.getDeclaredField("hadStartNT").apply { isAccessible = true }.get(ks)
                        as? java.util.concurrent.atomic.AtomicBoolean)?.get()
            val startup =
                impl.getDeclaredField("startupSessionWrapper").apply { isAccessible = true }.get(ks)
            "wrapper=${wrapper != null}, isNTStartFinish=$ready, hadStartNT=$hadStart, " +
                    "startupSessionWrapper=${startup != null}"
        }.getOrElse { "stateError=${it.javaClass.simpleName}" }
    }

    @Volatile
    private var msfConnectionBridgeRegistered = false

    private var msfConnectionListener: com.tencent.qqnt.watch.mainframe.api.IMsfConnPushListener? = null

    @Volatile
    private var foregroundReplayedSession: IQQNTWrapperSession? = null

    /** 避免 awaitCoreServices 在同一 ks 上反复 start */
    @Volatile
    private var lastForcedStartKs: IKernelService? = null

    fun bindLoggedInAccount(uin: String, account: SimpleAccount): String {
        return runCatching {
            val app = MobileQQ.sMobileQQ ?: return "MobileQQ null"
            runCatching { app.setLastLoginUin(uin) }
            runCatching { app.setSortAccountList(arrayListOf(account)) }
            val runtime = QmceApplication.ensureRuntime(app)
            QmceLog.d(
                TAG,
                "bind: runtime=$runtime, isLogin=${runtime?.isLogin()}, uin=${runtime?.currentUin}"
            )
            runCatching { runtime?.login(account) }
            runCatching { runtime?.setLogined() }
            QmceLog.d(
                TAG,
                "bind: after setLogined, isLogin=${runtime?.isLogin()}, uin=${runtime?.currentUin}"
            )

            if (!initialModuleInjected) initialModuleInjected = injectInitialModule()
            if (!nativeKernelLoaded) nativeKernelLoaded = loadNativeKernel()
            if (initialModuleInjected) reinitWrapperEngineConfig()
            if (!accountModuleInjected) accountModuleInjected = injectSAccountModule()

            checkTicketStatus(runtime, uin)

            val ks = runCatching {
                runtime?.getRuntimeService(IKernelService::class.java, "")
            }.getOrNull()
            QmceLog.d(TAG, "bind: kernelService=$ks")

            // createRuntime 里已经自动继承登录态，不用再调 waitAppRuntime 拿新实例
            val actualRuntime = runtime
            pinRuntime(actualRuntime)

            if (ks != null) {
                val existingSession = readWrapperSession(ks)
                QmceLog.d(TAG, "bind: existingSession=$existingSession")
                if (existingSession == null) {
                    // pinRuntime 已在上面完成，先 patch serviceContent 再 start
                    patchServiceContent(ks, actualRuntime ?: runtime)
                    startKernelSession(ks, actualRuntime)
                } else {
                    QmceLog.d(TAG, "bind: session already exists, initializing directly")
                    initExistingKernel(actualRuntime, ks)
                }
            }

            val sessionOk = waitForSession(ks, actualRuntime)
            if (!sessionOk) {
                QmceLog.e(
                    TAG,
                    "bind: wrapperSession still null after start+wait; " +
                            "ks=$ks state=${ks?.let { kernelState(it) }}",
                )
            }
            reinitializeAfterLogin(actualRuntime)
            when {
                areCoreServicesReady() -> "ok"
                // 账号已绑定、票可用，但 NT session/服务未齐；勿清 LoginPrefs
                else -> "kernel-not-ready"
            }
        }.getOrElse { "failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    fun reinitializeAfterLogin(runtime: AppRuntime?): Boolean {
        // 不要清空已有非空缓存；只在后续 await/cacheServices 时覆盖刷新
        if (cachedMsgService != null || cachedRecentService != null || cachedBuddyService != null) {
            QmceLog.d(
                TAG,
                "login reinitialize: keeping cached services " +
                        "msg=$cachedMsgService recent=$cachedRecentService buddy=$cachedBuddyService",
            )
        }

        val coreReady = awaitCoreServices(timeoutMillis = 30_000, runtimeOverride = runtime)
        if (!coreReady) {
            QmceLog.w(TAG, "login reinitialize: core services unavailable")
            return false
        }

        runCatching {
            cachedMsgService?.switchForeGround(object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    QmceLog.d(TAG, "login reinitialize: switchForeGround code=$code, errMsg=$errMsg")
                }
            })
            cachedMsgService?.startMsgSync()
            QmceLog.d(TAG, "login reinitialize: startMsgSync called")
        }.onFailure { QmceLog.w(TAG, "login reinitialize: message sync failed", it) }

        runCatching {
            val contactService = runtime?.getRuntimeService(IContactRuntimeService::class.java, "")
            contactService?.initUinToUidCache(true)
            QmceLog.d(TAG, "login reinitialize: initUinToUidCache(true) called")
        }.onFailure { QmceLog.w(TAG, "login reinitialize: contact cache init failed", it) }

        runCatching {
            cachedBuddyService?.getBuddyList(true, object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    QmceLog.d(TAG, "login reinitialize: getBuddyList code=$code, errMsg=$errMsg")
                }
            })
            QmceLog.d(TAG, "login reinitialize: getBuddyList(true) called")
        }.onFailure { QmceLog.w(TAG, "login reinitialize: buddy refresh failed", it) }

        runCatching {
            val context = com.tencent.qphone.base.util.BaseApplication.getContext()
            context.sendBroadcast(
                android.content.Intent("com.tencent.mobileqq.action.ON_KERNEL_INIT_COMPLETE")
                    .setPackage(context.packageName)
            )
            QmceLog.d(TAG, "login reinitialize: ON_KERNEL_INIT_COMPLETE sent")
        }.onFailure { QmceLog.w(TAG, "login reinitialize: init broadcast failed", it) }

        return cachedMsgService != null && cachedRecentService != null
    }

    /** Must run before KernelSetterImpl first use so native CheckConfig sees valid version/platform. */
    fun ensureEarlyNativeBootstrap() {
        if (!initialModuleInjected) initialModuleInjected = injectInitialModule()
        injectSAppSetting()
        if (initialModuleInjected) reinitWrapperEngineConfig()
    }

    private fun injectInitialModule(): Boolean {
        return runCatching {
            val setterCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelSetterImpl")
            val field = setterCls.getDeclaredField("sInitialModule")
            field.isAccessible = true
            val iface = Class.forName("com.tencent.qqnt.kernel.dependences.IInitialModule")
            val delegate = field.get(null) ?: Class.forName("com.tencent.qqnt.watch.inject.InitialModuleInjector")
                .getDeclaredConstructor()
                .newInstance()
            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                iface.classLoader,
                arrayOf(iface),
            ) { _, method, args ->
                if (method.name == "e" || method.returnType.simpleName == "WrapperEngineGlobalConfig") {
                    val config = method.invoke(delegate, *(args ?: emptyArray()))
                        ?: Class.forName("com.tencent.qqnt.kernel.nativeinterface.WrapperEngineGlobalConfig")
                            .getDeclaredConstructor()
                            .newInstance()
                    val configCls = config.javaClass
                    configCls.getField("appVersion").set(config, "9.0.7.2563")
                    configCls.getField("platformType").setInt(config, 1) // PlatformType.KANDROID
                    configCls.getField("appType").setInt(config, 7)
                    runCatching {
                        configCls.getField("osVersion").set(config, android.os.Build.VERSION.RELEASE)
                    }
                    runCatching {
                        val qua = Class.forName("com.tencent.qqnt.watch.inject.AppSettingInjector")
                            .getDeclaredConstructor()
                            .newInstance()
                            .let { injector ->
                                injector.javaClass.getMethod("getQUA").invoke(injector) as? String
                            }
                        if (!qua.isNullOrBlank()) {
                            configCls.getField("qua").set(config, qua)
                        }
                    }
                    QmceLog.d(TAG, "bind: patched WrapperEngineGlobalConfig appVersion=9.0.7.2563 platformType=1")
                    config
                } else {
                    method.invoke(delegate, *(args ?: emptyArray()))
                }
            }
            field.set(null, proxy)
            QmceLog.d(TAG, "bind: InitialModuleInjector patched with fixed global config")
            true
        }.getOrElse { error ->
            QmceLog.e(TAG, "bind: injectInitialModule failed", error)
            false
        }
    }

    private fun reinitWrapperEngineConfig() {
        runCatching {
            val setterCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelSetterImpl")
            val companionField = setterCls.getDeclaredField("Companion")
            companionField.isAccessible = true
            val companion = companionField.get(null)
            val cMethod = companion.javaClass.getDeclaredMethod("c")
            cMethod.isAccessible = true
            cMethod.invoke(companion)
            QmceLog.d(TAG, "bind: reinitWrapperEngineConfig via KernelSetterImpl.Companion.c() OK")
        }.onFailure { QmceLog.w(TAG, "bind: reinitWrapperEngineConfig skipped", it) }
    }

    private fun loadNativeKernel(): Boolean {
        nativePatchEligible = verifyNativeBaseline()
        if (initialModuleInjected) return true
        // Fallback: load native libs when bootstrap has not run yet (e.g. cold paths).
        val libs = listOf(
            "basic_share",
            "djinni_support_lib",
            "module_service",
            "djinni_interface_core_public",
            "gprowrapper",
            "wrapper",
            "startup",
        )
        var ok = true
        for (lib in libs) {
            runCatching { System.loadLibrary(lib) }
                .onFailure {
                    ok = false
                    QmceLog.w(TAG, "bind: loadLibrary($lib) failed", it)
                }
        }
        return ok
    }

    /**
     * Native work is valid only for the extracted QQ Watch 9.0.7 baseline. This
     * check does not block normal library loading; it prevents any future native
     * patch path from being enabled against an unknown APK build.
     */
    private fun verifyNativeBaseline(): Boolean {
        val expected = mapOf(
            "libstartup.so" to "3D9C3BC58DEAD5C6A2581DFC05BC3C7ABEBDE3E08BFA1CB7032E1D9F56E162BC",
            "libwrapper.so" to "8FF5BF7AEEB96DFB888A371F06329C264802294C4C9C64916F1EB2EAFF513784",
        )
        val nativeDir = runCatching {
            com.tencent.qphone.base.util.BaseApplication.getContext()
                .applicationInfo.nativeLibraryDir
        }.getOrNull() ?: return false
        val actual = expected.mapValues { (name, _) ->
            sha256(File(nativeDir, name))
        }
        val valid = expected.all { (name, hash) -> actual[name] == hash }
        QmceLog.i(TAG, "KernelBridge: native baseline eligible=$valid expected=$expected actual=$actual")
        return valid
    }

    private fun sha256(file: File): String? = runCatching {
        if (!file.isFile) return@runCatching null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02X".format(byte) }
    }.getOrNull()

    /** 锁定 mAppRuntime 字段 + serviceContent 里的 runtime，
     *  防止 waitAppRuntime 创建新未初始化实例替换 */
    private fun pinRuntime(runtime: AppRuntime?) {
        if (runtime == null) return

        // createRuntime() may be called by MobileQQ while login callbacks are running.
        // Keep the runtime that owns the KernelService we are about to start as the
        // application-wide source of truth as well.
        QmceApplication.sAppRuntime = runtime

        // 1. MobileQQ.mAppRuntime
        runCatching {
            val f = MobileQQ::class.java.getDeclaredField("mAppRuntime")
            f.isAccessible = true
            val current = f.get(MobileQQ.sMobileQQ)
            if (current !== runtime) {
                f.set(MobileQQ.sMobileQQ, runtime)
                QmceLog.d(TAG, "bind: pinned mAppRuntime: $current -> $runtime")
            }
            val stateField = MobileQQ::class.java.getDeclaredField("mRuntimeState")
            stateField.isAccessible = true
            (stateField.get(MobileQQ.sMobileQQ) as? java.util.concurrent.atomic.AtomicInteger)?.set(
                3
            )
        }.onFailure { QmceLog.e(TAG, "bind: pinRuntime mAppRuntime failed", it) }

        // 2. KernelServiceImpl.serviceContent 里的 WeakReference<AppRuntime>
        runCatching {
            val ks = runtime.getRuntimeService(IKernelService::class.java, "")
            patchServiceContent(ks, runtime)
            QmceLog.d(TAG, "bind: pinned serviceContent runtime -> $runtime")
        }.onFailure { QmceLog.e(TAG, "bind: pinRuntime serviceContent failed", it) }
    }

    /** 在 ks.start() 之前 patch serviceContent WeakReference，
     *  确保 serviceContent.a() 非空，否则官方 start() 会静默跳过 startSession */
    private fun patchServiceContent(ks: IKernelService, runtime: AppRuntime?) {
        if (runtime == null) {
            QmceLog.w(TAG, "patchServiceContent: runtime is null, skip")
            return
        }
        runCatching {
            val ksImplCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelServiceImpl")
            val scField = ksImplCls.getDeclaredField("serviceContent"); scField.isAccessible = true
            val sc = scField.get(ks) ?: return@runCatching
            val aField = sc.javaClass.getDeclaredField("a"); aField.isAccessible = true
            val weakRef = aField.get(sc)
            if (weakRef == null) {
                val weakRefClass = Class.forName("mqq.util.WeakReference")
                val created = weakRefClass
                    .getDeclaredConstructor(Any::class.java)
                    .newInstance(runtime)
                aField.set(sc, created)
                QmceLog.d(TAG, "patchServiceContent: created WeakReference for runtime=$runtime")
            } else {
                setWeakRefReferent(weakRef, runtime)
            }
            // also refresh appUin field when present
            runCatching {
                val bField = sc.javaClass.getDeclaredField("b"); bField.isAccessible = true
                bField.set(sc, runtime.currentAccountUin)
            }
            QmceLog.d(
                TAG,
                "patchServiceContent: set runtime=$runtime, isLogin=${runtime.isLogin()}, isRunning=${runtime.isRunning}"
            )
        }.onFailure { QmceLog.e(TAG, "patchServiceContent failed", it) }
    }

    /** ART 上 referent 在 java.lang.ref.Reference；null referent 也必须写入，否则 ks.start 空转 */
    private fun setWeakRefReferent(weakRef: Any, value: Any?) {
        var cls: Class<*>? = weakRef.javaClass
        while (cls != null) {
            val fields = runCatching { cls.declaredFields }.getOrNull()
            if (fields != null) {
                for (f in fields) {
                    if (f.name != "referent") continue
                    f.isAccessible = true
                    val current = f.get(weakRef)
                    if (current !== value) {
                        f.set(weakRef, value)
                        QmceLog.d(
                            TAG,
                            "setWeakRefReferent: patched field '${f.name}' in ${cls.simpleName} " +
                                "(wasNull=${current == null})"
                        )
                    }
                    return
                }
            }
            cls = cls.superclass
        }
        QmceLog.w(TAG, "setWeakRefReferent: could not find referent field")
    }

    private fun injectSAccountModule(): Boolean {
        return runCatching {
            val ksImplCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelServiceImpl")
            val sAccountModuleField = ksImplCls.getDeclaredField("sAccountModule")
            sAccountModuleField.isAccessible = true
            if (sAccountModuleField.get(null) == null) {
                val accountModuleCls =
                    Class.forName("com.tencent.qqnt.watch.inject.AccountModuleInjector")
                val accountModule = accountModuleCls.getDeclaredConstructor().newInstance()
                sAccountModuleField.set(null, accountModule)
                QmceLog.d(TAG, "bind: sAccountModule set to $accountModule")
            }
        }.onFailure { QmceLog.e(TAG, "bind: set sAccountModule failed", it) }.isSuccess
    }

    private fun createPatchedAppSettingInjector(): Any? {
        return runCatching {
            val iface = Class.forName("com.tencent.mobileqq.inject.IAppSettingInject")
            val delegate = Class.forName("com.tencent.qqnt.watch.inject.AppSettingInjector")
                .getDeclaredConstructor()
                .newInstance()
            java.lang.reflect.Proxy.newProxyInstance(
                iface.classLoader,
                arrayOf(iface),
            ) { _, method, args ->
                when (method.name) {
                    "d" -> "9.0.7.2563"
                    "e" -> "9.0.7.2563"
                    "h" -> "2563"
                    "j" -> "V 9.0.7.2563"
                    else -> method.invoke(delegate, *(args ?: emptyArray()))
                }
            }
        }.getOrNull()
    }

    private fun injectSAppSetting(ks: IKernelService? = null) {
        runCatching {
            val proxy = createPatchedAppSettingInjector()
                ?: return@runCatching
            val setterCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelSetterImpl")
            val field = setterCls.getDeclaredField("sAppSetting")
            field.isAccessible = true
            field.set(null, proxy)
            if (ks != null) {
                val ksField = ks.javaClass.getDeclaredField("sAppSetting")
                ksField.isAccessible = true
                ksField.set(ks, proxy)
                QmceLog.d(TAG, "bind: patched KernelServiceImpl.sAppSetting on $ks")
            }
            QmceLog.d(TAG, "bind: AppSettingInjector patched with fixed version proxy")
        }.onFailure { QmceLog.e(TAG, "bind: patch AppSettingInjector failed", it) }
    }

    private fun checkTicketStatus(runtime: AppRuntime?, uin: String) {
        runCatching {
            val ticketClass =
                Class.forName("com.tencent.qqnt.account.login.api.ITicketRuntimeService")
            val m = runtime?.javaClass?.methods?.firstOrNull {
                it.name == "getRuntimeService" && it.parameterTypes.size == 2
            }
            val ticketSvc = m?.invoke(runtime, ticketClass, "")
            QmceLog.d(TAG, "bind: ticketSvc=$ticketSvc")
            if (ticketSvc != null) {
                val a2 = runCatching {
                    ticketSvc.javaClass.getMethod("getA2", String::class.java)
                        .invoke(ticketSvc, uin)
                }.getOrNull()
                QmceLog.d(TAG, "bind: A2=$a2")
                val localTicket = runCatching {
                    ticketSvc.javaClass.getMethod(
                        "getLocalTicket",
                        String::class.java,
                        Int::class.javaPrimitiveType
                    )
                        .invoke(ticketSvc, uin, 262144)
                }.getOrNull()
                QmceLog.d(TAG, "bind: localTicket=$localTicket")
            }
        }.onFailure { QmceLog.e(TAG, "bind: ticket check failed", it) }
    }

    private fun startKernelSession(ks: IKernelService, runtime: AppRuntime?) {
        val boundRuntime = runtime ?: run {
            QmceLog.e(TAG, "bind: cannot start kernel session without runtime")
            return
        }

        if (!initialModuleInjected) initialModuleInjected = injectInitialModule()
        // Ensure native modules and account injection are present even on cold-start paths
        // that reach awaitCoreServices()/startKernelSession without running bindLoggedInAccount().
        if (!nativeKernelLoaded) nativeKernelLoaded = loadNativeKernel()
        if (initialModuleInjected) reinitWrapperEngineConfig()
        if (!accountModuleInjected) accountModuleInjected = injectSAccountModule()

        val setter = runCatching {
            val cls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelSetterImpl")
            cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        }.onFailure { QmceLog.e(TAG, "bind: create KernelSetterImpl failed", it) }.getOrNull() ?: return

        // mAppRef
        runCatching {
            val f = setter.javaClass.getDeclaredField("mAppRef"); f.isAccessible = true
            val weakRefClass = Class.forName("mqq.util.WeakReference")
            f.set(setter, weakRefClass.getDeclaredConstructor(Any::class.java).newInstance(runtime))
        }
        // ensureInject
        runCatching {
            setter.javaClass.getMethod("ensureInject").invoke(setter)
        }.onFailure { QmceLog.e(TAG, "bind: ensureInject failed", it) }
        // sAppSetting 注入
        injectSAppSetting(ks)

        // 创建 IKernelCreateListener，注册到 getAccountCallback
        val kernelCreateListener = java.lang.reflect.Proxy.newProxyInstance(
            Class.forName("com.tencent.qqnt.kernel.api.IKernelCreateListener").classLoader,
            arrayOf(Class.forName("com.tencent.qqnt.kernel.api.IKernelCreateListener"))
        ) { _, method, args ->
            when (method.name) {
                "a" -> { // onKernelCreate: 官方在此调用 setServletKernelInit
                    QmceLog.d(TAG, "IKernelCreateListener.a called (kernel created)")
                    runCatching {
                        val setterCls =
                            Class.forName("com.tencent.qqnt.kernel.api.impl.KernelSetterImpl")
                        val m = setterCls.getMethod("setServletKernelInit")
                        m.invoke(setter)
                        QmceLog.d(TAG, "setServletKernelInit OK")
                    }.onFailure { QmceLog.e(TAG, "setServletKernelInit failed", it) }
                    null
                }

                "b" -> { // onKernelInitComplete: 发送 ON_KERNEL_INIT_COMPLETE 广播
                    QmceLog.d(TAG, "IKernelCreateListener.b called (kernel init complete)")
                    registerOfficialMsfConnectionBridge(runtime)
                    registerOfficialForegroundCallback(runtime)
                    initializeOfficialMessageBridge(runtime)
                    runCatching {
                        // initUinToUidCache (官方 KernelInitTask 在此调用)
                        val contactSvc = runCatching {
                            val m = boundRuntime.javaClass.methods.firstOrNull {
                                it.name == "getRuntimeService" && it.parameterTypes.size == 2
                            }
                            m?.invoke(
                                boundRuntime,
                                Class.forName("com.tencent.qqnt.watch.contact.api.IContactRuntimeService"),
                                ""
                            )
                        }.getOrNull()
                        QmceLog.d(TAG, "initUinToUidCache: contactSvc=$contactSvc")
                        if (contactSvc != null) {
                            val m = contactSvc.javaClass.getMethod(
                                "initUinToUidCache",
                                Boolean::class.javaPrimitiveType
                            )
                            m.invoke(contactSvc, true) // true = fetch from server
                            QmceLog.d(TAG, "initUinToUidCache(true) OK")
                        }
                    }.onFailure { QmceLog.e(TAG, "initUinToUidCache failed", it) }

                    // 直接调 IBuddyService.getBuddyList(true, callback) 强制拉取
                    runCatching {
                        val ks = runCatching {
                            val m = boundRuntime.javaClass.methods.firstOrNull {
                                it.name == "getRuntimeService" && it.parameterTypes.size == 2
                            }
                            m?.invoke(boundRuntime, IKernelService::class.java, "")
                        }.getOrNull() as? IKernelService
                        val buddySvc = ks?.getBuddyService()
                        QmceLog.d(TAG, "buddySvc=$buddySvc")
                        if (buddySvc != null) {
                            val callback =
                                object : com.tencent.qqnt.kernel.nativeinterface.IOperateCallback {
                                    override fun onResult(code: Int, errMsg: String?) {
                                        QmceLog.d(
                                            TAG,
                                            "getBuddyList result: code=$code, errMsg=$errMsg"
                                        )
                                    }
                                }
                            buddySvc.getBuddyList(true, callback)
                            QmceLog.d(TAG, "getBuddyList(true) called")
                        }
                    }.onFailure { QmceLog.e(TAG, "getBuddyList direct failed", it) }

                    runCatching {
                        val ctx = com.tencent.qphone.base.util.BaseApplication.getContext()
                        val intent =
                            android.content.Intent("com.tencent.mobileqq.action.ON_KERNEL_INIT_COMPLETE")
                                .setPackage(ctx.packageName)
                        ctx.sendBroadcast(intent)
                        QmceLog.d(TAG, "ON_KERNEL_INIT_COMPLETE broadcast sent")
                    }.onFailure { QmceLog.e(TAG, "sendBroadcast failed", it) }
                    null
                }

                "hashCode" -> 42
                "equals" -> false
                "toString" -> "QMCE-KernelCreateListener"
                else -> {
                    QmceLog.d(TAG, "IKernelCreateListener: unexpected method=${method.name}")
                    null
                }
            }
        }
        runCatching {
            val getCallback = setter.javaClass.getMethod(
                "getAccountCallback",
                Class.forName("com.tencent.qqnt.kernel.api.IKernelCreateListener")
            )
            val accountCallback = getCallback.invoke(setter, kernelCreateListener)
            QmceLog.d(TAG, "bind: getAccountCallback returned=$accountCallback")
        }.onFailure { QmceLog.e(TAG, "bind: getAccountCallback failed", it) }

        // 原路径 onAccountChanged 被包名校验拦截，直接调 ks.start(listener)
        val listener = kernelCreateListener as IKernelCreateListener
        runCatching {
            ks.start(listener)
            QmceLog.d(TAG, "bind: ks.start(listener) OK state=${kernelState(ks)}")
        }.onFailure { QmceLog.e(TAG, "bind: ks.start(listener) failed", it) }

        // Do not invoke startSession reflectively here. On 9.0.7 it creates another
        // native shell after a failed official start and turns one missing session into
        // an unbounded stream of NPEs. A later explicit retry starts a fresh cycle.
        if (readWrapperSession(ks) == null) {
            QmceLog.w(
                TAG,
                "bind: official start returned without wrapperSession; " +
                    "state=${kernelState(ks)}",
            )
        }
    }

    /**
     * startSession NPE 的 native 侧逻辑会固定使用 StartupSessionConstant.KNTMODULENAME，
     * 但实际返回的 sessionIdList 里该 key 在 watch 冷启动路径上可能不匹配，导致
     * getNTWrapperSession 取不到 wrapperSession。
     *
     * 这里改为直接从 IQQNTStartupSessionWrapper.CppProxy.create() 取 sessionIdList，
     * 遍历其 values 调用 getNTWrapperSession，找到非空结果后回填到 KernelServiceImpl。
     */
    private fun tryForceWrapperSessionFromCppProxy(
        ks: IKernelService,
        runtime: AppRuntime,
    ): Boolean {
        return runCatching {
            // Ensure serviceContent runtime pointer exists; initService depends on it.
            patchServiceContent(ks, runtime)

            val implCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelServiceImpl")
            val wrapperField = implCls.getDeclaredField("wrapperSession").apply { isAccessible = true }
            val startupWrapperField =
                implCls.getDeclaredField("startupSessionWrapper").apply { isAccessible = true }

            val startupWrapperCls =
                Class.forName("com.tencent.qqnt.ntstartup.nativeinterface.IQQNTStartupSessionWrapper")
            val startupCppProxyCls = startupWrapperCls.declaredClasses
                .firstOrNull { it.simpleName == "CppProxy" }
                ?: return false
            val createMethod = startupCppProxyCls.getDeclaredMethod("create")
            val startupWrapper = createMethod.invoke(null) ?: return false

            val sessionIdListObj =
                startupWrapper.javaClass.getMethod("getSessionIdList").invoke(startupWrapper)
            val sessionIdList = sessionIdListObj as? Map<*, *> ?: return false

            val valueCandidates = sessionIdList.values.mapNotNull { it as? String }
            val keyCandidates = sessionIdList.keys.mapNotNull { it as? String }
            val candidateIds = (valueCandidates + keyCandidates).distinct()
            if (candidateIds.isEmpty()) {
                QmceLog.w(
                    TAG,
                    "bind: tryForceWrapperSession empty candidateIds; " +
                        "keyCandidates=${keyCandidates.take(10)} valueCandidates=${valueCandidates.take(10)}",
                )
                return false
            }

            QmceLog.d(
                TAG,
                "bind: tryForceWrapperSession sessionIdList.size=${sessionIdList.size()} " +
                    "keyCandidates=${keyCandidates.take(10)} " +
                    "valueCandidates=${valueCandidates.take(10)} " +
                    "candidates=${candidateIds.take(10)}",
            )

            val wrapperCls = Class.forName("com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession")
            val wrapperCppProxyCls = wrapperCls.declaredClasses
                .firstOrNull { it.simpleName == "CppProxy" }
                ?: return false
            val getNtMethod = wrapperCppProxyCls.getDeclaredMethod(
                "getNTWrapperSession",
                String::class.java,
            )

            for (candidate in candidateIds) {
                val ws = runCatching { getNtMethod.invoke(null, candidate) }.getOrNull()
                if (ws != null) {
                    wrapperField.set(ks, ws)
                    startupWrapperField.set(ks, startupWrapper)
                    QmceLog.d(
                        TAG,
                        "bind: forced wrapperSession via candidate=$candidate " +
                            "state=${kernelState(ks)}",
                    )
                    return true
                }
            }

            QmceLog.w(
                TAG,
                "bind: tryForceWrapperSessionFromCppProxy no candidate wrapper found; " +
                    "candidates=${candidateIds.take(10)}",
            )
            return false
        }.getOrElse { error ->
            QmceLog.w(TAG, "bind: tryForceWrapperSessionFromCppProxy failed", error)
            false
        }
    }

    private fun readServiceContentRuntime(ks: IKernelService): AppRuntime? =
        runCatching {
            val scField = ks.javaClass.getDeclaredField("serviceContent").apply { isAccessible = true }
            val sc = scField.get(ks) ?: return@runCatching null
            val aField = sc.javaClass.getDeclaredField("a").apply { isAccessible = true }
            val weakRef = aField.get(sc) ?: return@runCatching null
            (weakRef as? java.lang.ref.Reference<*>)?.get() as? AppRuntime
                ?: runCatching {
                    weakRef.javaClass.getMethod("get").invoke(weakRef) as? AppRuntime
                }.getOrNull()
        }.getOrNull()

    private fun invokeStartSession(
        ks: IKernelService,
        runtime: AppRuntime,
        listener: IKernelCreateListener?,
    ) {
        runCatching {
            val m = ks.javaClass.getDeclaredMethod(
                "startSession",
                AppRuntime::class.java,
                IKernelCreateListener::class.java,
            )
            m.isAccessible = true
            m.invoke(ks, runtime, listener)
            QmceLog.d(TAG, "bind: reflective startSession OK")
        }.onFailure { QmceLog.e(TAG, "bind: reflective startSession failed", it) }
    }

    private fun registerOfficialForegroundCallback(runtime: AppRuntime?) {
        if (foregroundCallbackRegistered || runtime == null) return
        runCatching {
            runtime.getRuntimeService(IMsgPushForegroundApi::class.java, "")
        }.onSuccess { api ->
            api.registerForegroundCallback()
            foregroundCallbackRegistered = true
            QmceLog.d(TAG, "bind: official foreground callback registered api=$api")
        }.onFailure { error ->
            QmceLog.w(TAG, "bind: official foreground callback unavailable", error)
        }
    }

    private fun initializeOfficialMessageBridge(runtime: AppRuntime?) {
        ensureOfficialMessageBridge(runtime)
    }

    private fun registerOfficialMsfConnectionBridge(runtime: AppRuntime?) {
        if (msfConnectionBridgeRegistered || runtime == null) return
        runCatching {
            val helper = QRoute.api(IMsfConnHelper::class.java)
            val listener = createMsfConnectionListener(runtime)
            helper.initMsfConnPush()
            helper.addPushListener(listener)
            msfConnectionListener = listener
            msfConnectionBridgeRegistered = true
            QmceLog.d(TAG, "bind: official MSF connection bridge registered helper=$helper")
        }.onFailure { error ->
            QmceLog.w(TAG, "bind: official MSF connection bridge unavailable", error)
        }
    }

    /**
     * Runtime qq-sdk uses JVM names a/b/c/d; compile-time jar may expose Kotlin names.
     * Proxy both naming schemes to avoid AbstractMethodError on MSF conn push.
     */
    private fun createMsfConnectionListener(
        runtime: AppRuntime,
    ): com.tencent.qqnt.watch.mainframe.api.IMsfConnPushListener {
        val iface = com.tencent.qqnt.watch.mainframe.api.IMsfConnPushListener::class.java
        return java.lang.reflect.Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface),
        ) { _, method, _ ->
            when (method.name) {
                "c", "onConnOpen" -> updateMsfKernelStatus(runtime, MsfStatusType.KCONNECTED)
                "b", "onConnClose" -> updateMsfKernelStatus(runtime, MsfStatusType.KDISCONNECTED)
                "a", "onConnAllFailed", "onNetWeak", "d" -> Unit
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }
        } as com.tencent.qqnt.watch.mainframe.api.IMsfConnPushListener
    }

    private fun updateMsfKernelStatus(runtime: AppRuntime, status: MsfStatusType) {
        runCatching {
            runtime.getRuntimeService(IKernelService::class.java, "")
                .setOnMsfStatusChanged(status, MsfChangeReasonType.KAUTO, 0)
            QmceLog.d(TAG, "msfBridge: status=$status")
        }.onFailure { error ->
            QmceLog.w(TAG, "msfBridge: status=$status failed", error)
        }
    }

    /**
     * 等待 wrapperSession；若首轮超时则再强制 startKernelSession 并等第二轮。
     * @return 是否最终拿到非空 wrapperSession
     */
    private fun waitForSession(ks: IKernelService?, runtime: AppRuntime?): Boolean {
        if (ks == null) {
            QmceLog.e(TAG, "bind: waitForSession skipped; kernelService=null")
            return false
        }
        fun pollOnce(label: String, maxRounds: Int): Any? {
            var waitCount = 0
            while (waitCount < maxRounds) {
                Thread.sleep(500)
                waitCount++
                val ws = readWrapperSession(ks)
                if (ws != null) {
                    QmceLog.d(
                        TAG,
                        "bind: kernel session established ($label) after ${waitCount * 500}ms",
                    )
                    return ws
                }
            }
            return null
        }

        var ws = pollOnce("initial", 20)
        if (ws == null) {
            QmceLog.w(TAG, "bind: wrapperSession still null after initial wait; forcing startKernelSession")
            patchServiceContent(ks, runtime)
            startKernelSession(ks, runtime)
            lastForcedStartKs = ks
            ws = pollOnce("forced-start", 30)
        }
        if (ws == null && runtime != null) {
            QmceLog.w(
                TAG,
                "bind: wrapperSession still null after forced start; trying CppProxy session force; " +
                    "state=${kernelState(ks)}",
            )
            if (tryForceWrapperSessionFromCppProxy(ks, runtime)) {
                registerOfficialMsfConnectionBridge(runtime)
                registerOfficialForegroundCallback(runtime)
                initializeOfficialMessageBridge(runtime)
                ws = readWrapperSession(ks) ?: pollOnce("cpp-proxy-force", 5)
            }
        } else if (ws == null) {
            QmceLog.w(TAG, "bind: skip CppProxy force; runtime=null state=${kernelState(ks)}")
        }
        if (ws == null) {
            QmceLog.e(
                TAG,
                "bind: wrapperSession unavailable after forced start; state=${kernelState(ks)}",
            )
            return false
        }

        cacheServices(ks)
        var serviceWait = 0
        while (serviceWait < 30 &&
            (cachedMsgService == null || cachedRecentService == null || cachedBuddyService == null)
        ) {
            Thread.sleep(500)
            serviceWait++
            cacheServices(ks)
        }
        QmceLog.d(
            TAG,
            "bind: services after session wait=${serviceWait * 500}ms " +
                    "msg=$cachedMsgService recent=$cachedRecentService buddy=$cachedBuddyService " +
                    "state=${kernelState(ks)}",
        )
        replayForegroundToWrapperSession(ws)
        unblockPush()
        return true
    }

    private fun replayForegroundToWrapperSession(session: Any) {
        val wrapperSession = session as? IQQNTWrapperSession
        if (wrapperSession == null) {
            QmceLog.w(TAG, "bind: session foreground replay skipped; unexpected session=$session")
            return
        }
        if (foregroundReplayedSession === wrapperSession) {
            QmceLog.d(TAG, "bind: session foreground replay already sent")
            return
        }
        val guardForeground = runCatching { GuardManager.c?.f() == true }
            .onFailure { QmceLog.w(TAG, "bind: session foreground replay guard check failed", it) }
            .getOrDefault(false)
        val lifecycleForeground = runCatching { Foreground.isCurrentProcessForeground() }
            .onFailure { QmceLog.w(TAG, "bind: session foreground replay lifecycle check failed", it) }
            .getOrDefault(false)
        if (!guardForeground && !lifecycleForeground) {
            QmceLog.d(
                TAG,
                "bind: session foreground replay skipped; guard and lifecycle are background"
            )
            return
        }
        runCatching { wrapperSession.switchToFront() }
            .onSuccess {
                foregroundReplayedSession = wrapperSession
                QmceLog.i(
                    TAG,
                    "bind: replayed foreground to WrapperSession " +
                            "guard=$guardForeground lifecycle=$lifecycleForeground"
                )
            }
            .onFailure { QmceLog.w(TAG, "bind: WrapperSession.switchToFront failed", it) }
    }

    /** 复用已有 wrapperSession 时，补做 IKernelCreateListener 回调里的关键初始化 */
    private fun initExistingKernel(runtime: AppRuntime?, ks: IKernelService) {
        Thread.sleep(500)
        cacheServices(ks)
        runCatching {
            val contactSvc = runtime?.getRuntimeService(IContactRuntimeService::class.java, "")
            QmceLog.d(TAG, "initExistingKernel: contactSvc=$contactSvc")
            contactSvc?.initUinToUidCache(true)
            QmceLog.d(TAG, "initExistingKernel: initUinToUidCache(true) OK")
        }.onFailure { QmceLog.e(TAG, "initExistingKernel: initUinToUidCache failed", it) }

        runCatching {
            val buddySvc = ks.getBuddyService()
            QmceLog.d(TAG, "initExistingKernel: buddySvc=$buddySvc")
            buddySvc?.getBuddyList(true, object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    QmceLog.d(TAG, "initExistingKernel: getBuddyList code=$code, errMsg=$errMsg")
                }
            })
            QmceLog.d(TAG, "initExistingKernel: getBuddyList(true) called")
        }.onFailure { QmceLog.e(TAG, "initExistingKernel: getBuddyList failed", it) }

        runCatching {
            val ctx = com.tencent.qphone.base.util.BaseApplication.getContext()
            ctx.sendBroadcast(
                android.content.Intent("com.tencent.mobileqq.action.ON_KERNEL_INIT_COMPLETE")
                    .setPackage(ctx.packageName)
            )
            QmceLog.d(TAG, "initExistingKernel: ON_KERNEL_INIT_COMPLETE sent")
        }.onFailure { QmceLog.e(TAG, "initExistingKernel: broadcast failed", it) }

        unblockPush()
    }

    private fun unblockPush() {
        runCatching {
            val msfServiceCls = Class.forName("com.tencent.mobileqq.msf.service.MsfService")
            val core =
                msfServiceCls.getDeclaredField("core").apply { isAccessible = true }.get(null)
            if (core != null) {
                val pm =
                    core.javaClass.getDeclaredField("pushManager").apply { isAccessible = true }
                        .get(core)
                if (pm != null) {
                    val oField = pm.javaClass.getDeclaredField("o")
                    oField.isAccessible = true
                    QmceLog.d(TAG, "bind: PushManager.o before = ${oField.get(pm)}")
                    oField.set(pm, java.lang.Boolean.FALSE)
                    QmceLog.d(TAG, "bind: PushManager.o set FALSE — push unblocked")
                }
            }
        }.onFailure { QmceLog.e(TAG, "bind: unblock push failed", it) }
    }
}
