package rj.qmce.lite.data.notify

import android.util.Log
import com.tencent.qqnt.kernel.api.IBuddyService
import com.tencent.qqnt.kernel.nativeinterface.ApprovalBuddyRequest
import com.tencent.qqnt.kernel.nativeinterface.BuddyReq
import com.tencent.qqnt.kernel.nativeinterface.BuddyReqInfo
import com.tencent.qqnt.kernel.nativeinterface.BuddySetting
import com.tencent.qqnt.kernel.nativeinterface.BuddyVerify
import com.tencent.qqnt.kernel.nativeinterface.DelBuddyResult
import com.tencent.qqnt.kernel.nativeinterface.DoubtBuddyReqListRsp
import com.tencent.qqnt.kernel.nativeinterface.IKernelBuddyListener
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.ReqType
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat

data class UiFriendRequest(
    val uid: String,
    val nick: String,
    val avatarUrl: String,
    val message: String,
    val reqTime: Long,
    val reqType: Int,
    val pending: Boolean,
)

class ContactNotifyRepository(
    private val onListChanged: (List<UiFriendRequest>) -> Unit,
) : IKernelBuddyListener {

    companion object {
        private const val TAG = "QMCE-FriendNotify"
    }

    private val listenerLock = Any()
    private var buddyService: IBuddyService? = null
    private var listenerRegistered = false

    fun start() {
        val service = resolveBuddyService()
            ?: run {
                Log.w(TAG, "buddy service unavailable")
                onListChanged(emptyList())
                return
            }
        synchronized(listenerLock) {
            if (!listenerRegistered || buddyService !== service) {
                unregisterInternal()
                runCatching {
                    SdkCompat.addBuddyListener(service, this)
                    buddyService = service
                    listenerRegistered = true
                }.onFailure {
                    Log.w(TAG, "buddy listener registration failed", it)
                    onListChanged(emptyList())
                    return
                }
            }
        }
        runCatching {
            service.getBuddyReq(object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "getBuddyReq: code=$code errMsg=$errMsg")
                }
            })
            service.clearBuddyReqUnreadCnt(object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "clearBuddyReqUnreadCnt: code=$code")
                }
            })
        }.onFailure {
            Log.w(TAG, "fetch buddy requests failed", it)
        }
    }

    fun stop() {
        synchronized(listenerLock) {
            unregisterInternal()
        }
    }

    /** Re-fetch the buddy request list so the UI reflects approvals/decisions. */
    fun refresh() {
        val service = buddyService ?: KernelBridge.getBuddyService() ?: return
        runCatching {
            service.getBuddyReq(object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    Log.d(TAG, "buddy refresh: code=$code errMsg=$errMsg")
                }
            })
        }.onFailure {
            Log.w(TAG, "buddy refresh failed", it)
        }
    }

    fun approve(uid: String, reqTime: Long, accept: Boolean, callback: (Boolean, String?) -> Unit) {
        val service = buddyService ?: KernelBridge.getBuddyService()
        if (service == null) {
            callback(false, "好友服务不可用")
            return
        }
        val request = ApprovalBuddyRequest(
            uid,
            accept,
            "",
            if (reqTime > 0L) reqTime else System.currentTimeMillis(),
        )
        runCatching {
            service.approvalFriendRequest(request, object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    callback(code == 0, errMsg?.takeIf { it.isNotBlank() })
                }
            })
        }.onFailure {
            Log.w(TAG, "approvalFriendRequest failed", it)
            callback(false, it.message)
        }
    }

    override fun onBuddyReqChange(info: BuddyReqInfo) {
        val items = info.buddyReqs.orEmpty().mapNotNull(::mapBuddyReq)
        Log.d(TAG, "onBuddyReqChange: count=${items.size} unread=${info.unreadNums}")
        onListChanged(items)
    }

    override fun onBuddyReqUnreadCntChange(count: Int) = Unit

    override fun onAddBuddyNeedVerify(verify: BuddyVerify) = Unit
    override fun onAddMeSettingChanged(type: Int, settings: HashMap<String, String>) = Unit
    override fun onAvatarUrlUpdated(uid: String) = Unit
    override fun onBlockChanged(blockMap: HashMap<String, Boolean>) = Unit
    override fun onBuddyDeleted(uid: String) = Unit
    override fun onBuddyListChange(categories: ArrayList<com.tencent.qqnt.kernel.nativeinterface.BuddyCategory>) = Unit
    override fun onBuddyListChangedV2(changed: Boolean) = Unit
    override fun onBuddyRemarkUpdated(uid: String, remark: String) = Unit
    override fun onCheckBuddySettingResult(setting: BuddySetting) = Unit
    override fun onDelBatchBuddyInfos(results: ArrayList<DelBuddyResult>) = Unit
    override fun onDoubtBuddyReqChange(info: DoubtBuddyReqListRsp) = Unit
    override fun onDoubtBuddyReqUnreadNumChange(count: Int) = Unit
    override fun onNickUpdated(uid: String, nick: String) = Unit
    override fun onSmartInfos(uid: String, smartInfo: String, type: Int) = Unit
    override fun onSpacePermissionInfos(infos: HashMap<Long, Int>) = Unit

    private fun resolveBuddyService(): IBuddyService? {
        KernelBridge.getBuddyService()?.let { return it }
        KernelBridge.awaitCoreServices(timeoutMillis = 15_000)
        return KernelBridge.getBuddyService()
    }

    private fun unregisterInternal() {
        if (!listenerRegistered) return
        runCatching {
            buddyService?.let { SdkCompat.removeBuddyListener(it, this) }
        }
        buddyService = null
        listenerRegistered = false
    }

    private fun mapBuddyReq(req: BuddyReq): UiFriendRequest? {
        val uid = req.friendUid?.takeIf { it.isNotBlank() } ?: return null
        val reqType = normalizeReqType(req)
        val pending = !req.isDecide && !req.isInitiator &&
            reqType != ReqType.KMEAGREED.ordinal &&
            reqType != ReqType.KMEAGREEDANDADDED.ordinal &&
            reqType != ReqType.KMEREFUSED.ordinal &&
            reqType != ReqType.KMEIGNORED.ordinal
        return UiFriendRequest(
            uid = uid,
            nick = req.friendNick.orEmpty().ifBlank { uid },
            avatarUrl = req.friendAvatarUrl.orEmpty(),
            message = req.extWords.orEmpty(),
            reqTime = req.reqTime,
            reqType = reqType,
            pending = pending,
        )
    }

    private fun normalizeReqType(req: BuddyReq): Int {
        val rawType = req.reqType
        val isBuddy = req.isBuddy == true
        if (isBuddy &&
            (rawType == ReqType.KMESETQUESTION.ordinal || rawType == ReqType.KMEAGREEANYONE.ordinal)
        ) {
            return ReqType.KMEAGREEDANDADDED.ordinal
        }
        return rawType
    }
}
