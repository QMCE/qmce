package rj.qmce.lite.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.qqnt.kernel.nativeinterface.GroupDetailInfo
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rj.qmce.lite.data.chat.GroupBulletinItem
import rj.qmce.lite.data.chat.GroupInfoRepository
import rj.qmce.lite.data.chat.GroupMemberRepository
import rj.qmce.lite.data.chat.GroupManagementRepository

data class GroupManagementState(
    val groupCode: Long = 0L,
    val detail: GroupDetailInfo? = null,
    val role: MemberRole? = null,
    val allMuted: Boolean = false,
    val members: List<GroupMemberRepository.Member> = emptyList(),
    val membersLoading: Boolean = false,
    val membersError: String? = null,
    val bulletins: List<GroupBulletinItem> = emptyList(),
    val bulletinLoading: Boolean = false,
    val bulletinSaving: Boolean = false,
    val bulletinError: String? = null,
    val bulletinMessage: String? = null,
    val memberActionUid: String? = null,
    val memberActionError: String? = null,
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
            membersLoading = true,
            bulletinLoading = true,
            error = null,
            membersError = null,
            bulletinError = null,
            bulletinMessage = null,
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

            GroupMemberRepository.load(groupCode, forceRefresh) { members, error ->
                _state.value = _state.value.copy(
                    members = members ?: _state.value.members,
                    membersLoading = false,
                    membersError = error,
                )
            }

            val bulletinResult = infoRepository.loadBulletin(groupCode)
            _state.value = _state.value.copy(
                bulletins = bulletinResult.getOrElse { _state.value.bulletins },
                bulletinLoading = false,
                bulletinError = bulletinResult.exceptionOrNull()?.message,
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

    fun kickMember(member: GroupMemberRepository.Member) {
        val before = _state.value
        if (before.memberActionUid != null) return
        if (!before.canManage) {
            _state.value = before.copy(memberActionError = "当前账号没有群管理权限")
            return
        }
        val uid = member.uid.trim()
        if (uid.isBlank()) {
            _state.value = before.copy(memberActionError = "成员 UID 不可用")
            return
        }
        _state.value = before.copy(memberActionUid = uid, memberActionError = null)
        val requested = GroupManagementRepository.kickMember(
            groupCode = before.groupCode,
            memberUid = uid,
            actorRole = before.role,
            targetRole = member.role,
        ) { success, message ->
            val latest = _state.value
            _state.value = if (success) {
                latest.copy(
                    members = latest.members.filterNot { it.uid == uid },
                    memberActionUid = null,
                    memberActionError = null,
                )
            } else {
                latest.copy(
                    memberActionUid = null,
                    memberActionError = message ?: "移出成员失败",
                )
            }
        }
        if (!requested) {
            _state.value = _state.value.copy(
                memberActionUid = null,
                memberActionError = "群管理服务不可用",
            )
        }
    }

    fun clearMemberActionError() {
        _state.value = _state.value.copy(memberActionError = null)
    }

    fun publishBulletin(text: String, oldFeedsId: String? = null, pinned: Boolean = false) {
        val before = _state.value
        if (before.bulletinSaving) return
        if (!before.canManage) {
            _state.value = before.copy(bulletinError = "当前账号没有发布群公告的权限")
            return
        }
        _state.value = before.copy(bulletinSaving = true, bulletinError = null, bulletinMessage = null)
        val requested = GroupManagementRepository.publishBulletin(
            groupCode = before.groupCode,
            oldFeedsId = oldFeedsId,
            text = text,
            pinned = pinned,
            role = before.role,
        ) { success, message ->
            val latest = _state.value
            if (success) {
                _state.value = latest.copy(
                    bulletinSaving = false,
                    bulletinError = null,
                    bulletinMessage = "群公告已提交，正在刷新…",
                )
                refreshBulletins()
            } else {
                _state.value = latest.copy(
                    bulletinSaving = false,
                    bulletinError = message ?: "发布群公告失败",
                    bulletinMessage = null,
                )
            }
        }
        if (!requested) {
            _state.value = _state.value.copy(
                bulletinSaving = false,
                bulletinError = "群公告服务不可用",
            )
        }
    }

    fun refreshBulletins() {
        val groupCode = _state.value.groupCode
        if (groupCode <= 0L) return
        _state.value = _state.value.copy(bulletinLoading = true, bulletinError = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = infoRepository.loadBulletin(groupCode)
            _state.value = _state.value.copy(
                bulletins = result.getOrElse { _state.value.bulletins },
                bulletinLoading = false,
                bulletinError = result.exceptionOrNull()?.message,
            )
        }
    }

    fun clearBulletinStatus() {
        _state.value = _state.value.copy(bulletinError = null, bulletinMessage = null)
    }
}
