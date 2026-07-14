package rj.qmce.lite.kernel

import android.util.Log
import com.tencent.mobileqq.app.guard.GuardManager
import com.tencent.qqnt.kernel.api.IKernelCreateListener
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsfChangeReasonType
import com.tencent.qqnt.kernel.nativeinterface.MsfStatusType
import com.tencent.qqnt.msg.api.IMsgPushForegroundApi
import com.tencent.qqnt.watch.mainframe.api.IMsfConnHelper
import com.tencent.qqnt.watch.selftab.api.ISelfProfileRuntimeService
import com.tencent.qqnt.watch.contact.api.IContactRuntimeService
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.qphone.base.remote.SimpleAccount
import mqq.app.AppRuntime
import mqq.app.Foreground
import mqq.app.MobileQQ
import rj.qmce.lite.QmceApplication

private const val TAG = "QMCE"

object KernelBridge {
    @Volatile
    private var foregroundCallbackRegistered = false

    // 全局服务缓存
    @Volatile private var cachedKs: IKernelService? = null
    @Volatile private var cachedMsgService: com.tencent.qqnt.kernel.api.IMsgService? = null
    @Volatile private var cachedRecentService: com.tencent.qqnt.kernel.api.IRecentContactService? = null
    @Volatile private var cachedBuddyService: com.tencent.qqnt.kernel.api.IBuddyService? = null

    fun getKernelService(): IKernelService? = cachedKs
    fun getMsgService(): com.tencent.qqnt.kernel.api.IMsgService? = cachedMsgService
    fun getRecentContactService(): com.tencent.qqnt.kernel.api.IRecentContactService? = cachedRecentService
    fun getBuddyService(): com.tencent.qqnt.kernel.api.IBuddyService? = cachedBuddyService
    fun getSelfProfileService(): ISelfProfileRuntimeService? = runCatching {
        QmceApplication.ensureRuntime()?.getRuntimeService(ISelfProfileRuntimeService::class.java, "")
    }.getOrNull()

    fun awaitCoreServices(timeoutMillis: Long = 15_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val runtime = QmceApplication.ensureRuntime()
            val kernelService = cachedKs ?: runCatching {
                runtime?.getRuntimeService(IKernelService::class.java, "")
            }.getOrNull()
            if (kernelService != null) {
                cacheServices(kernelService)
                if (cachedMsgService != null && cachedRecentService != null) {
                    Log.d(TAG, "KernelBridge: core services ready")
                    return true
                }
            }
            Thread.sleep(250)
        }
        Log.w(TAG, "KernelBridge: timed out waiting for core services; msg=$cachedMsgService, recent=$cachedRecentService")
        return false
    }

    /** bind 完成后由 waitForSession 调用，缓存各子 service */
    private fun cacheServices(ks: IKernelService) {
        cachedKs = ks
        cachedMsgService = runCatching { ks.msgService }.getOrNull()
        cachedRecentService = runCatching { ks.recentContactService }.getOrNull()
        cachedBuddyService = runCatching { ks.buddyService }.getOrNull()
        Log.d(TAG, "KernelBridge: cached services — ks=$cachedKs, msg=$cachedMsgService, recent=$cachedRecentService, buddy=$cachedBuddyService")
    }

    @Volatile
    private var msfConnectionBridgeRegistered = false

    private var msfConnectionListener: MsfConnectionListener? = null

    @Volatile
    private var foregroundReplayedSession: IQQNTWrapperSession? = null

    fun bindLoggedInAccount(uin: String, account: SimpleAccount): String {
        return runCatching {
            val app = MobileQQ.sMobileQQ ?: return "MobileQQ null"
            runCatching { app.setLastLoginUin(uin) }
            runCatching { app.setSortAccountList(arrayListOf(account)) }
            val runtime = QmceApplication.ensureRuntime(app)
            Log.d(TAG, "bind: runtime=$runtime, isLogin=${runtime?.isLogin()}, uin=${runtime?.currentUin}")
            runCatching { runtime?.login(account) }
            runCatching { runtime?.setLogined() }
            Log.d(TAG, "bind: after setLogined, isLogin=${runtime?.isLogin()}, uin=${runtime?.currentUin}")

            loadNativeKernel()
            injectSAccountModule()

            checkTicketStatus(runtime, uin)

            val ks = runCatching {
                runtime?.getRuntimeService(IKernelService::class.java, "")
            }.getOrNull()
            Log.d(TAG, "bind: kernelService=$ks")

            // createRuntime 里已经自动继承登录态，不用再调 waitAppRuntime 拿新实例
            val actualRuntime = runtime
            pinRuntime(actualRuntime)

            if (ks != null) {
                val existingSession = runCatching {
                    val f = ks.javaClass.getDeclaredField("wrapperSession"); f.isAccessible = true; f.get(ks)
                }.getOrNull()
                Log.d(TAG, "bind: existingSession=$existingSession")
                if (existingSession == null) {
                    // pinRuntime 已在上面完成，先 patch serviceContent 再 start
                    patchServiceContent(ks, actualRuntime ?: runtime)
                    startKernelSession(ks, actualRuntime)
                } else {
                    Log.d(TAG, "bind: session already exists, initializing directly")
                    initExistingKernel(actualRuntime, ks)
                }
            }

            waitForSession(ks)
            reinitializeAfterLogin(actualRuntime)
            "ok"
        }.getOrElse { "failed: ${it.javaClass.simpleName}: ${it.message}" }
    }

    fun reinitializeAfterLogin(runtime: AppRuntime?): Boolean {
        cachedKs = null
        cachedMsgService = null
        cachedRecentService = null
        cachedBuddyService = null

        val coreReady = awaitCoreServices()
        if (!coreReady) {
            Log.w(TAG, "login reinitialize: core services unavailable")
            return false
        }

        runCatching {
            cachedMsgService?.switchForeGround(object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "login reinitialize: switchForeGround code=$code, errMsg=$errMsg")
                }
            })
            cachedMsgService?.startMsgSync()
            Log.d(TAG, "login reinitialize: startMsgSync called")
        }.onFailure { Log.w(TAG, "login reinitialize: message sync failed", it) }

        runCatching {
            val contactService = runtime?.getRuntimeService(IContactRuntimeService::class.java, "")
            contactService?.initUinToUidCache(true)
            Log.d(TAG, "login reinitialize: initUinToUidCache(true) called")
        }.onFailure { Log.w(TAG, "login reinitialize: contact cache init failed", it) }

        runCatching {
            cachedBuddyService?.getBuddyList(true, object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "login reinitialize: getBuddyList code=$code, errMsg=$errMsg")
                }
            })
            Log.d(TAG, "login reinitialize: getBuddyList(true) called")
        }.onFailure { Log.w(TAG, "login reinitialize: buddy refresh failed", it) }

        runCatching {
            val context = com.tencent.qphone.base.util.BaseApplication.getContext()
            context.sendBroadcast(
                android.content.Intent("com.tencent.mobileqq.action.ON_KERNEL_INIT_COMPLETE")
                    .setPackage(context.packageName)
            )
            Log.d(TAG, "login reinitialize: ON_KERNEL_INIT_COMPLETE sent")
        }.onFailure { Log.w(TAG, "login reinitialize: init broadcast failed", it) }

        return cachedMsgService != null && cachedRecentService != null
    }

    private fun loadNativeKernel() {
        runCatching { System.loadLibrary("kernel") }
            .onFailure { Log.e(TAG, "bind: loadLibrary(kernel) failed", it) }
    }

    /** 锁定 mAppRuntime 字段 + serviceContent 里的 runtime，
     *  防止 waitAppRuntime 创建新未初始化实例替换 */
    private fun pinRuntime(runtime: AppRuntime?) {
        // 1. MobileQQ.mAppRuntime
        runCatching {
            val f = MobileQQ::class.java.getDeclaredField("mAppRuntime")
            f.isAccessible = true
            val current = f.get(MobileQQ.sMobileQQ)
            if (current !== runtime) {
                f.set(MobileQQ.sMobileQQ, runtime)
                Log.d(TAG, "bind: pinned mAppRuntime: $current -> $runtime")
            }
            val stateField = MobileQQ::class.java.getDeclaredField("mRuntimeState")
            stateField.isAccessible = true
            (stateField.get(MobileQQ.sMobileQQ) as? java.util.concurrent.atomic.AtomicInteger)?.set(3)
        }.onFailure { Log.e(TAG, "bind: pinRuntime mAppRuntime failed", it) }

        // 2. KernelServiceImpl.serviceContent 里的 WeakReference<AppRuntime>
        runCatching {
            val ks = runtime?.getRuntimeService(IKernelService::class.java, "") ?: return@runCatching
            val ksImplCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelServiceImpl")
            val scField = ksImplCls.getDeclaredField("serviceContent"); scField.isAccessible = true
            val sc = scField.get(ks)
            if (sc != null) {
                val aField = sc.javaClass.getDeclaredField("a"); aField.isAccessible = true
                val weakRef = aField.get(sc)
                if (weakRef != null) {
                    setWeakRefReferent(weakRef, runtime)
                    Log.d(TAG, "bind: pinned serviceContent runtime -> $runtime")
                }
            }
        }.onFailure { Log.e(TAG, "bind: pinRuntime serviceContent failed", it) }
    }

    /** 在 ks.start() 之前 patch serviceContent WeakReference，
     *  确保 native startServlet 拿到已初始化的 runtime */
    private fun patchServiceContent(ks: IKernelService, runtime: AppRuntime?) {
        runCatching {
            val ksImplCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelServiceImpl")
            val scField = ksImplCls.getDeclaredField("serviceContent"); scField.isAccessible = true
            val sc = scField.get(ks) ?: return@runCatching
            val aField = sc.javaClass.getDeclaredField("a"); aField.isAccessible = true
            val weakRef = aField.get(sc) ?: return@runCatching
            setWeakRefReferent(weakRef, runtime)
            Log.d(TAG, "patchServiceContent: set runtime=$runtime, isLogin=${runtime?.isLogin()}, isRunning=${runtime?.isRunning}")
        }.onFailure { Log.e(TAG, "patchServiceContent failed", it) }
    }

    /** ART 上 WeakReference 继承 Object，referent 在 ART 内部类中，需要遍历所有字段 */
    private fun setWeakRefReferent(weakRef: Any, value: Any?) {
        var cls: Class<*>? = weakRef.javaClass
        while (cls != null) {
            val fields = runCatching { cls.declaredFields }.getOrNull()
            if (fields != null) {
                for (f in fields) {
                    if (f.name == "referent" || f.type == AppRuntime::class.java || f.type == Any::class.java) {
                        f.isAccessible = true
                        val current = f.get(weakRef)
                        if (current != null && current is AppRuntime) {
                            if (current !== value) {
                                f.set(weakRef, value)
                                Log.d(TAG, "setWeakRefReferent: patched field '${f.name}' in ${cls.simpleName}")
                            }
                            return
                        }
                    }
                }
            }
            cls = cls.superclass
        }
        Log.w(TAG, "setWeakRefReferent: could not find referent field")
    }

    private fun injectSAccountModule() {
        runCatching {
            val ksImplCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelServiceImpl")
            val sAccountModuleField = ksImplCls.getDeclaredField("sAccountModule")
            sAccountModuleField.isAccessible = true
            if (sAccountModuleField.get(null) == null) {
                val accountModuleCls = Class.forName("com.tencent.qqnt.watch.inject.AccountModuleInjector")
                val accountModule = accountModuleCls.getDeclaredConstructor().newInstance()
                sAccountModuleField.set(null, accountModule)
                Log.d(TAG, "bind: sAccountModule set to $accountModule")
            }
        }.onFailure { Log.e(TAG, "bind: set sAccountModule failed", it) }
    }

    private fun injectSAppSetting() {
        runCatching {
            val setterCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelSetterImpl")
            val field = setterCls.getDeclaredField("sAppSetting"); field.isAccessible = true
            if (field.get(null) == null) {
                val injector = Class.forName("com.tencent.qqnt.watch.inject.AppSettingInjector")
                    .getDeclaredConstructor()
                    .newInstance()
                field.set(null, injector)
                Log.d(TAG, "bind: official AppSettingInjector injected: $injector")
            }
        }.onFailure { Log.e(TAG, "bind: sAppSetting inject failed", it) }
    }

    private fun checkTicketStatus(runtime: AppRuntime?, uin: String) {
        runCatching {
            val ticketClass = Class.forName("com.tencent.qqnt.account.login.api.ITicketRuntimeService")
            val m = runtime?.javaClass?.methods?.firstOrNull {
                it.name == "getRuntimeService" && it.parameterTypes.size == 2
            }
            val ticketSvc = m?.invoke(runtime, ticketClass, "")
            Log.d(TAG, "bind: ticketSvc=$ticketSvc")
            if (ticketSvc != null) {
                val a2 = runCatching {
                    ticketSvc.javaClass.getMethod("getA2", String::class.java).invoke(ticketSvc, uin)
                }.getOrNull()
                Log.d(TAG, "bind: A2=$a2")
                val localTicket = runCatching {
                    ticketSvc.javaClass.getMethod("getLocalTicket", String::class.java, Int::class.javaPrimitiveType)
                        .invoke(ticketSvc, uin, 262144)
                }.getOrNull()
                Log.d(TAG, "bind: localTicket=$localTicket")
            }
        }.onFailure { Log.e(TAG, "bind: ticket check failed", it) }
    }

    private fun startKernelSession(ks: IKernelService, runtime: AppRuntime?) {
        val setter = runCatching {
            val cls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelSetterImpl")
            cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        }.onFailure { Log.e(TAG, "bind: create KernelSetterImpl failed", it) }.getOrNull() ?: return

        // mAppRef
        runCatching {
            val f = setter.javaClass.getDeclaredField("mAppRef"); f.isAccessible = true
            val weakRefClass = Class.forName("mqq.util.WeakReference")
            f.set(setter, weakRefClass.getDeclaredConstructor(Any::class.java).newInstance(runtime))
        }
        // ensureInject
        runCatching {
            setter.javaClass.getMethod("ensureInject").invoke(setter)
        }.onFailure { Log.e(TAG, "bind: ensureInject failed", it) }
        // sAppSetting 注入
        injectSAppSetting()

        // 创建 IKernelCreateListener，注册到 getAccountCallback
        val kernelCreateListener = java.lang.reflect.Proxy.newProxyInstance(
            Class.forName("com.tencent.qqnt.kernel.api.IKernelCreateListener").classLoader,
            arrayOf(Class.forName("com.tencent.qqnt.kernel.api.IKernelCreateListener"))
        ) { _, method, args ->
            when (method.name) {
                "a" -> { // onKernelCreate: 官方在此调用 setServletKernelInit
                    Log.d(TAG, "IKernelCreateListener.a called (kernel created)")
                    runCatching {
                        val setterCls = Class.forName("com.tencent.qqnt.kernel.api.impl.KernelSetterImpl")
                        val m = setterCls.getMethod("setServletKernelInit")
                        m.invoke(setter)
                        Log.d(TAG, "setServletKernelInit OK")
                    }.onFailure { Log.e(TAG, "setServletKernelInit failed", it) }
                    null
                }
                "b" -> { // onKernelInitComplete: 发送 ON_KERNEL_INIT_COMPLETE 广播
                    Log.d(TAG, "IKernelCreateListener.b called (kernel init complete)")
                    registerOfficialMsfConnectionBridge(runtime)
                    registerOfficialForegroundCallback(runtime)
                    initializeOfficialMessageBridge(runtime)
                    runCatching {
                        // initUinToUidCache (官方 KernelInitTask 在此调用)
                        val contactSvc = runCatching {
                            val app = MobileQQ.sMobileQQ
                            val rt = app?.let { a -> runCatching { a.waitAppRuntime(null) }.getOrNull() }
                            val m = rt?.javaClass?.methods?.firstOrNull {
                                it.name == "getRuntimeService" && it.parameterTypes.size == 2
                            }
                            m?.invoke(rt, Class.forName("com.tencent.qqnt.watch.contact.api.IContactRuntimeService"), "")
                        }.getOrNull()
                        Log.d(TAG, "initUinToUidCache: contactSvc=$contactSvc")
                        if (contactSvc != null) {
                            val m = contactSvc.javaClass.getMethod("initUinToUidCache", Boolean::class.javaPrimitiveType)
                            m.invoke(contactSvc, true) // true = fetch from server
                            Log.d(TAG, "initUinToUidCache(true) OK")
                        }
                    }.onFailure { Log.e(TAG, "initUinToUidCache failed", it) }

                    // 直接调 IBuddyService.getBuddyList(true, callback) 强制拉取
                    runCatching {
                        val ks = runCatching {
                            val app = MobileQQ.sMobileQQ
                            val rt = app?.let { a -> runCatching { a.waitAppRuntime(null) }.getOrNull() }
                            val m = rt?.javaClass?.methods?.firstOrNull {
                                it.name == "getRuntimeService" && it.parameterTypes.size == 2
                            }
                            m?.invoke(rt, IKernelService::class.java, "")
                        }.getOrNull() as? IKernelService
                        val buddySvc = ks?.buddyService
                        Log.d(TAG, "buddySvc=$buddySvc")
                        if (buddySvc != null) {
                            val callback = object : com.tencent.qqnt.kernel.nativeinterface.IOperateCallback {
                                override fun onResult(code: Int, errMsg: String?) {
                                    Log.d(TAG, "getBuddyList result: code=$code, errMsg=$errMsg")
                                }
                            }
                            buddySvc.getBuddyList(true, callback)
                            Log.d(TAG, "getBuddyList(true) called")
                        }
                    }.onFailure { Log.e(TAG, "getBuddyList direct failed", it) }

                    runCatching {
                        val ctx = com.tencent.qphone.base.util.BaseApplication.getContext()
                        val intent = android.content.Intent("com.tencent.mobileqq.action.ON_KERNEL_INIT_COMPLETE")
                            .setPackage(ctx.packageName)
                        ctx.sendBroadcast(intent)
                        Log.d(TAG, "ON_KERNEL_INIT_COMPLETE broadcast sent")
                    }.onFailure { Log.e(TAG, "sendBroadcast failed", it) }
                    null
                }
                "hashCode" -> 42
                "equals" -> false
                "toString" -> "QMCE-KernelCreateListener"
                else -> {
                    Log.d(TAG, "IKernelCreateListener: unexpected method=${method.name}")
                    null
                }
            }
        }
        runCatching {
            val getCallback = setter.javaClass.getMethod("getAccountCallback",
                Class.forName("com.tencent.qqnt.kernel.api.IKernelCreateListener"))
            val accountCallback = getCallback.invoke(setter, kernelCreateListener)
            Log.d(TAG, "bind: getAccountCallback returned=$accountCallback")
        }.onFailure { Log.e(TAG, "bind: getAccountCallback failed", it) }

        // 原路径: KernelSetterImpl.onAccountChanged(runtime) — 被包名校验拦截
        // getPackageName()="com.tencent.qqlite" vs getQQProcessName()="rj.qmce.litex" 不匹配
        // val changedOk = runCatching {
        //     val m = setter.javaClass.getMethod("onAccountChanged", mqq.app.AppRuntime::class.java)
        //     m.invoke(setter, runtime)
        // }.onFailure { Log.e(TAG, "bind: onAccountChanged failed", it) }.isSuccess
        // if (!changedOk) {
        //     Log.d(TAG, "bind: onAccountChanged failed, falling back to start()")
        //     runCatching { ks.start(null) }
        //         .onFailure { Log.e(TAG, "bind: start() failed", it) }
        // }

        // 直接调 KernelServiceImpl.start(listener)，绕过包名校验
        runCatching {
            ks.start(kernelCreateListener as IKernelCreateListener)
            Log.d(TAG, "bind: ks.start(listener) OK")
        }.onFailure { Log.e(TAG, "bind: ks.start(listener) failed", it) }
    }

    private fun registerOfficialForegroundCallback(runtime: AppRuntime?) {
        if (foregroundCallbackRegistered || runtime == null) return
        runCatching {
            runtime.getRuntimeService(IMsgPushForegroundApi::class.java, "")
        }.onSuccess { api ->
            if (api != null) {
                api.registerForegroundCallback()
                foregroundCallbackRegistered = true
                Log.d(TAG, "bind: official foreground callback registered api=$api")
            } else {
                Log.w(TAG, "bind: official foreground api unavailable")
            }
        }.onFailure { error ->
            Log.w(TAG, "bind: official foreground callback unavailable", error)
        }
    }

    private fun initializeOfficialMessageBridge(runtime: AppRuntime?) {
        if (runtime == null) return
        runCatching {
            val kernelService = runtime.getRuntimeService(IKernelService::class.java, "")
            val kernelMsgService = kernelService.getMsgService()
            val messageBridge = QRoute.api(com.tencent.qqnt.msg.api.IMsgService::class.java)
            messageBridge.init(kernelMsgService)
            Log.d(TAG, "bind: official message bridge initialized service=$messageBridge")
        }.onFailure { error ->
            Log.w(TAG, "bind: official message bridge initialization failed", error)
        }
    }

    private fun registerOfficialMsfConnectionBridge(runtime: AppRuntime?) {
        if (msfConnectionBridgeRegistered || runtime == null) return
        runCatching {
            val helper = QRoute.api(IMsfConnHelper::class.java)
            val listener = MsfConnectionListener(runtime)
            helper.initMsfConnPush()
            helper.addPushListener(listener)
            msfConnectionListener = listener
            msfConnectionBridgeRegistered = true
            Log.d(TAG, "bind: official MSF connection bridge registered helper=$helper")
        }.onFailure { error ->
            Log.w(TAG, "bind: official MSF connection bridge unavailable", error)
        }
    }

    private class MsfConnectionListener(private val runtime: AppRuntime) : com.tencent.qqnt.watch.mainframe.api.IMsfConnPushListener {
        override fun a() = Unit

        override fun b() = updateStatus(MsfStatusType.KCONNECTED)

        override fun c() = updateStatus(MsfStatusType.KDISCONNECTED)

        override fun d() = Unit

        private fun updateStatus(status: MsfStatusType) {
            runCatching {
                runtime.getRuntimeService(IKernelService::class.java, "")
                    .setOnMsfStatusChanged(status, MsfChangeReasonType.KAUTO, 0)
                Log.d(TAG, "msfBridge: status=$status")
            }.onFailure { error ->
                Log.w(TAG, "msfBridge: status=$status failed", error)
            }
        }
    }

    private fun waitForSession(ks: IKernelService?) {
        var waitCount = 0
        while (waitCount < 5) {
            Thread.sleep(500)
            waitCount++
            val ws = runCatching {
                val f = ks?.javaClass?.getDeclaredField("wrapperSession"); f?.isAccessible = true; f?.get(ks)
            }.getOrNull()
            if (ws != null) {
                Log.d(TAG, "bind: kernel session established after ${waitCount * 500}ms")
                if (ks != null) cacheServices(ks)
                replayForegroundToWrapperSession(ws)
                unblockPush()
                break
            }
        }
    }

    private fun replayForegroundToWrapperSession(session: Any) {
        val wrapperSession = session as? IQQNTWrapperSession
        if (wrapperSession == null) {
            Log.w(TAG, "bind: session foreground replay skipped; unexpected session=$session")
            return
        }
        if (foregroundReplayedSession === wrapperSession) {
            Log.d(TAG, "bind: session foreground replay already sent")
            return
        }
        val guardForeground = runCatching { GuardManager.c?.f() == true }
            .onFailure { Log.w(TAG, "bind: session foreground replay guard check failed", it) }
            .getOrDefault(false)
        val lifecycleForeground = runCatching { Foreground.isCurrentProcessForeground() }
            .onFailure { Log.w(TAG, "bind: session foreground replay lifecycle check failed", it) }
            .getOrDefault(false)
        if (!guardForeground && !lifecycleForeground) {
            Log.d(TAG, "bind: session foreground replay skipped; guard and lifecycle are background")
            return
        }
        runCatching { wrapperSession.switchToFront() }
            .onSuccess {
                foregroundReplayedSession = wrapperSession
                Log.i(
                    TAG,
                    "bind: replayed foreground to WrapperSession " +
                        "guard=$guardForeground lifecycle=$lifecycleForeground"
                )
            }
            .onFailure { Log.w(TAG, "bind: WrapperSession.switchToFront failed", it) }
    }

    /** 复用已有 wrapperSession 时，补做 IKernelCreateListener 回调里的关键初始化 */
    private fun initExistingKernel(runtime: AppRuntime?, ks: IKernelService) {
        Thread.sleep(500)
        cacheServices(ks)
        runCatching {
            val contactSvc = runtime?.getRuntimeService(IContactRuntimeService::class.java, "")
            Log.d(TAG, "initExistingKernel: contactSvc=$contactSvc")
            contactSvc?.initUinToUidCache(true)
            Log.d(TAG, "initExistingKernel: initUinToUidCache(true) OK")
        }.onFailure { Log.e(TAG, "initExistingKernel: initUinToUidCache failed", it) }

        runCatching {
            val buddySvc = ks.buddyService
            Log.d(TAG, "initExistingKernel: buddySvc=$buddySvc")
            buddySvc?.getBuddyList(true, object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "initExistingKernel: getBuddyList code=$code, errMsg=$errMsg")
                }
            })
            Log.d(TAG, "initExistingKernel: getBuddyList(true) called")
        }.onFailure { Log.e(TAG, "initExistingKernel: getBuddyList failed", it) }

        runCatching {
            val ctx = com.tencent.qphone.base.util.BaseApplication.getContext()
            ctx.sendBroadcast(
                android.content.Intent("com.tencent.mobileqq.action.ON_KERNEL_INIT_COMPLETE")
                    .setPackage(ctx.packageName)
            )
            Log.d(TAG, "initExistingKernel: ON_KERNEL_INIT_COMPLETE sent")
        }.onFailure { Log.e(TAG, "initExistingKernel: broadcast failed", it) }

        unblockPush()
    }

    private fun unblockPush() {
        runCatching {
            val msfServiceCls = Class.forName("com.tencent.mobileqq.msf.service.MsfService")
            val core = msfServiceCls.getDeclaredField("core").apply { isAccessible = true }.get(null)
            if (core != null) {
                val pm = core.javaClass.getDeclaredField("pushManager").apply { isAccessible = true }.get(core)
                if (pm != null) {
                    val oField = pm.javaClass.getDeclaredField("o")
                    oField.isAccessible = true
                    Log.d(TAG, "bind: PushManager.o before = ${oField.get(pm)}")
                    oField.set(pm, java.lang.Boolean.FALSE)
                    Log.d(TAG, "bind: PushManager.o set FALSE — push unblocked")
                }
            }
        }.onFailure { Log.e(TAG, "bind: unblock push failed", it) }
    }
}
