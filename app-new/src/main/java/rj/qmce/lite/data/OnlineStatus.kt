package rj.qmce.lite.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.qqnt.kernel.api.IProfileService
import com.tencent.qqnt.kernel.api.impl.ProfileService
import com.tencent.qqnt.kernel.nativeinterface.CoreInfo
import com.tencent.qqnt.kernel.nativeinterface.IKernelProfileListener
import com.tencent.qqnt.kernel.nativeinterface.IKernelProfileService
import com.tencent.qqnt.kernel.nativeinterface.StatusInfo
import com.tencent.qqnt.kernel.nativeinterface.UserDetailInfo
import com.tencent.qqnt.kernel.nativeinterface.UserSimpleInfo
import rj.qmce.lite.kernel.SdkCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "QMCE"

/**
 * 在线状态缓存。只关心自己的状态（selfUid）。
 *
 * 1. start() 注册 IKernelProfileListener + startStatusPolling(true)
 * 2. onStatusUpdate / onSelfStatusChanged 收到推送后 merge 进 cache
 * 3. UI 通过 addObserver 注册回调，在 UI 线程收到通知后刷新
 */
object OnlineStatus {
    enum class TermKind {
        Phone,
        Computer,
        Tablet,
        Watch,
        Unknown,
    }

    private val cache = ConcurrentHashMap<String, StatusInfo>()
    private val observers = CopyOnWriteArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false
    @Volatile
    private var selfUid: String = ""
    @Volatile
    private var nativeService: IKernelProfileService? = null

    fun addObserver(cb: () -> Unit) {
        if (cb !in observers) observers.add(cb)
    }

    fun removeObserver(cb: () -> Unit) {
        observers.remove(cb)
    }

    /** 已有缓存记录（区分"未初始化"和"离线"） */
    fun known(): Boolean = selfUid.isNotEmpty() && cache.containsKey(selfUid)

    fun isOnline(): Boolean {
        val s = cache[selfUid] ?: return false
        return s.status != 0 && s.status != 20
    }

    fun statusInfo(): StatusInfo? = cache[selfUid]

    /** "手机在线" / "在线" / "离线" / null（未知） */
    fun describe(): String? {
        val s = cache[selfUid] ?: return null
        val d = s.termDesc
        if (!d.isNullOrEmpty()) return d
        return if (s.status != 0 && s.status != 20) "在线" else "离线"
    }

    /** 主终端类型；离线返回 null。 */
    fun termKind(): TermKind? {
        val s = cache[selfUid] ?: return null
        if (s.status == 0 || s.status == 20) return null
        return kindForTerm(s.termType, s.iconType, s.termDesc)
    }

    fun start(profileService: IProfileService, uid: String) {
        if (started && selfUid == uid) {
            refreshStatusInfo()
            return
        }
        selfUid = uid
        try {
            SdkCompat.addProfileListener(profileService, Listener)
            val native = readNativeProfileService(profileService)
            nativeService = native
            native?.startStatusPolling(true)
            started = true
            refreshStatusInfo()
            // 登录后短时重试，缩短首屏等待
            mainHandler.postDelayed({ refreshStatusInfo() }, 400L)
            mainHandler.postDelayed({ refreshStatusInfo() }, 1_200L)
            Log.d(TAG, "OnlineStatus: started, uid=$uid")
        } catch (e: Throwable) {
            Log.w(TAG, "OnlineStatus: start failed", e)
        }
    }

    fun refreshStatusInfo() {
        val uid = selfUid
        val native = nativeService
        if (uid.isEmpty() || native == null) return
        runCatching {
            val map = native.getStatusInfo("qmce", arrayListOf(uid))
            if (!map.isNullOrEmpty()) {
                merge(map)
                notifyObservers()
            }
        }.onFailure {
            Log.w(TAG, "OnlineStatus: getStatusInfo failed", it)
        }
    }

    private fun readNativeProfileService(profileService: IProfileService): IKernelProfileService? {
        return runCatching {
            profileService.javaClass.getMethod("getService").invoke(profileService) as? IKernelProfileService
        }.getOrNull() ?: runCatching {
            (profileService as? ProfileService)?.service
        }.getOrNull()
    }

    private fun merge(map: HashMap<String, StatusInfo>) {
        for ((uid, info) in map) {
            if (!uid.isNullOrEmpty()) cache[uid] = info
        }
    }

    private fun notifyObservers() {
        mainHandler.post { observers.forEach { runCatching { it() } } }
    }

    private fun kindForTerm(termType: Int, iconType: Int, termDesc: String?): TermKind {
        val desc = termDesc.orEmpty()
        when {
            desc.contains("手表") || desc.contains("Watch", ignoreCase = true) ->
                return TermKind.Watch
            desc.contains("平板") || desc.contains("Pad", ignoreCase = true) ||
                desc.contains("iPad", ignoreCase = true) ->
                return TermKind.Tablet
            desc.contains("电脑") || desc.contains("PC", ignoreCase = true) ||
                desc.contains("Windows", ignoreCase = true) ||
                desc.contains("Mac", ignoreCase = true) ->
                return TermKind.Computer
            desc.contains("手机") || desc.contains("Phone", ignoreCase = true) ->
                return TermKind.Phone
        }
        return when (termType) {
            1, 6 -> TermKind.Computer
            2 -> TermKind.Phone
            3, 4, 5 -> TermKind.Tablet
            8, 9 -> TermKind.Watch
            else -> when (iconType) {
                1, 6 -> TermKind.Computer
                2 -> TermKind.Phone
                3, 4, 5 -> TermKind.Tablet
                8, 9 -> TermKind.Watch
                else -> TermKind.Phone
            }
        }
    }

    private object Listener : IKernelProfileListener {
        override fun onStatusUpdate(map: HashMap<String, StatusInfo>?) {
            if (!map.isNullOrEmpty()) {
                merge(map); notifyObservers()
            }
        }

        override fun onStatusAsyncFieldUpdate(map: HashMap<String, StatusInfo>?) {
            if (!map.isNullOrEmpty()) {
                merge(map); notifyObservers()
            }
        }

        override fun onSelfStatusChanged(statusInfo: StatusInfo?) {
            if (statusInfo != null && selfUid.isNotEmpty()) {
                cache[selfUid] = statusInfo
                notifyObservers()
            }
        }

        override fun onProfileSimpleChanged(map: HashMap<String, UserSimpleInfo>?) {}
        override fun onStrangerRemarkChanged(map: HashMap<String, CoreInfo>?) {}
        override fun onUserDetailInfoChanged(userDetailInfo: UserDetailInfo?) {}
    }
}
