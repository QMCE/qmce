package rj.qmce.lite

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.multidex.MultiDex
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import com.tencent.mmkv.MMKV
import com.tencent.mobileqq.qmmkv.MMKVHandlerImpl
import com.tencent.mobileqq.qmmkv.QMMKV
import com.tencent.qqnt.watch.app.WatchAppInterface
import com.tencent.qqnt.watch.app.WatchApplicationDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import mqq.app.AppRuntime
import mqq.app.Constants
import mqq.app.IAccountCallback
import mqq.app.MobileQQ
import rj.qmce.lite.data.LoginPrefs
import rj.qmce.lite.data.emotion.EmotionAssetBridge
import rj.qmce.lite.data.emotion.EmotionRepository
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.update.OtaUpdateSession
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.fix.LegacyKiller
import rj.qmce.lite.fix.PackageSignatureProvider
import rj.qmce.lite.fix.SignatureProbe
import rj.qmce.lite.util.QmceLog
import rj.qmce.lite.viewmodel.SettingsViewModel
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess


@Suppress("SpellCheckingInspection")
class QmceApplication : WatchApplicationDelegate(), SingletonImageLoader.Factory {
    private val logoutCallback = object : IAccountCallback {
        override fun onAccountChangeFailed(runtime: AppRuntime?) = Unit

        override fun onAccountChanged(runtime: AppRuntime?) = Unit

        override fun onLogout(reason: Constants.LogoutReason?) {
            when (reason) {
                null,
                Constants.LogoutReason.user,
                Constants.LogoutReason.switchAccount,
                Constants.LogoutReason.restartProcess,
                Constants.LogoutReason.tips,
                Constants.LogoutReason.gray,
                -> {
                    QmceLog.d("QMCE", "account: ignore logout reason=$reason")
                    return
                }
                Constants.LogoutReason.kicked,
                Constants.LogoutReason.secKicked,
                Constants.LogoutReason.forceLogout,
                Constants.LogoutReason.expired,
                Constants.LogoutReason.suspend,
                -> {
                    // Bind/login can emit transitional forced-offline noise. Suppress only while
                    // the intentional login transition is active; after markLoginEstablished(),
                    // the same reasons still force UI back to QR.
                    if (loginTransitionActive.get()) {
                        QmceLog.w(
                            "QMCE",
                            "account: suppress transitional logout reason=$reason " +
                                "(login transition active)",
                        )
                        return
                    }
                }
            }
            forcedOfflineLatch.set(true)
            clearExpiredLoginState()
            _logoutReason.value = reason
            QmceLog.important("QMCE", "account: official logout reason=$reason")
        }
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.12)
                    .build()
            }
            .components {
                add(GifDecoder.Factory())
                add(OkHttpNetworkFetcherFactory())
            }
            .build()
    }

    companion object {
        var sAppRuntime: AppRuntime? = null
        private val _logoutReason = MutableStateFlow<Constants.LogoutReason?>(null)
        val logoutReason = _logoutReason.asStateFlow()

        /** Latched until the next intentional [beginLoginTransition]; blocks re-entering main. */
        private val forcedOfflineLatch = AtomicBoolean(false)

        private val loginTransitionActive = AtomicBoolean(false)

        fun beginLoginTransition() {
            forcedOfflineLatch.set(false)
            _logoutReason.value = null
            loginTransitionActive.set(true)
            QmceLog.d("QMCE", "account: login transition begin")
        }

        fun endLoginTransition() {
            loginTransitionActive.set(false)
            QmceLog.d("QMCE", "account: login transition end")
        }

        /**
         * Call only after bind/core ready when entering the logged-in UI.
         * Returns false if a forced offline (expired/kicked/…) already latched — caller must
         * keep [isLoggedIn] false.
         */
        fun markLoginEstablished(): Boolean {
            if (forcedOfflineLatch.get() || _logoutReason.value != null) {
                loginTransitionActive.set(false)
                QmceLog.w(
                    "QMCE",
                    "account: refuse markLoginEstablished " +
                        "(forcedOffline=${forcedOfflineLatch.get()} reason=${_logoutReason.value})",
                )
                return false
            }
            loginTransitionActive.set(false)
            QmceLog.important("QMCE", "account: login established")
            return true
        }

        fun isForcedOfflineLatched(): Boolean = forcedOfflineLatch.get()

        fun isLoginTransitionActive(): Boolean = loginTransitionActive.get()

        fun consumeLogoutReason() {
            _logoutReason.value = null
        }

        fun forceExit(context: Context) {
            Handler(Looper.getMainLooper()).post {
                runCatching { (context as? Activity)?.finishAndRemoveTask() }
                Process.killProcess(Process.myPid())
                exitProcess(0)
            }
        }

        fun resetRuntimeAfterLogout(app: MobileQQ? = sMobileQQ) {
            sAppRuntime = null
            app ?: return
            runCatching { app.setSortAccountList(emptyList()) }
            runCatching { app.lastLoginUin = "" }
            runCatching {
                val runtimeField = MobileQQ::class.java.getDeclaredField("mAppRuntime")
                runtimeField.isAccessible = true
                runtimeField.set(app, null)
            }
            runCatching {
                val stateField = MobileQQ::class.java.getDeclaredField("mRuntimeState")
                stateField.isAccessible = true
                (stateField.get(app) as? AtomicInteger)?.set(STATE_EMPTY)
            }
            runCatching {
                val ntInitUinField = MobileQQ::class.java.getDeclaredField("ntInitUin")
                ntInitUinField.isAccessible = true
                ntInitUinField.set(app, null)
            }
        }

        fun ensureRuntime(app: MobileQQ? = sMobileQQ): AppRuntime? {
            if (sAppRuntime != null) return sAppRuntime
            val mobile = app ?: return null
            if (BuildConfig.APPLICATION_ID != runCatching { mobile.qqProcessName }.getOrNull()) return null
            // 优先 waitAppRuntime — 它内部调 onCreate(Bundle) 设置 isRunning=true
            runCatching { mobile.waitAppRuntime() }.getOrNull()?.let {
                sAppRuntime = it
                QmceLog.d(
                    "QMCE",
                    "ensureRuntime: waitAppRuntime=$it, isRunning=${it.isRunning}, isLogin=${it.isLogin()}"
                )
                return it
            }
            // fallback: peekAppRuntime
            runCatching { mobile.peekAppRuntime() }.getOrNull()?.let {
                sAppRuntime = it
                return it
            }
            // 最后 createRuntime（不调 onCreate，isRunning 为 false，仅作兜底）
            if (mobile is QmceApplication) {
                val runtime = runCatching {
                    mobile.createRuntime(
                        mobile.qqProcessName,
                        false
                    )
                }.getOrNull()
                    ?: runCatching {
                        mobile.createRuntime(
                            BuildConfig.APPLICATION_ID,
                            false
                        )
                    }.getOrNull()
                if (runtime != null) {
                    sAppRuntime = runtime
                    runCatching {
                        val f = MobileQQ::class.java.getDeclaredField("mAppRuntime")
                        f.isAccessible = true
                        f.set(mobile, runtime)
                    }
                    runCatching {
                        val f = MobileQQ::class.java.getDeclaredField("mRuntimeState")
                        f.isAccessible = true
                        (f.get(mobile) as? AtomicInteger)?.set(3)
                    }
                    // 手动补 onCreate 让 isRunning=true
                    runCatching { runtime.onCreate(null) }
                    QmceLog.d(
                        "QMCE",
                        "ensureRuntime: createRuntime=$runtime, isRunning=${runtime.isRunning}, isLogin=${runtime.isLogin()}"
                    )
                    return runtime
                }
            }
            return null
        }
    }

    override fun attachBaseContext(base: Context) {
        QmceLog.d("QMCE", "attachBaseContext start")
        LegacyKiller.installForCurrentPackage(base)   // PM proxy for package name mapping (always needed)
        PackageSignatureProvider.install()                 // new CREATOR hook for IPC signature
        if (isMainProcess()) {
            setMainProcessName(BuildConfig.APPLICATION_ID)
            // getQQProcessName() reads processName field, not PACKAGE_NAME
            runCatching {
                val f = MobileQQ::class.java.getDeclaredField("processName")
                f.isAccessible = true
                f.set(null, BuildConfig.APPLICATION_ID)
            }
        }
        runCatching { EmotionAssetBridge.ensure(base) }
            .onFailure { QmceLog.e("QMCE", "emotion asset bridge failed", it) }
        super.attachBaseContext(base)
        MultiDex.install(this)
        QmceLog.d("QMCE", "attachBaseContext done")
    }

    override fun onCreate() {
        super.onCreate()
        SettingsViewModel.applyDiagnosticFlags(this)
        QmceLog.d("QMCE", "onCreate start")
        runCatching { KernelBridge.ensureEarlyNativeBootstrap() }
            .onFailure { QmceLog.e("QMCE", "early native bootstrap failed", it) }
        QmceLog.d("QMCE", "onCreate super done")
        AppCenter.start(
            this, "c67e55e2-35a3-4197-a7f6-633d41127b17",
            Analytics::class.java, Crashes::class.java
        )
        val appCenterOn = if (BuildConfig.DEBUG) {
            true
        } else {
            SettingsViewModel.isAppCenterReportingEnabled(this)
        }
        SettingsViewModel.applyAppCenterEnabled(appCenterOn)
        CrashCatcher.install(this)
        OtaUpdateSession.ensure(this)
        rj.qmce.lite.agent.AgentSubsystem.ensure(this)
        QmceLog.d("QMCE", "crashcatcher init done")
        if (BuildConfig.DEBUG) {
            SignatureProbe.dump(this)
        }
        // MMKVInitTask ：必须在 getLastLoginUin 等调用前完成
        synchronized(QMMKV::class.java) {
            if (!QMMKV.d) {
                QMMKV.e = MMKVHandlerImpl()
                runCatching {
                    MMKV.t(this)
                    MMKV.z(QMMKV.e)
                    MMKV.y(QMMKV.e)
                    QMMKV.d = true
                    QmceLog.d("QMCE", "MMKV init OK")
                }.onFailure { QmceLog.e("QMCE", "MMKV init failed", it) }
            }
        }
        if (isMainProcess()) {
            ensureRuntime(this)
            initializeOfficialImageRuntime()
            registerLogoutCallback()
            OfficialReportBridge.initialize(this)
        }
    }

    private fun initializeOfficialImageRuntime() {
        runCatching { System.loadLibrary("apng") }
            .onSuccess { QmceLog.d("QMCE", "libapng.so loaded") }
            .onFailure { QmceLog.w("QMCE", "libapng.so unavailable", it) }
        runCatching { System.loadLibrary("jlottie") }
            .onSuccess { QmceLog.d("QMCE", "libjlottie.so loaded") }
            .onFailure { QmceLog.w("QMCE", "libjlottie.so unavailable", it) }
        runCatching {
            val taskClass = Class.forName("com.tencent.qqnt.watch.startup.task.UrlDrawableInitTask")
            val task = taskClass.getDeclaredConstructor().newInstance()
            taskClass.getMethod("a", Context::class.java).invoke(task, this)
            QmceLog.d("QMCE", "URLDrawable runtime initialized")
        }.onFailure {
            QmceLog.w("QMCE", "URLDrawable runtime unavailable; emotion fallback remains enabled", it)
        }
        runCatching { EmotionRepository.warmupEmotionAssets() }
            .onFailure { QmceLog.w("QMCE", "emotion assets warmup schedule failed", it) }
    }

    private fun isMainProcess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            processName == BuildConfig.APPLICATION_ID
        } else (currentProcessNameByActivityThread
            ?: currentProcessNameByActivityManager
                ) == BuildConfig.APPLICATION_ID
    }

    /**
     * Get current process name.
     * Quicker than ActivityManager.
     */
    val currentProcessNameByActivityThread: String?
        @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
        get() = runCatching {
            val declaredMethod: Method = Class.forName(
                "android.app.ActivityThread",
                false,
                Application::class.java.classLoader
            ).getDeclaredMethod("currentProcessName")
            declaredMethod.isAccessible = true
            declaredMethod.invoke(null) as String
        }.getOrNull()

    /**
     * Get current process name.
     * Slowest.
     */
    val currentProcessNameByActivityManager: String
        get() {
            val pid: Int = Process.myPid()
            val am = this.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val runningAppList = am.runningAppProcesses
            for (processInfo in runningAppList) {
                if (processInfo.pid == pid) {
                    return processInfo.processName
                }
            }
            throw IllegalStateException("it is impossible")
        }

    fun clearLocalLoginState() {
        LoginPrefs.clear(this)
        runCatching {
            rj.qmce.lite.notify.QmceNotifyLifecycle.onLoggedOut(this)
        }.onFailure { QmceLog.w("QMCE", "notify lifecycle logout failed", it) }
        val runtime =
            sAppRuntime ?: runCatching { sMobileQQ?.peekAppRuntime() }.getOrNull()
        runCatching { runtime?.userLogoutReleaseData() }
            .onFailure { error -> QmceLog.w("QMCE", "account: release runtime failed", error) }
        resetRuntimeAfterLogout()
        KernelBridge.resetAfterLogout()
        endLoginTransition()
        QmceLog.d("QMCE", "account: cleared runtime and saved account")
    }

    private fun clearExpiredLoginState() {
        clearLocalLoginState()
    }

    private fun registerLogoutCallback() {
        sMobileQQ?.registerAccountCallback(logoutCallback)
        QmceLog.d("QMCE", "account: logout callback registered")
    }


    override fun getPackageName(): String {
        // Only spoof for QQ signature/apk-id related code. Global spoofing breaks AndroidX
        // provider discovery and MSF service binding because framework APIs then look for
        // components under com.tencent.qqlite instead of the installed package.
        return if (isOriginalPackageNameCaller()) "com.tencent.qqlite" else BuildConfig.APPLICATION_ID
    }

    private fun isOriginalPackageNameCaller(): Boolean {
        return Thread.currentThread().stackTrace.any { frame ->
            val c = frame.className
            c.startsWith("oicq.wlogin_sdk.") ||
                c.startsWith("com.tencent.mobileqq.msf.core.auth") ||
                c.startsWith("com.tencent.mobileqq.msf.core.net.") ||
                c.startsWith("com.tencent.turingfd.") ||
                c.startsWith("com.tencent.secprotocol.") ||
                c.startsWith("com.tencent.qimei.") ||
                c.startsWith("com.tencent.beacon.") ||
                c == "com.tencent.mobileqq.utils.KidInfoUtil" ||
                c.startsWith("com.tencent.mobileqq.utils.KidInfoUtil$") ||
                c == "com.tencent.mobileqq.utils.HexUtil" ||
                c.startsWith("com.tencent.mobileqq.utils.HexUtil$") ||
                c.contains("WtLogin") ||
                c.contains("wlogin") ||
                c == "rj.qmce.lite.fix.SignatureProbe"
        }
    }

    override fun createRuntime(processName: String?, readyNew: Boolean): AppRuntime? {
        // Keep apktool WatchApplicationDelegate semantics: only the main package process
        // owns WatchAppInterface. The :MSF process runs MsfService only; creating a
        // business runtime there pulls in unrelated app services and crashes.
        if (processName != BuildConfig.APPLICATION_ID) return null
        val oldRuntime = sAppRuntime
        val runtime = WatchAppInterface(this, processName)
        sAppRuntime = runtime
        // 新 runtime 自动继承旧 runtime 的登录态 — 出生即"活"
        if (oldRuntime != null && oldRuntime.isLogin()) {
            val uin = runCatching { oldRuntime.currentUin }.getOrNull()
            // 从 MobileQQ 拿 SimpleAccount（login() 需要）
            val account = runCatching {
                val m = sMobileQQ?.javaClass?.methods?.firstOrNull {
                    it.name == "getAccount" && it.parameterTypes.isEmpty()
                }
                m?.invoke(sMobileQQ) as? com.tencent.qphone.base.remote.SimpleAccount
            }.getOrNull()
            if (account != null) runCatching { runtime.login(account) }
            runCatching { runtime.setLogined() }
            // 不调 onCreate — caller（waitAppRuntime）会自己调，重复调会 addManager duplicated crash
            QmceLog.d(
                "QMCE",
                "createRuntime: adopted login uin=$uin, old=$oldRuntime -> new=$runtime, isLogin=${runtime.isLogin()}"
            )
            // 更新 mAppRuntime 字段
            runCatching {
                val f = MobileQQ::class.java.getDeclaredField("mAppRuntime")
                f.isAccessible = true
                f.set(sMobileQQ, runtime)
            }
            runCatching {
                val f = MobileQQ::class.java.getDeclaredField("mRuntimeState")
                f.isAccessible = true
                (f.get(sMobileQQ) as? AtomicInteger)?.set(3)
            }
        } else {
            QmceLog.d("QMCE", "createRuntime: new=$runtime (no old runtime or not logged in)")
        }
        return runtime
    }

    override fun getAppId(processName: String?): Int = 537282233
    override fun getAppId(): Int = 537282233

    override fun getCustomGuid(): ByteArray? = runCatching {
        val guid = com.tencent.mobileqq.utils.KidInfoUtil.getGuid(this)
        com.tencent.mobileqq.utils.HexUtil.c(guid)
    }.onFailure { error ->
        QmceLog.w("QMCE", "getCustomGuid failed", error)
    }.getOrNull()

    // QQ 代码构造的 intent ComponentName 用 com.tencent.qqlite，但实际装的是 rj.qmce.litex，
    // Android 找不到组件抛 SecurityException。拦截并修正包名。
    private fun fixIntent(intent: Intent?): Intent? {
        val cn = intent?.component ?: return intent
        if (cn.packageName == "com.tencent.qqlite") {
            intent.component =
                android.content.ComponentName(BuildConfig.APPLICATION_ID, cn.className)
        }
        return intent
    }

    override fun startService(service: Intent): android.content.ComponentName? {
        val fixed = fixIntent(service) ?: return null
        return super.startService(fixed)
    }

    override fun startForegroundService(service: Intent): android.content.ComponentName? {
        val fixed = fixIntent(service) ?: return null
        return super.startForegroundService(fixed)
    }

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
        val fixed = fixIntent(service) ?: return false
        return super.bindService(fixed, conn, flags)
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(receiver, filter, receiverExportFlag(filter))
        } else {
            super.registerReceiver(receiver, filter)
        }
    }

    override fun registerReceiver(
        receiver: BroadcastReceiver?,
        filter: IntentFilter,
        broadcastPermission: String?,
        scheduler: Handler?
    ): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            super.registerReceiver(
                receiver,
                filter,
                broadcastPermission,
                scheduler,
                receiverExportFlag(filter)
            )
        } else {
            super.registerReceiver(receiver, filter, broadcastPermission, scheduler)
        }
    }

    private fun receiverExportFlag(filter: IntentFilter): Int {
        val hasPlatformAction = (0 until filter.countActions()).any { index ->
            filter.getAction(index)?.startsWith("android.") == true
        }
        return if (hasPlatformAction) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED
    }

    override fun isUserAllow(): Boolean = true
}
