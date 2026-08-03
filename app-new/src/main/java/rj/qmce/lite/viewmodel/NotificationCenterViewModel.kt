package rj.qmce.lite.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rj.qmce.lite.data.notify.SharedNotifyRepositories
import rj.qmce.lite.data.notify.UiFriendRequest
import rj.qmce.lite.data.notify.UiGroupNotice
import rj.qmce.lite.kernel.KernelBridge

data class FriendNotifyState(
    val loading: Boolean = false,
    val items: List<UiFriendRequest> = emptyList(),
    val error: String? = null,
    val actingUid: String? = null,
    val actionMessage: String? = null,
)

data class GroupNotifyState(
    val loading: Boolean = false,
    val items: List<UiGroupNotice> = emptyList(),
    val error: String? = null,
    val actingSeq: Long? = null,
    val actionMessage: String? = null,
)

class NotificationCenterViewModel : ViewModel() {

    private val friendRepo = SharedNotifyRepositories.friendRepo
    private val groupRepo = SharedNotifyRepositories.groupRepo

    private val friendListener: (List<UiFriendRequest>) -> Unit = { items ->
        _friendState.update {
            it.copy(loading = false, items = items, error = null)
        }
    }
    private val groupListener: (List<UiGroupNotice>) -> Unit = { items ->
        _groupState.update {
            it.copy(loading = false, items = items, error = null)
        }
    }

    private val _friendState = MutableStateFlow(FriendNotifyState())
    val friendState: StateFlow<FriendNotifyState> = _friendState

    private val _groupState = MutableStateFlow(GroupNotifyState())
    val groupState: StateFlow<GroupNotifyState> = _groupState

    private var friendActive = false
    private var groupActive = false

    fun enterFriendRequests() {
        if (friendActive) return
        friendActive = true
        _friendState.value = FriendNotifyState(loading = true)
        SharedNotifyRepositories.addFriendListener(friendListener)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!KernelBridge.areCoreServicesReady()) {
                    KernelBridge.awaitCoreServices(timeoutMillis = 15_000)
                }
            }
            if (KernelBridge.getBuddyService() == null) {
                _friendState.update {
                    it.copy(
                        loading = false,
                        error = "好友服务暂不可用",
                        items = emptyList(),
                    )
                }
                return@launch
            }
            withContext(Dispatchers.IO) { friendRepo.start() }
            friendRepo.refresh()
        }
    }

    fun leaveFriendRequests() {
        if (!friendActive) return
        friendActive = false
        SharedNotifyRepositories.removeFriendListener(friendListener)
        // Do not stop shared repos — system notifier owns lifecycle.
        _friendState.value = FriendNotifyState()
    }

    fun enterGroupNotices() {
        if (groupActive) return
        groupActive = true
        _groupState.value = GroupNotifyState(loading = true)
        SharedNotifyRepositories.addGroupListener(groupListener)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (!KernelBridge.areCoreServicesReady()) {
                    KernelBridge.awaitCoreServices(timeoutMillis = 15_000)
                }
            }
            if (KernelBridge.getGroupService() == null) {
                _groupState.update {
                    it.copy(
                        loading = false,
                        error = "群服务暂不可用",
                        items = emptyList(),
                    )
                }
                return@launch
            }
            withContext(Dispatchers.IO) { groupRepo.start() }
            groupRepo.refresh()
        }
    }

    fun leaveGroupNotices() {
        if (!groupActive) return
        groupActive = false
        SharedNotifyRepositories.removeGroupListener(groupListener)
        _groupState.value = GroupNotifyState()
    }

    fun approveFriendRequest(uid: String, reqTime: Long, accept: Boolean) {
        _friendState.update {
            it.copy(actingUid = uid, actionMessage = null)
        }
        friendRepo.approve(uid, reqTime, accept) { success, errMsg ->
            _friendState.update { state ->
                state.copy(
                    actingUid = null,
                    actionMessage = when {
                        success -> if (accept) "已同意" else "已拒绝"
                        else -> errMsg ?: "操作失败"
                    },
                )
            }
            if (success) friendRepo.refresh()
        }
    }

    fun operateGroupNotice(notice: UiGroupNotice, accept: Boolean) {
        _groupState.update {
            it.copy(actingSeq = notice.seq, actionMessage = null)
        }
        groupRepo.operate(notice, accept) { success, errMsg ->
            _groupState.update { state ->
                state.copy(
                    actingSeq = null,
                    actionMessage = when {
                        success -> if (accept) "已同意" else "已拒绝"
                        else -> errMsg ?: "操作失败"
                    },
                )
            }
            if (success) groupRepo.refresh()
        }
    }

    override fun onCleared() {
        SharedNotifyRepositories.removeFriendListener(friendListener)
        SharedNotifyRepositories.removeGroupListener(groupListener)
        super.onCleared()
    }
}
