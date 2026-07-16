package rj.qmce.lite.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.UserSimpleInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.kernel.KernelBridge
import java.io.File

class MyViewModel : ViewModel() {
    companion object {
        private const val TAG = "QMCE-My"
    }

    data class Profile(
        val uin: String,
        val nickname: String,
        val signature: String,
        val avatarPath: String,
        val avatarUrls: List<String>,
        val isLoggedIn: Boolean,
        val refreshing: Boolean = false,
    )

    private val _profile = MutableStateFlow(Profile("", "QQ用户", "", "", emptyList(), false))
    val profile: StateFlow<Profile> = _profile.asStateFlow()

    private val _operationStatus = MutableStateFlow("")
    val operationStatus: StateFlow<String> = _operationStatus.asStateFlow()

    fun load(uin: String, forceRefresh: Boolean = false) {
        if (uin.isBlank()) return
        if (!forceRefresh && _profile.value.uin == uin && _profile.value.avatarUrls.isNotEmpty()) return
        _profile.value = _profile.value.copy(
            uin = uin,
            nickname = _profile.value.nickname.takeUnless { it == "QQ用户" } ?: uin,
            avatarUrls = avatarUrls(uin),
            isLoggedIn = QmceApplication.ensureRuntime()?.isLogin() == true,
            refreshing = true,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val selfProfileService = KernelBridge.getSelfProfileService()
            val localNickname = selfProfileService?.getCurrentAccountNickName(uin).orEmpty()
            val localAvatarPath = selfProfileService?.getCurrentAccountAvatarPath(uin).orEmpty()
            val profileService = KernelBridge.getKernelService()?.profileService
            val uid = profileService
                ?.getUidByUin("QMCE-My", arrayListOf(uin.toLongOrNull() ?: 0L))
                ?.values
                ?.firstOrNull()
                .orEmpty()
            val info = profileService?.getCoreAndBaseInfo("QMCE-My", arrayListOf(uid))?.get(uid)
            applyProfile(uin, localNickname, localAvatarPath, info)

            if (forceRefresh) {
                runCatching {
                    profileService?.getUserSimpleInfo(
                        true,
                        arrayListOf(uid.ifBlank { uin }),
                        object : IOperateCallback {
                            override fun onResult(code: Int, errMsg: String?) {
                                Log.d(TAG, "profile refresh: code=$code, errMsg=$errMsg")
                            }
                        },
                    )
                }.onFailure { error -> Log.w(TAG, "profile refresh request failed", error) }
                Thread.sleep(700)
                val refreshed =
                    profileService?.getCoreAndBaseInfo("QMCE-My", arrayListOf(uid))?.get(uid)
                applyProfile(
                    uin = uin,
                    localNickname = selfProfileService?.getCurrentAccountNickName(uin).orEmpty(),
                    localAvatarPath = selfProfileService?.getCurrentAccountAvatarPath(uin)
                        .orEmpty(),
                    info = refreshed ?: info,
                )
            }
        }
    }

    fun syncMessages(chatListVm: ChatListViewModel) {
        _operationStatus.value = "正在同步消息…"
        chatListVm.refreshContacts()
        _operationStatus.value = "已请求消息同步"
    }

    fun clearChatCache() {
        val storageService = KernelBridge.getKernelService()?.storageCleanService
        if (storageService == null) {
            _operationStatus.value = "缓存服务暂不可用"
            return
        }
        _operationStatus.value = "正在清理聊天缓存…"
        runCatching {
            storageService.clearAllChatCacheInfo(object : IOperateCallback {
                override fun onResult(code: Int, errMsg: String?) {
                    _operationStatus.value =
                        if (code == 0) "聊天缓存已清理" else "清理失败: ${errMsg.orEmpty()}"
                    Log.d(TAG, "clear chat cache: code=$code, errMsg=$errMsg")
                }
            })
        }.onFailure { error ->
            _operationStatus.value = "清理失败: ${error.javaClass.simpleName}"
            Log.w(TAG, "clear chat cache failed", error)
        }
    }

    fun clearOperationStatus() {
        _operationStatus.value = ""
    }

    private fun applyProfile(
        uin: String,
        localNickname: String,
        localAvatarPath: String,
        info: UserSimpleInfo?,
    ) {
        val profileNickname = info?.coreInfo?.nick.orEmpty()
        val signature = info?.baseInfo?.longNick.orEmpty()
        _profile.value = Profile(
            uin = uin,
            nickname = localNickname.ifBlank { profileNickname }.ifBlank { uin },
            signature = signature,
            avatarPath = localAvatarPath.removePrefix("file://").takeIf { File(it).isFile }
                .orEmpty(),
            avatarUrls = avatarUrls(uin),
            isLoggedIn = QmceApplication.ensureRuntime()?.isLogin() == true,
            refreshing = false,
        )
    }

    private fun avatarUrls(uin: String): List<String> = listOf(
        "https://q1.qlogo.cn/g?b=qq&nk=$uin&s=100",
        "https://q2.qlogo.cn/headimg_dl?dst_uin=$uin&spec=100",
        "https://qlogo2.store.qq.com/qzone/$uin/$uin/100",
    )
}
