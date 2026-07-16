package rj.qmce.lite.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.qqnt.kernel.api.IProfileService
import com.tencent.qqnt.kernel.api.impl.ProfileService
import com.tencent.qqnt.kernel.nativeinterface.CoreInfo
import com.tencent.qqnt.kernel.nativeinterface.IKernelProfileListener
import com.tencent.qqnt.kernel.nativeinterface.StatusInfo
import com.tencent.qqnt.kernel.nativeinterface.UserDetailInfo
import com.tencent.qqnt.kernel.nativeinterface.UserSimpleInfo
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
    private val cache = ConcurrentHashMap<String, StatusInfo>()
    private val observers = CopyOnWriteArrayList<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false
    @Volatile
    private var selfUid: String = ""

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

    /** "手机在线" / "在线" / "离线" / null（未知） */
    fun describe(): String? {
        val s = cache[selfUid] ?: return null
        val d = s.termDesc
        if (!d.isNullOrEmpty()) return d
        return if (s.status != 0 && s.status != 20) "在线" else "离线"
    }

    fun start(profileService: IProfileService, uid: String) {
        if (started) return
        selfUid = uid
        try {
            profileService.H(Listener)
            val native = (profileService as? ProfileService)?.service
            native?.startStatusPolling(true)
            // 预热缓存
            val map = native?.getStatusInfo("qmce", arrayListOf(uid))
            if (!map.isNullOrEmpty()) merge(map)
            started = true
            Log.d(TAG, "OnlineStatus: started, uid=$uid")
        } catch (e: Exception) {
            Log.w(TAG, "OnlineStatus: start failed", e)
        }
    }

    private fun merge(map: HashMap<String, StatusInfo>) {
        for ((uid, info) in map) {
            if (!uid.isNullOrEmpty() && info != null) cache[uid] = info
        }
    }

    private fun notifyObservers() {
        mainHandler.post { observers.forEach { runCatching { it() } } }
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
