package rj.qmce.lite

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ActivityOptions
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.multidex.MultiDex
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
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
import rj.qmce.lite.fix.LegacyKiller
import rj.qmce.lite.fix.PackageSignatureProvider
import rj.qmce.lite.fix.SignatureProbe
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
            if (reason !in forcedLogoutReasons) return
            clearExpiredLoginState()
            _logoutReason.value = reason
            Log.w("QMCE", "account: official logout reason=$reason")
        }
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
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
        private val forcedLogoutReasons = setOf(
            Constants.LogoutReason.expired,
            Constants.LogoutReason.forceLogout,
            Constants.LogoutReason.kicked,
            Constants.LogoutReason.secKicked,
            Constants.LogoutReason.suspend
        )
        private val loginRestartScheduled = AtomicBoolean(false)

        fun markLoginEstablished() {
            _logoutReason.value = null
        }

        /**
         * 登录完成后重启主进程，让 MobileQQ、KernelService 和 MsgService 从全新生命周期初始化。
         * 账号必须在调用前落盘；旧进程只负责安排启动并退出，不再尝试复用半初始化的 NT 对象。
         */
        fun restartAfterLogin(context: Context): Boolean {
            if (!loginRestartScheduled.compareAndSet(false, true)) return false
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                }
                ?: run {
                    loginRestartScheduled.set(false)
                    return false
                }
            val pendingOptions = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentCreatorBackgroundActivityStartMode(
                        if (Build.VERSION.SDK_INT >= 35) {
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                        } else {
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                        },
                    )
                }.toBundle()
            } else {
                null
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0x514D,
                launchIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                pendingOptions,
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: run {
                    loginRestartScheduled.set(false)
                    return false
                }
            val triggerAt = SystemClock.elapsedRealtime() + 1_500L
            val scheduled = runCatching {
                if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                }
            }.isSuccess
            if (!scheduled) {
                loginRestartScheduled.set(false)
                Log.e("QMCE", "login restart: failed to schedule alarm")
                return false
            }
            Log.i(
                "QMCE",
                "login restart: scheduled ${triggerAt - SystemClock.elapsedRealtime()}ms " +
                        "component=${launchIntent.component}",
            )
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { (context as? Activity)?.finishAndRemoveTask() }
                Process.killProcess(Process.myPid())
            }, 700L)
            return true
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
                Log.d(
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
                    Log.d(
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
        Log.d("QMCE", "attachBaseContext start")
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
            .onFailure { Log.e("QMCE", "emotion asset bridge failed", it) }
        super.attachBaseContext(base)
        MultiDex.install(this)
        Log.d("QMCE", "attachBaseContext done")
    }

    override fun onCreate() {
        Log.d("QMCE", "onCreate start")
        super.onCreate()
        Log.d("QMCE", "onCreate super done")
        AppCenter.start(
            this, "c67e55e2-35a3-4197-a7f6-633d41127b17",
            Analytics::class.java, Crashes::class.java
        )
        CrashCatcher.install(this)
        Log.d("QMCE", "crashcatcher init done")
        SignatureProbe.dump(this)
        // MMKVInitTask ：必须在 getLastLoginUin 等调用前完成
        synchronized(QMMKV::class.java) {
            if (!QMMKV.d) {
                QMMKV.e = MMKVHandlerImpl()
                runCatching {
                    MMKV.t(this)
                    MMKV.z(QMMKV.e)
                    MMKV.y(QMMKV.e)
                    QMMKV.d = true
                    Log.d("QMCE", "MMKV init OK")
                }.onFailure { Log.e("QMCE", "MMKV init failed", it) }
            }
        }
        if (isMainProcess()) {
            ensureRuntime(this)
            initializeOfficialImageRuntime()
            registerLogoutCallback()
        }
    }

    private fun initializeOfficialImageRuntime() {
        runCatching {
            val taskClass = Class.forName("com.tencent.qqnt.watch.startup.task.UrlDrawableInitTask")
            val task = taskClass.getDeclaredConstructor().newInstance()
            taskClass.getMethod("a", Context::class.java).invoke(task, this)
            Log.d("QMCE", "URLDrawable runtime initialized")
        }.onFailure {
            Log.w("QMCE", "URLDrawable runtime unavailable; emotion fallback remains enabled", it)
        }
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
        val runtime =
            sAppRuntime ?: runCatching { sMobileQQ?.peekAppRuntime() }.getOrNull()
        runCatching { runtime?.userLogoutReleaseData() }
            .onFailure { error -> Log.w("QMCE", "account: release runtime failed", error) }
        resetRuntimeAfterLogout()
        Log.d("QMCE", "account: cleared runtime and saved account")
    }

    private fun clearExpiredLoginState() {
        clearLocalLoginState()
    }

    private fun registerLogoutCallback() {
        sMobileQQ?.registerAccountCallback(logoutCallback)
        Log.d("QMCE", "account: logout callback registered")
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
                    c.startsWith("com.tencent.mobileqq.msf.core.auth.") ||
                    c.startsWith("com.tencent.mobileqq.msf.core.net.") ||
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
            Log.d(
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
            Log.d("QMCE", "createRuntime: new=$runtime (no old runtime or not logged in)")
        }
        return runtime
    }

    override fun getAppId(processName: String?): Int = 537243416
    override fun getAppId(): Int = 537243416

    override fun getCustomGuid(): ByteArray? = runCatching {
        val guid = com.tencent.mobileqq.utils.KidInfoUtil.getGuid(this)
        com.tencent.mobileqq.utils.HexUtil.c(guid)
    }.onFailure { error ->
        Log.w("QMCE", "getCustomGuid failed", error)
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
        return super.startService(fixIntent(service)!!)
    }

    override fun startForegroundService(service: Intent): android.content.ComponentName? {
        return super.startForegroundService(fixIntent(service)!!)
    }

    override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
        return super.bindService(fixIntent(service)!!, conn, flags)
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
