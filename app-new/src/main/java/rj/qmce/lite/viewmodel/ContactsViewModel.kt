package rj.qmce.lite.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.qqnt.kernel.api.IBuddyService
import com.tencent.qqnt.kernel.nativeinterface.BuddyListCategory
import com.tencent.qqnt.kernel.nativeinterface.BuddyListReqType
import com.tencent.qqnt.kernel.nativeinterface.IBuddyListCallback
import com.tencent.qqnt.watch.contact.api.IContactRuntimeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mqq.app.AppRuntime
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.kernel.KernelBridge
import java.util.concurrent.atomic.AtomicInteger

class ContactsViewModel : ViewModel() {

    companion object {
        private const val TAG = "QMCE-Contacts"
    }

    data class UiBuddy(
        val uid: String,
        val uin: Long,
        val nick: String,
        val remark: String,
        val avatarPath: String,
        val avatarUrls: List<String>,
        val categoryId: Int,
        val categoryName: String,
    )

    data class UiCategory(
        val id: Int,
        val name: String,
        val buddies: List<UiBuddy>,
    )

    private val _categories = MutableStateFlow<List<UiCategory>>(emptyList())
    val categories: StateFlow<List<UiCategory>> = _categories

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    @Volatile
    private var loaded = false
    private val loadGeneration = AtomicInteger()
    private val loadLock = Any()
    private var retryJob: Job? = null

    private fun scheduleRetry(runtime: AppRuntime?, reason: String) {
        if (runtime == null) return
        synchronized(loadLock) {
            if (retryJob?.isActive == true) return
            retryJob = viewModelScope.launch(Dispatchers.IO) {
                delay(2_000)
                synchronized(loadLock) { retryJob = null }
                if (!_loading.value && !loaded) {
                    Log.d(TAG, "retrying buddy list, reason=$reason")
                    loadBuddies(runtime, forceRefresh = true)
                }
            }
        }
    }

    fun loadBuddies(runtime: AppRuntime?, forceRefresh: Boolean = false) {
        if (loaded && !forceRefresh) return
        if (_loading.value) return
        _loading.value = true
        _statusText.value = "加载联系人..."

        val buddySvc = KernelBridge.getBuddyService()
        if (buddySvc != null) {
            requestBuddyList(buddySvc, runtime, forceRefresh)
            return
        }

        // Kernel 还没初始化完，轮询等待
        _statusText.value = "等待内核服务..."
        viewModelScope.launch(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + 15_000
            var svc = KernelBridge.getBuddyService()
            while (svc == null && System.currentTimeMillis() < deadline) {
                delay(300)
                svc = KernelBridge.getBuddyService()
            }
            if (svc != null) {
                requestBuddyList(svc, runtime, forceRefresh)
            } else {
                _statusText.value = "BuddyService 不可用"
                _loading.value = false
                scheduleRetry(runtime, "buddy-service-unavailable")
            }
        }
    }

    private fun requestBuddyList(
        buddySvc: IBuddyService,
        runtime: AppRuntime?,
        forceRefresh: Boolean,
    ) {
        runCatching {
            buddySvc.getBuddyListV2(forceRefresh, BuddyListReqType.KNOMAL, object : IBuddyListCallback {
                override fun onResult(
                    code: Int,
                    errMsg: String?,
                    list: java.util.ArrayList<BuddyListCategory>?
                ) {
                    Log.d(TAG, "getBuddyListV2: code=$code, count=${list?.size}")
                    if (code == 0 && !list.isNullOrEmpty()) {
                        loaded = true
                        val generation = loadGeneration.incrementAndGet()
                        viewModelScope.launch(Dispatchers.IO) {
                            runCatching {
                                processCategoriesIncrementally(list, buddySvc, generation)
                            }.onFailure { error ->
                                loaded = false
                                _loading.value = false
                                _statusText.value = "联系人加载失败，正在重试"
                                Log.e(TAG, "process buddy list failed", error)
                                scheduleRetry(runtime, "process-${error.javaClass.simpleName}")
                            }
                        }
                    } else {
                        loaded = false
                        _statusText.value = "联系人服务暂未返回数据，正在重试"
                        _loading.value = false
                        scheduleRetry(runtime, "empty-or-error-$code")
                    }
                }
            })
        }.onFailure { error ->
            loaded = false
            _loading.value = false
            _statusText.value = "联系人加载失败，正在重试"
            Log.e(TAG, "getBuddyListV2 request failed", error)
            scheduleRetry(runtime, "request-${error.javaClass.simpleName}")
        }
    }

    private suspend fun processCategoriesIncrementally(
        list: List<BuddyListCategory>,
        buddySvc: IBuddyService,
        generation: Int,
    ) {
        val allUids = list.flatMap { it.buddyUids }.distinct()
        val nickMap =
            runCatching { buddySvc.getBuddyNick(ArrayList(allUids)) }.getOrNull() ?: emptyMap()
        val uinsByUid = LinkedHashMap<String, Long>()
        var lastResolvedCount = -1

        delay(500)
        val initialRecentByUid = KernelBridge.getRecentContactService()
            ?.let { service -> runCatching { service.l(1) }.getOrNull() }
            .orEmpty()
            .associateBy { it.peerUid }
        _categories.value = buildCategories(list, nickMap, initialRecentByUid, uinsByUid)
        _statusText.value = ""
        _loading.value = false

        val profileUins = runCatching {
            KernelBridge.getKernelService()
                ?.profileService
                ?.getUinByUid("ContactRepo", ArrayList(allUids))
        }.getOrNull().orEmpty()
        profileUins.forEach { (uid, uin) ->
            if (uin != null && uin > 0L) uinsByUid[uid] = uin
        }
        Log.d(TAG, "contacts avatars: profile service resolved=${uinsByUid.size}/${allUids.size}")

        repeat(60) {
            if (generation != loadGeneration.get()) return
            val contactService = runCatching {
                QmceApplication.ensureRuntime()
                    ?.getRuntimeService(IContactRuntimeService::class.java, "")
            }.getOrNull()
            allUids.forEach { uid ->
                if (uid !in uinsByUid) {
                    contactService?.getUinByUid(uid)?.takeIf { it > 0L }
                        ?.let { uinsByUid[uid] = it }
                }
            }
            val recentByUid = KernelBridge.getRecentContactService()
                ?.let { service -> runCatching { service.l(1) }.getOrNull() }
                .orEmpty()
                .associateBy { it.peerUid }
            if (uinsByUid.size != lastResolvedCount || lastResolvedCount == -1) {
                _categories.value = buildCategories(list, nickMap, recentByUid, uinsByUid)
                _statusText.value = ""
                lastResolvedCount = uinsByUid.size
                Log.d(TAG, "contacts avatars: resolved=${uinsByUid.size}/${allUids.size}")
            }
            _loading.value = false
            if (uinsByUid.size == allUids.size) return
            delay(500)
        }
    }

    override fun onCleared() {
        synchronized(loadLock) {
            retryJob?.cancel()
            retryJob = null
        }
        super.onCleared()
    }

    private fun buildCategories(
        list: List<BuddyListCategory>,
        nickMap: Map<String, String>,
        recentByUid: Map<String?, com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo>,
        uinsByUid: Map<String, Long>,
    ): List<UiCategory> {
        return list.mapNotNull { category ->
            val buddies = category.buddyUids
                .asSequence()
                .filter { uid -> uid.isNotBlank() }
                .distinct()
                .map { uid ->
                    val uin = uinsByUid[uid] ?: 0L
                    val recent = recentByUid[uid]
                    val fallbackUrls = if (uin > 0L) listOf(
                        "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=100",
                        "https://q2.qlogo.cn/headimg_dl?dst_uin=$uin&spec=100",
                        "https://qlogo2.store.qq.com/qzone/$uin/$uin/100",
                    ) else emptyList()
                    UiBuddy(
                        uid = uid,
                        uin = uin,
                        nick = nickMap[uid]?.takeIf { it.isNotBlank() } ?: uid,
                        remark = "",
                        avatarPath = recent?.avatarPath.orEmpty(),
                        avatarUrls = listOfNotNull(recent?.avatarUrl?.takeIf { it.isNotBlank() }) + fallbackUrls,
                        categoryId = category.categoryId,
                        categoryName = category.categroyName.orEmpty(),
                    )
                }
                .toList()
            buddies.takeIf { it.isNotEmpty() }?.let {
                UiCategory(
                    id = category.categoryId,
                    name = category.categroyName.ifEmpty { "我的好友" },
                    buddies = it,
                )
            }
        }.sortedBy { it.id }
    }
}
