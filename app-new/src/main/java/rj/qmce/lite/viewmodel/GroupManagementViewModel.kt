package rj.qmce.lite.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.qqnt.kernel.nativeinterface.GroupDetailInfo
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rj.qmce.lite.data.chat.GroupInfoRepository
import rj.qmce.lite.data.chat.GroupManagementRepository

data class GroupManagementState(
    val groupCode: Long = 0L,
    val detail: GroupDetailInfo? = null,
    val role: MemberRole? = null,
    val allMuted: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canManage: Boolean
        get() = role == MemberRole.OWNER || role == MemberRole.ADMIN

    val roleLabel: String
        get() = when (role) {
            MemberRole.OWNER -> "群主"
            MemberRole.ADMIN -> "管理员"
            MemberRole.MEMBER -> "成员"
            MemberRole.STRANGER -> "陌生人"
            else -> "未知权限"
        }
}

class GroupManagementViewModel : ViewModel() {
    private val infoRepository = GroupInfoRepository()
    private val _state = MutableStateFlow(GroupManagementState())
    val state: StateFlow<GroupManagementState> = _state

    private var loadedKey: Long? = null

    fun load(groupCode: Long, forceRefresh: Boolean = false) {
        if (groupCode <= 0L) {
            _state.value = GroupManagementState(groupCode = groupCode, error = "群号无效")
            return
        }
        if (!forceRefresh && loadedKey == groupCode && _state.value.detail != null) return
        loadedKey = groupCode
        _state.value = _state.value.copy(
            groupCode = groupCode,
            loading = true,
            error = null,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val result = infoRepository.loadDetail(groupCode, forceRefresh)
            val detail = result.getOrNull()
            _state.value = _state.value.copy(
                groupCode = groupCode,
                detail = detail ?: _state.value.detail,
                role = detail?.cmdUinPrivilege ?: _state.value.role,
                allMuted = detail?.shutUpAllTimestamp?.let { it > (System.currentTimeMillis() / 1000).toInt() }
                    ?: _state.value.allMuted,
                loading = false,
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    fun toggleAllMuted(enabled: Boolean) {
        val before = _state.value
        if (before.busy || !before.canManage) return
        _state.value = before.copy(busy = true, error = null)
        val requested = GroupManagementRepository.setAllMuted(
            groupCode = before.groupCode,
            enabled = enabled,
            role = before.role,
        ) { success, message ->
            val latest = _state.value
            _state.value = if (success) {
                latest.copy(allMuted = enabled, busy = false, error = null)
            } else {
                latest.copy(
                    allMuted = before.allMuted,
                    busy = false,
                    error = message?.takeIf { it.isNotBlank() } ?: "设置全员禁言失败",
                )
            }
        }
        if (!requested) {
            _state.value = _state.value.copy(
                allMuted = before.allMuted,
                busy = false,
                error = "群管理服务不可用",
            )
        }
    }
}
