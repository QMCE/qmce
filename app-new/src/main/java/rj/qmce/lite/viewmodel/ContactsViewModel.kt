package rj.qmce.lite.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.qqnt.kernel.api.IBuddyService
import com.tencent.qqnt.kernel.api.IGroupService
import com.tencent.qqnt.kernel.nativeinterface.BuddyListCategory
import com.tencent.qqnt.kernel.nativeinterface.BuddyListReqType
import com.tencent.qqnt.kernel.nativeinterface.BulletinFeedsDownloadInfo
import com.tencent.qqnt.kernel.nativeinterface.DataSource
import com.tencent.qqnt.kernel.nativeinterface.FirstGroupBulletinInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupAllInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupArkInviteStateInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupBulletin
import com.tencent.qqnt.kernel.nativeinterface.GroupBulletinListResult
import com.tencent.qqnt.kernel.nativeinterface.GroupDetailInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupExtInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupExtListUpdateType
import com.tencent.qqnt.kernel.nativeinterface.GroupListUpdateType
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberInfoListId
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberLevelInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupMemberListChangeInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupMsgMaskInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyMsg
import com.tencent.qqnt.kernel.nativeinterface.GroupNotifyTemplateItem
import com.tencent.qqnt.kernel.nativeinterface.GroupSimpleInfo
import com.tencent.qqnt.kernel.nativeinterface.GroupStatisticInfo
import com.tencent.qqnt.kernel.nativeinterface.IBuddyListCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupListener
import com.tencent.qqnt.kernel.nativeinterface.JoinGroupNotifyMsg
import com.tencent.qqnt.kernel.nativeinterface.MemberInfo
import com.tencent.qqnt.kernel.nativeinterface.RemindGroupBulletinMsg
import com.tencent.qqnt.watch.contact.api.IContactRuntimeService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mqq.app.AppRuntime
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import java.util.concurrent.atomic.AtomicInteger

class ContactsViewModel : ViewModel() {

    companion object {
        private const val TAG = "QMCE-Contacts"
        private const val GROUP_LIST_TIMEOUT_MS = 12_000L
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
        val sortId: Int,
        val onlineCount: Int,
        val buddies: List<UiBuddy>,
    )

    data class UiGroup(
        val groupCode: Long,
        val groupName: String,
        val memberCount: Int,
        val avatarUrl: String,
    )

    private val _categories = MutableStateFlow<List<UiCategory>>(emptyList())
    val categories: StateFlow<List<UiCategory>> = _categories

    private val _groups = MutableStateFlow<List<UiGroup>>(emptyList())
    val groups: StateFlow<List<UiGroup>> = _groups

    private val _groupsLoading = MutableStateFlow(false)
    val groupsLoading: StateFlow<Boolean> = _groupsLoading

    private val _groupsError = MutableStateFlow<String?>(null)
    val groupsError: StateFlow<String?> = _groupsError

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    @Volatile
    private var loaded = false
    private val loadGeneration = AtomicInteger()
    private val loadLock = Any()
    private var retryJob: Job? = null
    private var rawCategories: List<UiCategory> = emptyList()
    private var sortMode: String = SettingsViewModel.DEFAULT_CONTACTS_SORT_MODE

    private val groupListenerLock = Any()
    private var groupListenerService: IGroupService? = null
    private var groupListenerRegistered = false
    private var pendingGroupList: CompletableDeferred<List<UiGroup>>? = null

    private val groupListener = object : IKernelGroupListener {
        override fun onGroupListUpdate(type: GroupListUpdateType, groups: ArrayList<GroupSimpleInfo>) {
            pendingGroupList?.takeIf { !it.isCompleted }?.complete(mapGroups(groups))
        }

        override fun onGroupBulletinChange(groupCode: Long, bulletin: GroupBulletin) = Unit
        override fun onGroupDetailInfoChange(detail: GroupDetailInfo) = Unit
        override fun onGetGroupBulletinListResult(
            groupCode: Long,
            errorMessage: String,
            result: GroupBulletinListResult,
        ) = Unit

        override fun onGroupAdd(groupCode: Long) = Unit
        override fun onGroupAllInfoChange(info: GroupAllInfo) = Unit
        override fun onGroupArkInviteStateResult(groupCode: Long, info: GroupArkInviteStateInfo) = Unit
        override fun onGroupBulletinRemindNotify(groupCode: Long, info: RemindGroupBulletinMsg) = Unit
        override fun onGroupBulletinRichMediaDownloadComplete(info: BulletinFeedsDownloadInfo) = Unit
        override fun onGroupBulletinRichMediaProgressUpdate(info: BulletinFeedsDownloadInfo) = Unit
        override fun onGroupConfMemberChange(groupCode: Long, memberUids: ArrayList<String>) = Unit
        override fun onGroupExtListUpdate(type: GroupExtListUpdateType, infos: ArrayList<GroupExtInfo>) = Unit
        override fun onGroupFirstBulletinNotify(info: FirstGroupBulletinInfo) = Unit
        override fun onGroupNotifiesUnreadCountUpdated(
            isGroup: Boolean,
            groupCode: Long,
            count: Int,
        ) = Unit

        override fun onGroupNotifiesUpdated(
            isGroup: Boolean,
            notifies: ArrayList<GroupNotifyMsg>,
        ) = Unit

        override fun onGroupEssenceListChange(groupCode: Long) = Unit
        override fun onGroupListInited(inited: Boolean) = Unit
        override fun onGroupMemberLevelInfoChange(
            groupCode: Long,
            info: GroupMemberLevelInfo?,
        ) = Unit

        override fun onGroupNotifiesUnreadCountUpdatedV2(
            isGroup: Boolean,
            groupCode: Long,
            p2: Int,
            p3: Int,
            p4: Int,
            p5: Int,
        ) = Unit

        override fun onGroupNotifiesUpdatedV2(
            isGroup: Boolean,
            groupCode: Long,
            notifies: ArrayList<GroupNotifyMsg>?,
            templates: ArrayList<GroupNotifyTemplateItem>?,
        ) = Unit

        override fun onGroupSingleScreenNotifies(
            isGroup: Boolean,
            groupCode: Long,
            notifies: ArrayList<GroupNotifyMsg>,
        ) = Unit

        override fun onGroupSingleScreenNotifiesV2(
            isGroup: Boolean,
            groupCode: Long,
            p2: Long,
            p3: Boolean,
            p4: Int,
            notifies: ArrayList<GroupNotifyMsg>?,
            templates: ArrayList<GroupNotifyTemplateItem>?,
        ) = Unit

        override fun onGroupStatisticInfoChange(groupCode: Long, info: GroupStatisticInfo) = Unit
        override fun onGroupsMsgMaskResult(infos: ArrayList<GroupMsgMaskInfo>) = Unit
        override fun onJoinGroupNoVerifyFlag(groupCode: Long, first: Boolean, second: Boolean) = Unit
        override fun onJoinGroupNotify(info: JoinGroupNotifyMsg) = Unit
        override fun onMemberInfoChange(
            groupCode: Long,
            source: DataSource,
            members: HashMap<String, MemberInfo>,
        ) = Unit

        override fun onMemberListChange(info: GroupMemberListChangeInfo) = Unit
        override fun onSearchMemberChange(
            first: String,
            second: String,
            ids: ArrayList<GroupMemberInfoListId>,
            members: HashMap<String, MemberInfo>,
        ) = Unit

        override fun onShutUpMemberListChanged(
            groupCode: Long,
            members: ArrayList<MemberInfo>,
        ) = Unit
    }

    fun setSortMode(mode: String) {
        val normalized = mode.ifBlank { SettingsViewModel.DEFAULT_CONTACTS_SORT_MODE }
        if (sortMode == normalized) return
        sortMode = normalized
        _categories.value = applySort(rawCategories, sortMode)
    }

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

    fun markWaitingForKernel() {
        _statusText.value = "等待内核服务..."
        _loading.value = false
    }

    fun markKernelInitFailed() {
        _statusText.value = "内核服务初始化失败，稍后自动重试..."
        _loading.value = false
    }

    fun loadBuddies(runtime: AppRuntime?, forceRefresh: Boolean = false) {
        if (loaded && !forceRefresh) return
        if (_loading.value) return
        _loading.value = true
        _statusText.value = "加载联系人..."

        if (!KernelBridge.areCoreServicesReady()) {
            _statusText.value = "等待内核服务..."
            viewModelScope.launch(Dispatchers.IO) {
                val ready = KernelBridge.awaitCoreServices(
                    timeoutMillis = 30_000,
                    runtimeOverride = runtime,
                )
                if (!ready || KernelBridge.getBuddyService() == null) {
                    _statusText.value = "等待内核服务..."
                    _loading.value = false
                    scheduleRetry(runtime, "buddy-service-unavailable")
                    return@launch
                }
                val svc = KernelBridge.getBuddyService()
                if (svc != null) {
                    requestBuddyList(svc, runtime, forceRefresh)
                } else {
                    _statusText.value = "等待内核服务..."
                    _loading.value = false
                    scheduleRetry(runtime, "buddy-service-unavailable")
                }
            }
            return
        }

        val buddySvc = KernelBridge.getBuddyService()
        if (buddySvc != null) {
            requestBuddyList(buddySvc, runtime, forceRefresh)
            return
        }

        _statusText.value = "等待内核服务..."
        viewModelScope.launch(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + 30_000
            var svc = KernelBridge.getBuddyService()
            while (svc == null && System.currentTimeMillis() < deadline) {
                delay(300)
                KernelBridge.awaitCoreServices(timeoutMillis = 1_000, runtimeOverride = runtime)
                svc = KernelBridge.getBuddyService()
            }
            if (svc != null) {
                requestBuddyList(svc, runtime, forceRefresh)
            } else {
                _statusText.value = "等待内核服务..."
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
            buddySvc.getBuddyListV2("", forceRefresh, BuddyListReqType.KNOMAL, object : IBuddyListCallback {
                override fun onResult(
                    code: Int,
                    errMsg: String?,
                    list: java.util.ArrayList<BuddyListCategory>?,
                ) {
                    Log.d(TAG, "getBuddyListV2: code=$code, count=${list?.size}")
                    if (code == 0 && !list.isNullOrEmpty()) {
                        onBuddyCategories(list, buddySvc, runtime)
                    } else {
                        fallbackBuddyList(buddySvc, runtime, "v2-empty-or-error-$code")
                    }
                }
            })
        }.onFailure { error ->
            loaded = false
            _loading.value = false
            _statusText.value = "联系人加载失败，正在重试"
            Log.e(TAG, "getBuddyListV2 request failed", error)
            fallbackBuddyList(buddySvc, runtime, "request-${error.javaClass.simpleName}")
        }
    }

    private fun onBuddyCategories(
        list: java.util.ArrayList<BuddyListCategory>,
        buddySvc: IBuddyService,
        runtime: AppRuntime?,
    ) {
        loaded = true
        val generation = loadGeneration.incrementAndGet()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                launch { loadGroups(forceRefresh = true) }
                processCategoriesIncrementally(list, buddySvc, generation)
            }.onFailure { error ->
                loaded = false
                _loading.value = false
                _statusText.value = "联系人加载失败，正在重试"
                Log.e(TAG, "process buddy list failed", error)
                scheduleRetry(runtime, "process-${error.javaClass.simpleName}")
            }
        }
    }

    fun ensureGroupsLoaded() {
        if (_groups.value.isNotEmpty() || _groupsLoading.value) return
        refreshGroups()
    }

    fun refreshGroups() {
        if (_groupsLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            loadGroups(forceRefresh = true)
        }
    }

    private suspend fun loadGroups(forceRefresh: Boolean) {
        _groupsLoading.value = true
        _groupsError.value = null
        val service = KernelBridge.getGroupService()
            ?: KernelBridge.awaitGroupService()
            ?: run {
                Log.w(TAG, "group service unavailable")
                _groupsError.value = "群服务不可用"
                _groupsLoading.value = false
                return
            }
        if (!registerGroupListener(service)) {
            Log.w(TAG, "group listener registration failed")
            _groupsError.value = "群列表监听注册失败"
            _groupsLoading.value = false
            return
        }
        val pending = CompletableDeferred<List<UiGroup>>()
        pendingGroupList = pending
        try {
            service.getGroupList(forceRefresh, object : com.tencent.qqnt.kernel.nativeinterface.IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "getGroupList: code=$code, errMsg=$errMsg")
                    if (code != 0) {
                        pending.takeIf { !it.isCompleted }?.complete(emptyList())
                    }
                }
            })
            val groups = withTimeoutOrNull(GROUP_LIST_TIMEOUT_MS) {
                pending.await()
            }
            if (groups == null) {
                _groupsError.value = "加载群列表超时"
                if (_groups.value.isEmpty()) {
                    _groups.value = emptyList()
                }
                Log.w(TAG, "load groups timed out")
            } else {
                _groups.value = groups
                if (groups.isEmpty()) {
                    _groupsError.value = "暂无群聊"
                }
                Log.d(TAG, "loaded ${groups.size} groups")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "load groups failed", error)
            _groupsError.value = "加载群列表失败"
            if (_groups.value.isEmpty()) {
                _groups.value = emptyList()
            }
        } finally {
            pendingGroupList = null
            unregisterGroupListener()
            _groupsLoading.value = false
        }
    }

    private fun registerGroupListener(service: IGroupService): Boolean = synchronized(groupListenerLock) {
        if (groupListenerRegistered && groupListenerService === service) return@synchronized true
        if (groupListenerRegistered) {
            unregisterGroupListenerInternal()
        }
        runCatching {
            SdkCompat.addGroupListener(service, groupListener)
            groupListenerService = service
            groupListenerRegistered = true
            true
        }.onFailure {
            Log.w(TAG, "group listener registration failed", it)
        }.getOrDefault(false)
    }

    private fun unregisterGroupListener() {
        synchronized(groupListenerLock) {
            unregisterGroupListenerInternal()
        }
    }

    private fun unregisterGroupListenerInternal() {
        if (!groupListenerRegistered) return
        runCatching {
            groupListenerService?.let { SdkCompat.removeGroupListener(it, groupListener) }
        }
        groupListenerService = null
        groupListenerRegistered = false
    }

    private fun mapGroups(groups: ArrayList<GroupSimpleInfo>): List<UiGroup> {
        return groups.mapNotNull { info ->
            val code = info.groupCode
            if (code <= 0L) return@mapNotNull null
            val name = info.remarkName?.takeIf { it.isNotBlank() }
                ?: info.groupName?.takeIf { it.isNotBlank() }
                ?: code.toString()
            UiGroup(
                groupCode = code,
                groupName = name,
                memberCount = info.memberCount,
                avatarUrl = "https://p.qlogo.cn/gh/$code/$code/100",
            )
        }.sortedBy { it.groupName.lowercase() }
    }

    /**
     * V2 空/失败时先触发官方 getBuddyList(true) 预拉，再重试一次 V2；仍空则 scheduleRetry。
     */
    private fun fallbackBuddyList(
        buddySvc: IBuddyService,
        runtime: AppRuntime?,
        reason: String,
    ) {
        Log.d(TAG, "fallback getBuddyList(true), reason=$reason")
        _statusText.value = "联系人服务暂未返回数据，正在重试"
        runCatching {
            buddySvc.getBuddyList(true, object : com.tencent.qqnt.kernel.nativeinterface.IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "getBuddyList(true) fallback code=$code, errMsg=$errMsg")
                    runCatching {
                        buddySvc.getBuddyListV2(
                            "",
                            false,
                            BuddyListReqType.KNOMAL,
                            object : IBuddyListCallback {
                                override fun onResult(
                                    code: Int,
                                    errMsg: String?,
                                    list: java.util.ArrayList<BuddyListCategory>?,
                                ) {
                                    Log.d(
                                        TAG,
                                        "getBuddyListV2 after fallback: code=$code, count=${list?.size}",
                                    )
                                    if (code == 0 && !list.isNullOrEmpty()) {
                                        onBuddyCategories(list, buddySvc, runtime)
                                    } else {
                                        loaded = false
                                        _loading.value = false
                                        scheduleRetry(runtime, "fallback-empty-$code")
                                    }
                                }
                            },
                        )
                    }.onFailure { error ->
                        loaded = false
                        _loading.value = false
                        Log.e(TAG, "getBuddyListV2 after fallback failed", error)
                        scheduleRetry(runtime, "fallback-v2-${error.javaClass.simpleName}")
                    }
                }
            })
        }.onFailure { error ->
            loaded = false
            _loading.value = false
            Log.e(TAG, "getBuddyList(true) fallback failed", error)
            scheduleRetry(runtime, "fallback-${error.javaClass.simpleName}")
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
        val initialRecentList: List<com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo> =
            KernelBridge.getRecentContactService()
                ?.let { service -> runCatching { SdkCompat.getRecentContactFromCache(service, 1) }.getOrNull() }
                .orEmpty()
        val initialRecentByUid = initialRecentList.associateBy { it.peerUid }
        publishCategories(buildCategories(list, nickMap, initialRecentByUid, uinsByUid))
        _statusText.value = ""
        _loading.value = false

        val profileUins = runCatching {
            KernelBridge.getKernelService()
                ?.getProfileService()
                ?.getUinByUid("ContactRepo", ArrayList(allUids))
        }.getOrNull().orEmpty()
        profileUins.forEach { (uid, uin) ->
            if (uin > 0L) uinsByUid[uid] = uin
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
            val recentList: List<com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo> =
                KernelBridge.getRecentContactService()
                    ?.let { service -> runCatching { SdkCompat.getRecentContactFromCache(service, 1) }.getOrNull() }
                    .orEmpty()
            val recentByUid = recentList.associateBy { it.peerUid }
            if (uinsByUid.size != lastResolvedCount || lastResolvedCount == -1) {
                publishCategories(buildCategories(list, nickMap, recentByUid, uinsByUid))
                _statusText.value = ""
                lastResolvedCount = uinsByUid.size
                Log.d(TAG, "contacts avatars: resolved=${uinsByUid.size}/${allUids.size}")
            }
            _loading.value = false
            if (uinsByUid.size == allUids.size) return
            delay(500)
        }
    }

    private fun publishCategories(categories: List<UiCategory>) {
        rawCategories = categories
        _categories.value = applySort(rawCategories, sortMode)
    }

    private fun applySort(categories: List<UiCategory>, mode: String): List<UiCategory> {
        val sortedCategories = when (mode) {
            "name" -> categories.sortedBy { it.name.lowercase() }
            "online" -> categories.sortedWith(
                compareByDescending<UiCategory> { it.onlineCount > 0 }
                    .thenByDescending { it.onlineCount }
                    .thenBy { it.sortId },
            )
            else -> categories.sortedBy { it.sortId }
        }
        return if (mode == "name") {
            sortedCategories.map { category ->
                category.copy(
                    buddies = category.buddies.sortedBy { buddyDisplayName(it).lowercase() },
                )
            }
        } else {
            sortedCategories
        }
    }

    private fun buddyDisplayName(buddy: UiBuddy): String =
        buddy.remark.ifEmpty { buddy.nick }

    override fun onCleared() {
        synchronized(loadLock) {
            retryJob?.cancel()
            retryJob = null
        }
        pendingGroupList?.cancel()
        pendingGroupList = null
        unregisterGroupListener()
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
                    sortId = category.categorySortId,
                    onlineCount = category.onlineCount,
                    buddies = it,
                )
            }
        }
    }
}
