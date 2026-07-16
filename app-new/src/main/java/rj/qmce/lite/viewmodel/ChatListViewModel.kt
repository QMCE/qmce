package rj.qmce.lite.viewmodel

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import com.tencent.qqnt.kernel.api.IKernelService
import com.tencent.qqnt.kernel.nativeinterface.AnchorPointContactInfo
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgListener
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactListChangedInfo
import com.tencent.qqnt.kernel.utils.RecentThreadDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import mqq.app.AppRuntime
import rj.qmce.lite.kernel.KernelBridge

class ChatListViewModel : ViewModel() {

    companion object {
        private const val TAG = "QMCE"
        private const val LIST_TYPE = 1 // main chat list
        private const val TARGET_PEER_UID = "749317302"
        private const val PULL_REFRESH_TIMEOUT_MS = 10_000L
        private val DEBUG_CONTACT_NAMES = listOf(
            "AgentRouter", "简幻云", "Atri Shop", "明日方舟", "终末地", "绝区零国际服交流群"
        )
    }

    data class ContactsSnapshot(
        val revision: Long,
        val contacts: List<RecentContactInfo>
    )

    private val _contacts = MutableStateFlow(ContactsSnapshot(0, emptyList()))
    val contacts: StateFlow<ContactsSnapshot> = _contacts

    private val _statusText = MutableStateFlow("加载中...")
    val statusText: StateFlow<String> = _statusText

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val cacheLock = Any()
    private val contactsById = LinkedHashMap<Long, RecentContactInfo>()
    private var sortedContactIds: List<Long> = emptyList()
    private var cacheRevision = 0L
    private val instanceId = System.identityHashCode(this)
    @Volatile
    private var running = false
    @Volatile
    private var loading = false
    private var workerThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recentService: com.tencent.qqnt.kernel.api.IRecentContactService? = null
    private var msgService: com.tencent.qqnt.kernel.api.IMsgService? = null
    private var syncProbeListener: IKernelMsgListener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Any()
    private var refreshSequence = 0L
    private var activeRefreshSequence: Long? = null
    private var activeRefreshStarted = false
    private var refreshTimeoutJob: Job? = null

    fun refreshContacts() {
        val refreshId = synchronized(refreshLock) {
            if (activeRefreshSequence != null) return@synchronized null
            refreshSequence += 1
            activeRefreshSequence = refreshSequence
            activeRefreshStarted = false
            refreshSequence
        }
        if (refreshId == null) return
        _isRefreshing.value = true

        val service = msgService ?: KernelBridge.getMsgService()
        if (service == null) {
            Log.w(TAG, "recentSync: pull-refresh skipped; MsgService unavailable")
            finishPullRefresh(refreshId, "msg-service-unavailable")
            return
        }

        scope.launch {
            armPullRefreshTimeout(refreshId)
            runCatching { service.startMsgSync() }
                .onSuccess { Log.d(TAG, "recentSync: startMsgSync pull-refresh") }
                .onFailure { error ->
                    Log.w(TAG, "recentSync: pull-refresh startMsgSync failed", error)
                    finishPullRefresh(refreshId, "start-failed")
                }
        }
    }

    private fun armPullRefreshTimeout(refreshId: Long) = synchronized(refreshLock) {
        if (activeRefreshSequence != refreshId) return@synchronized
        refreshTimeoutJob?.cancel()
        refreshTimeoutJob = scope.launch {
            delay(PULL_REFRESH_TIMEOUT_MS)
            finishPullRefresh(refreshId, "timeout")
        }
    }

    private fun finishPullRefresh(refreshId: Long, source: String) {
        val finished = synchronized(refreshLock) {
            if (activeRefreshSequence != refreshId) return@synchronized false
            activeRefreshSequence = null
            activeRefreshStarted = false
            refreshTimeoutJob?.cancel()
            refreshTimeoutJob = null
            true
        }
        if (finished) {
            _isRefreshing.value = false
            Log.d(TAG, "recentSync: pull-refresh finished source=$source")
        }
    }

    private fun finishActivePullRefresh(source: String) {
        val refreshId = synchronized(refreshLock) {
            activeRefreshSequence?.takeIf { activeRefreshStarted }
        } ?: return
        finishPullRefresh(refreshId, source)
    }

    private fun markActivePullRefreshStarted() {
        synchronized(refreshLock) {
            if (activeRefreshSequence != null) {
                activeRefreshStarted = true
            }
        }
    }

    private fun isDebugContact(contact: RecentContactInfo): Boolean {
        val name = contact.peerName.orEmpty()
        val remark = contact.remark.orEmpty()
        return DEBUG_CONTACT_NAMES.any { keyword ->
            name.contains(keyword) || remark.contains(
                keyword
            )
        }
    }

    private fun formatAbstract(contact: RecentContactInfo?): String =
        contact?.abstractContent?.joinToString(prefix = "[", postfix = "]") { element ->
            "type=${element.elementType},sub=${element.elementSubType}," +
                    "content=${element.content},custom=${element.customContent}," +
                    "emojiId=${element.emojiId},emojiType=${element.emojiType}"
        }.orEmpty()

    private fun logDebugContacts(source: String, contacts: Collection<RecentContactInfo>?) {
        contacts.orEmpty()
            .filter(::isDebugContact)
            .forEach { contact ->
                Log.d(
                    TAG,
                    "recentTrace[$source]: id=${contact.id}, contactId=${contact.contactId}, " +
                            "peerUid=${contact.peerUid}, peerUin=${contact.peerUin}, " +
                            "name=${contact.peerName}, remark=${contact.remark}, " +
                            "msgTime=${contact.msgTime}, sortField=${contact.sortField}, " +
                            "msgSeq=${contact.msgSeq}, unread=${contact.unreadCnt}, " +
                            "abstract=${formatAbstract(contact)}"
                )
            }
    }

    init {
        Log.d(TAG, "chatListVm[$instanceId]: created")
        scope.launch {
            RecentMessageStore.version.collect {
                Log.d(TAG, "RecentMessageStore updated, patching recent cache")
                patchSentMessages()
                publishCache("sent-message")
            }
        }
    }

    private fun patchSentMessages() = synchronized(cacheLock) {
        for (c in contactsById.values) {
            val id = c.id ?: continue
            val stored = RecentMessageStore.latest(id) ?: continue
            val text = stored.elements?.firstOrNull { it.elementType == 1 }
                ?.textElement?.content ?: ""
            if (text.isNotBlank()) {
                val el = com.tencent.qqnt.kernel.nativeinterface.MsgAbstractElement()
                el.content = text
                el.elementType = 1
                c.abstractContent = arrayListOf(el)
                // 如果 store 的时间更新，也更新 msgTime
                if (stored.msgTime > c.msgTime) {
                    c.msgTime = stored.msgTime
                }
                Log.d(TAG, "recentCache: patched sent message id=$id")
            }
        }
    }

    private fun replaceCache(contacts: Collection<RecentContactInfo>, source: String) =
        synchronized(cacheLock) {
            contactsById.clear()
            contacts.forEach { contact -> contactsById[contact.contactId] = contact }
            sortedContactIds = contacts
                .sortedByDescending { it.msgTime }
                .map { it.contactId }
            Log.d(TAG, "recentCache[$source]: replace=${contactsById.size}")
        }

    private fun applyRecentChange(info: RecentContactListChangedInfo) = synchronized(cacheLock) {
        val changed = info.changedList.orEmpty()
        if (info.notificationType == 1) {
            contactsById.clear()
        }
        changed.forEach { incoming ->
            val previous = contactsById[incoming.contactId]
            val hasAbstract = incoming.abstractContent?.any { !it.content.isNullOrBlank() } == true
            if (!hasAbstract && previous?.abstractContent != null) {
                if (isDebugContact(incoming)) {
                    Log.d(
                        TAG,
                        "recentCache[v2]: preserving previous abstract name=${incoming.peerName}, " +
                                "time=${previous.msgTime}->${incoming.msgTime}, seq=${previous.msgSeq}->${incoming.msgSeq}, " +
                                "incoming=${formatAbstract(incoming)}, previous=${
                                    formatAbstract(
                                        previous
                                    )
                                }"
                    )
                }
                incoming.abstractContent = previous.abstractContent
            } else if (isDebugContact(incoming)) {
                Log.d(
                    TAG,
                    "recentCache[v2]: accepting native abstract name=${incoming.peerName}, " +
                            "time=${previous?.msgTime}->${incoming.msgTime}, seq=${previous?.msgSeq}->${incoming.msgSeq}, " +
                            "incoming=${formatAbstract(incoming)}"
                )
            }
            contactsById[incoming.contactId] = incoming
        }
        val nativeOrder = info.sortedContactList.orEmpty()
        if (nativeOrder.isNotEmpty()) {
            sortedContactIds = nativeOrder
        } else if (info.notificationType == 1) {
            sortedContactIds =
                contactsById.values.sortedByDescending { it.msgTime }.map { it.contactId }
        }
        Log.d(
            TAG,
            "recentCache[v2]: type=${info.notificationType}, changed=${changed.size}, " +
                    "entities=${contactsById.size}, order=${sortedContactIds.size}"
        )
    }

    private fun cacheIsEmpty(): Boolean = synchronized(cacheLock) { contactsById.isEmpty() }

    private fun logTargetMessages(callback: String, messages: List<MsgRecord>?) {
        messages.orEmpty()
            .filter { it.peerUid == TARGET_PEER_UID }
            .forEach { message ->
                Log.d(
                    TAG,
                    "syncProbe: $callback peer=${message.peerUid}, time=${message.msgTime}, " +
                            "seq=${message.msgSeq}, id=${message.msgId}"
                )
            }
    }

    private fun installSyncProbe(
        msgService: com.tencent.qqnt.kernel.api.IMsgService,
        recentService: com.tencent.qqnt.kernel.api.IRecentContactService
    ) {
        if (syncProbeListener != null) return
        val listener = java.lang.reflect.Proxy.newProxyInstance(
            IKernelMsgListener::class.java.classLoader,
            arrayOf(IKernelMsgListener::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "onNtMsgSyncStart" -> {
                    Log.d(TAG, "syncProbe: onNtMsgSyncStart")
                    markActivePullRefreshStarted()
                    null
                }

                "onNtMsgSyncEnd" -> {
                    Log.d(TAG, "syncProbe: onNtMsgSyncEnd")
                    finishActivePullRefresh("nt-sync-end")
                    scope.launch {
                        delay(2_000)
                        val target = runCatching { recentService.l(LIST_TYPE) }
                            .getOrNull()
                            ?.firstOrNull { it.peerUid == TARGET_PEER_UID }
                        target?.let { logDebugContacts("sync-end+2s", listOf(it)) }
                    }
                    null
                }

                "onRecvMsg" -> {
                    @Suppress("UNCHECKED_CAST")
                    val messages = args?.getOrNull(0) as? List<MsgRecord>
                    logTargetMessages("onRecvMsg", messages)
                    null
                }

                "onMsgInfoListAdd", "onMsgInfoListUpdate" -> {
                    @Suppress("UNCHECKED_CAST")
                    val messages = args?.getOrNull(0) as? List<MsgRecord>
                    logTargetMessages(method.name, messages)
                    null
                }

                "onMsgAbstractUpdate" -> {
                    @Suppress("UNCHECKED_CAST")
                    val abstracts =
                        args?.getOrNull(0) as? List<com.tencent.qqnt.kernel.nativeinterface.MsgAbstract>
                    abstracts.orEmpty()
                        .filter { it.peer?.peerUid == TARGET_PEER_UID }
                        .forEach { abstract ->
                            Log.d(
                                TAG,
                                "syncProbe: onMsgAbstractUpdate peer=${abstract.peer?.peerUid}, " +
                                        "time=${abstract.abstractTime}, seq=${abstract.msgSeq}"
                            )
                        }
                    null
                }

                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "QMCE-SyncProbe"
                else -> null
            }
        } as IKernelMsgListener
        msgService.s(listener)
        this.msgService = msgService
        syncProbeListener = listener
        Log.d(TAG, "syncProbe: registered")
    }

    private fun publishCache(source: String) {
        val visible = synchronized(cacheLock) {
            val ordered = ArrayList<RecentContactInfo>(contactsById.size)
            val emittedIds = HashSet<Long>()
            sortedContactIds.forEach { id ->
                contactsById[id]?.let { contact ->
                    ordered += contact
                    emittedIds += id
                }
            }
            contactsById.values
                .filter { it.contactId !in emittedIds }
                .sortedByDescending { it.msgTime }
                .forEach(ordered::add)
            ordered
        }
        logDebugContacts("publish-$source", visible)
        _contacts.value = ContactsSnapshot(++cacheRevision, visible)
        _statusText.value = "${visible.size} 条会话"
        Log.d(
            TAG,
            "recentCache[$source]: publish=${visible.size}, revision=$cacheRevision, top3=${
                visible.take(3).map { "${it.id}:${it.msgTime}" }
            }"
        )
    }

    fun loadContacts(runtime: AppRuntime?) {
        if (loading) {
            Log.d(TAG, "chatPoll: already loading, skip")
            return
        }
        loading = true
        if (runtime == null) {
            _statusText.value = "Runtime 不可用"
            loading = false
            return
        }
        running = true
        workerThread = Thread {
            try {
                Log.d(TAG, "chatPoll: thread started")
                var ks = runCatching {
                    runtime.getRuntimeService(IKernelService::class.java, "")
                }.getOrNull()
                Log.d(TAG, "chatPoll: kernelService=$ks")
                if (ks == null) {
                    for (i in 0 until 30) {
                        if (!running) return@Thread
                        Thread.sleep(1000)
                        ks = runCatching {
                            runtime.getRuntimeService(IKernelService::class.java, "")
                        }.getOrNull()
                        Log.d(TAG, "chatPoll: kernelService retry $i=$ks")
                        if (ks != null) break
                    }
                    if (ks == null) {
                        mainHandler.post { _statusText.value = "IKernelService 不可用" }
                        return@Thread
                    }
                }
                val kernelService = ks!!

                // 等 isNTStartFinish=true，否则 recentService 永远返回 null
                runCatching {
                    val ksImplCls =
                        Class.forName("com.tencent.qqnt.kernel.api.impl.KernelServiceImpl")
                    val field = ksImplCls.getDeclaredField("isNTStartFinish"); field.isAccessible =
                    true
                    val atomicBool = field.get(kernelService)
                    val getMethod = atomicBool.javaClass.getMethod("get")
                    for (i in 0 until 60) {
                        if (!running) return@Thread
                        val ready = getMethod.invoke(atomicBool) as Boolean
                        if (ready) {
                            Log.d(TAG, "chatPoll: isNTStartFinish=true after ${i}s"); break
                        }
                        Log.d(TAG, "chatPoll: isNTStartFinish=false, waiting... $i")
                        Thread.sleep(1000)
                    }
                }.onFailure { Log.e(TAG, "chatPoll: wait isNTStartFinish failed", it) }

                val rc = KernelBridge.getRecentContactService()
                    ?: runCatching { kernelService.recentContactService }.getOrNull()
                Log.d(TAG, "chatPoll: recentService=$rc")
                var recentService = rc
                if (recentService == null) {
                    for (i in 0 until 30) {
                        if (!running) return@Thread
                        Thread.sleep(1000)
                        recentService =
                            runCatching { kernelService.recentContactService }.getOrNull()
                        Log.d(TAG, "chatPoll: recentService retry $i=$recentService")
                        if (recentService != null) break
                    }
                }
                Log.d(TAG, "chatPoll: recentService final=$recentService")
                this@ChatListViewModel.recentService = recentService
                if (recentService == null) {
                    mainHandler.post { _statusText.value = "IRecentContactService 不可用" }
                    return@Thread
                }

                // 1. 先读本地缓存 (official: l(listType))
                val cached = runCatching { recentService.l(LIST_TYPE) }.getOrNull()
                Log.d(TAG, "chatPoll: cached(l(1))=${cached?.size}")
                if (cached != null && cached.isNotEmpty()) {
                    logDebugContacts("cached", cached)
                    replaceCache(cached, "cached")
                    patchSentMessages()
                    publishCache("cached")
                }

                // 2. 立即注册 listeners（不等 buddy init，官方也是这样）
                // 2a. 注册 V2 listener (official: w(listType, listener))
                // 所有更新统一走 RecentThreadDispatcher，和官方一致
                val recentContactListener: (RecentContactListChangedInfo) -> Unit = { info ->
                    Log.d(
                        TAG,
                        "v2Listener: type=${info.notificationType}, changed=${info.changedList?.size}, sorted=${info.sortedContactList?.size}, listType=${info.listType}"
                    )
                    logDebugContacts("v2-changed(type=${info.notificationType})", info.changedList)
                    RecentThreadDispatcher.a.a {
                        applyRecentChange(info)
                        patchSentMessages()
                        publishCache("v2")
                    }
                }
                recentService.w(LIST_TYPE, recentContactListener)
                Log.d(TAG, "chatPoll: v2Listener registered")

                // 通知内核我们正在看会话列表 → 开启实时推送（官方 MiniMsgFragment.onCreateView 调用）
                val enterInfo = com.tencent.qqnt.kernel.nativeinterface.EnterOrExitMsgListInfo(7, 1)
                recentService.enterOrExitMsgList(enterInfo, object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        Log.d(TAG, "enterOrExitMsgList(enter) code=$code, errMsg=$errMsg")
                    }
                })

                // 3. 主动拉取（V2 listener 已就绪）
                val anchor = AnchorPointContactInfo()
                Log.d(TAG, "chatPoll: calling v() to fetch contacts")
                recentService.v(anchor, true, LIST_TYPE, 200, object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        Log.d(TAG, "chatPoll: v() result code=$code, errMsg=$errMsg")
                    }
                })

                val msgService = KernelBridge.getMsgService()
                    ?: run {
                        // 等 cacheServices 完成（initExistingKernel 里 500ms sleep 之后）
                        for (i in 0 until 10) {
                            Thread.sleep(500)
                            val svc = KernelBridge.getMsgService()
                            if (svc != null) return@run svc
                        }
                        null
                    }
                if (msgService == null) {
                    Log.w(TAG, "recentSync: IMsgService unavailable")
                } else {
                    msgService.switchForeGround(object : IOperateCallback {
                        override fun onResult(code: Int, errMsg: String?) {
                            Log.d(TAG, "recentSync: switchForeGround code=$code, errMsg=$errMsg")
                        }
                    })
                    installSyncProbe(msgService, recentService)
                    runCatching { msgService.startMsgSync() }
                        .onSuccess { Log.d(TAG, "recentSync: startMsgSync initial") }
                        .onFailure { Log.w(TAG, "recentSync: initial startMsgSync failed", it) }
                    scope.launch {
                        while (running) {
                            delay(120_000)
                            if (!running) break
                            runCatching { msgService.startMsgSync() }
                                .onSuccess { Log.d(TAG, "recentSync: startMsgSync periodic") }
                                .onFailure {
                                    Log.w(
                                        TAG,
                                        "recentSync: periodic startMsgSync failed",
                                        it
                                    )
                                }
                        }
                    }
                }

                // 4b. 尝试 native getRecentContactListSync via reflection
                runCatching {
                    val nativeSvc = runCatching {
                        val f =
                            recentService.javaClass.getDeclaredField("nativeService"); f.isAccessible =
                        true; f.get(recentService)
                    }.getOrNull() ?: runCatching {
                        val f =
                            recentService.javaClass.getDeclaredField("mService"); f.isAccessible =
                        true; f.get(recentService)
                    }.getOrNull()
                    Log.d(TAG, "chatPoll: nativeService=$nativeSvc")
                    if (nativeSvc != null) {
                        val syncMethod =
                            nativeSvc.javaClass.methods.firstOrNull { it.name == "getRecentContactListSync" }
                        Log.d(TAG, "chatPoll: getRecentContactListSync method=$syncMethod")
                        if (syncMethod != null) {
                            val result = syncMethod.invoke(nativeSvc)
                            Log.d(TAG, "chatPoll: getRecentContactListSync result=$result")
                            if (result != null) {
                                val changedField =
                                    result.javaClass.getDeclaredField("changedList"); changedField.isAccessible =
                                    true
                                val contacts = changedField.get(result) as? java.util.ArrayList<*>
                                Log.d(
                                    TAG,
                                    "chatPoll: getRecentContactListSync contacts=${contacts?.size}"
                                )
                            }
                        }
                    }
                }.onFailure { Log.e(TAG, "chatPoll: native sync failed", it) }

                // 用 getRecentContactListSnapShot 获取实际数据
                val snapshotCallback =
                    object : com.tencent.qqnt.kernel.nativeinterface.IKernelRecentSnapShotCallback {
                        override fun onResult(
                            code: Int,
                            errMsg: String?,
                            info: com.tencent.qqnt.kernel.nativeinterface.CompleteRecentContactInfo?
                        ) {
                            Log.d(
                                TAG,
                                "chatPoll: snapShot code=$code, errMsg=$errMsg, changed=${info?.changedList?.size}, sorted=${info?.sortedContactList?.size}"
                            )
                            if (cacheIsEmpty() && code == 0 && info != null) {
                                val contacts = info.changedList
                                if (contacts != null && contacts.isNotEmpty()) {
                                    logDebugContacts("snapshot-initial", contacts)
                                    replaceCache(contacts, "snapshot-initial")
                                    patchSentMessages()
                                    publishCache("snapshot-initial")
                                }
                            }
                        }
                    }

                // 轮询等待内核数据就绪
                for (i in 0 until 60) {
                    if (!running) return@Thread
                    // 尝试通过 snapShot 获取
                    val got = java.util.concurrent.atomic.AtomicBoolean(false)
                    recentService.getRecentContactListSnapShot(
                        LIST_TYPE,
                        object :
                            com.tencent.qqnt.kernel.nativeinterface.IKernelRecentSnapShotCallback {
                            override fun onResult(
                                code: Int,
                                errMsg: String?,
                                info: com.tencent.qqnt.kernel.nativeinterface.CompleteRecentContactInfo?
                            ) {
                                Log.d(
                                    TAG,
                                    "chatPoll: snapShot[$i] code=$code, changed=${info?.changedList?.size}, sorted=${info?.sortedContactList?.size}"
                                )
                                if (cacheIsEmpty() && code == 0 && info?.changedList != null && info.changedList.isNotEmpty()) {
                                    logDebugContacts("snapshot-poll[$i]", info.changedList)
                                    replaceCache(info.changedList, "snapshot-poll[$i]")
                                    patchSentMessages()
                                    publishCache("snapshot-poll[$i]")
                                    got.set(true)
                                }
                            }
                        })
                    Thread.sleep(500)
                    if (got.get()) break
                    // 也检查 V2 listener 是否已经填充了 localCache
                    if (!cacheIsEmpty()) break
                    if (i % 5 == 0) Log.d(TAG, "chatPoll: poll $i, still empty, retrying v()")
                    if (i > 0 && i % 5 == 0) {
                        recentService.v(AnchorPointContactInfo(), true, LIST_TYPE, 200, null)
                    }
                    Thread.sleep(500)
                }
                if (cacheIsEmpty()) {
                    Log.d(TAG, "chatPoll: 60s poll exhausted, no contacts")
                    mainHandler.post { _statusText.value = "暂无会话" }
                }

                // 4. 后台触发 buddy init + getBuddyList（不阻塞，不影响已有 listeners）
                runCatching {
                    val contactClass =
                        Class.forName("com.tencent.qqnt.watch.contact.api.IContactRuntimeService")
                    val contactSvc = runCatching {
                        val m = runtime.javaClass.methods.firstOrNull {
                            it.name == "getRuntimeService" && it.parameterTypes.size == 2
                        }
                        m?.invoke(runtime, contactClass, "")
                    }.getOrNull()
                    if (contactSvc != null) {
                        val buddyCallbackClass =
                            Class.forName("com.tencent.qqnt.watch.contact.api.IContactRuntimeService\$BuddyInitCallback")
                        val buddyCallback = java.lang.reflect.Proxy.newProxyInstance(
                            buddyCallbackClass.classLoader,
                            arrayOf(buddyCallbackClass)
                        ) { _, method, _ ->
                            when (method.name) {
                                "u" -> {
                                    Log.d(TAG, "chatPoll: BuddyInitCallback.u() fired!"); null
                                }

                                else -> null
                            }
                        }
                        contactSvc.javaClass.getMethod(
                            "listenerBuddyInitFinish",
                            buddyCallbackClass
                        )
                            .invoke(contactSvc, buddyCallback)
                    }
                }.onFailure { Log.d(TAG, "chatPoll: buddy init skipped: ${it.message}") }

                runCatching {
                    val buddySvc = runCatching { kernelService.buddyService }.getOrNull()
                    if (buddySvc != null) {
                        val nativeSvc = runCatching {
                            val m = buddySvc.javaClass.getMethod("getService"); m.invoke(buddySvc)
                        }.getOrNull()
                        nativeSvc?.javaClass?.methods?.firstOrNull {
                            it.name == "getBuddyList" && it.parameterTypes.size == 2
                        }?.invoke(nativeSvc, true, object : IOperateCallback {
                            override fun onResult(code: Int, errMsg: String?) {
                                Log.d(TAG, "chatPoll: getBuddyList result: code=$code")
                            }
                        })
                    }
                }.onFailure { Log.d(TAG, "chatPoll: getBuddyList skipped: ${it.message}") }
            } finally {
                loading = false
                Log.d(TAG, "chatPoll: thread finished; loading released")
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    override fun onCleared() {
        Log.w(TAG, "chatListVm[$instanceId]: onCleared")
        running = false
        scope.cancel()
        workerThread?.interrupt()
        syncProbeListener?.let { listener -> runCatching { msgService?.g(listener) } }
        syncProbeListener = null
        msgService = null
        // 通知内核离开会话列表（官方 MiniMsgFragment.onDestroy 调用）
        runCatching {
            recentService?.let { svc ->
                val exitInfo = com.tencent.qqnt.kernel.nativeinterface.EnterOrExitMsgListInfo(7, 2)
                svc.enterOrExitMsgList(exitInfo, object : IOperateCallback {
                    override fun onResult(code: Int, errMsg: String?) {
                        Log.d(TAG, "enterOrExitMsgList(exit) code=$code")
                    }
                })
            }
        }
    }
}
