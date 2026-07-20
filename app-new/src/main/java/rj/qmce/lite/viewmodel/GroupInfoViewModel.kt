package rj.qmce.lite.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.qqnt.kernel.nativeinterface.GroupDetailInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rj.qmce.lite.data.chat.GroupBulletinItem
import rj.qmce.lite.data.chat.GroupInfoRepository

data class GroupInfoState(
    val groupCode: Long = 0L,
    val detail: GroupDetailInfo? = null,
    val bulletins: List<GroupBulletinItem> = emptyList(),
    val loading: Boolean = false,
    val bulletinLoading: Boolean = false,
    val error: String? = null,
    val bulletinError: String? = null,
)

class GroupInfoViewModel : ViewModel() {
    private val repository = GroupInfoRepository()
    private val _state = MutableStateFlow(GroupInfoState())
    val state: StateFlow<GroupInfoState> = _state

    private var loadedKey: Long? = null

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    fun load(groupCode: Long, forceRefresh: Boolean = false) {
        if (groupCode <= 0L) {
            _state.value = GroupInfoState(groupCode = groupCode, error = "群号无效")
            return
        }
        if (!forceRefresh && loadedKey == groupCode && _state.value.detail != null) return
        loadedKey = groupCode
        _state.value = _state.value.copy(
            groupCode = groupCode,
            loading = true,
            bulletinLoading = true,
            error = null,
            bulletinError = null,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val detailResult = repository.loadDetail(groupCode, forceRefresh)
            val bulletinResult = repository.loadBulletin(groupCode)
            _state.value = _state.value.copy(
                groupCode = groupCode,
                detail = detailResult.getOrNull() ?: _state.value.detail,
                bulletins = bulletinResult.getOrElse { _state.value.bulletins },
                loading = false,
                bulletinLoading = false,
                error = detailResult.exceptionOrNull()?.message,
                bulletinError = bulletinResult.exceptionOrNull()?.message,
            )
        }
    }
}
