package rj.qmce.lite.data.chat

import android.util.Log
import com.tencent.qqnt.kernel.api.IGroupService
import com.tencent.qqnt.kernel.nativeinterface.GroupBulletinPublishReq
import com.tencent.qqnt.kernel.nativeinterface.IKickMemberOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupService
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.KickMemberResult
import com.tencent.qqnt.kernel.nativeinterface.MemberRole
import rj.qmce.lite.kernel.KernelBridge

object GroupManagementRepository {
    private const val TAG = "QMCE"

    fun setAllMuted(
        groupCode: Long,
        enabled: Boolean,
        role: MemberRole?,
        callback: (Boolean, String?) -> Unit,
    ): Boolean {
        if (groupCode <= 0L) {
            callback(false, "群号无效")
            return false
        }
        if (role != MemberRole.OWNER && role != MemberRole.ADMIN) {
            callback(false, "当前账号没有群管理权限")
            return false
        }
        val service = kernelGroupService()
        if (service == null) {
            callback(false, "群管理服务不可用")
            return false
        }
        return runCatching {
            service.setGroupShutUp(
                groupCode,
                enabled,
                object : IOperateCallback {
                    override fun onResult(errorCode: Int, errorMessage: String?) {
                        callback(errorCode == 0, errorMessage)
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "set group all muted failed: group=$groupCode enabled=$enabled", it)
            callback(false, "群管理请求失败")
            }.getOrDefault(false)
    }

    fun kickMember(
        groupCode: Long,
        memberUid: String,
        actorRole: MemberRole?,
        targetRole: String,
        callback: (Boolean, String?) -> Unit,
    ): Boolean {
        if (groupCode <= 0L) {
            callback(false, "群号无效")
            return false
        }
        if (memberUid.isBlank()) {
            callback(false, "成员 UID 不可用")
            return false
        }
        if (actorRole != MemberRole.OWNER && actorRole != MemberRole.ADMIN) {
            callback(false, "当前账号没有群管理权限")
            return false
        }
        val normalizedTargetRole = targetRole.uppercase()
        if (normalizedTargetRole == MemberRole.OWNER.name ||
            (actorRole == MemberRole.ADMIN && normalizedTargetRole == MemberRole.ADMIN.name)
        ) {
            callback(false, "当前账号不能移出该成员")
            return false
        }
        val service = KernelBridge.getGroupService()
        if (service == null) {
            callback(false, "群管理服务不可用")
            return false
        }
        return runCatching {
            service.kickMember(
                groupCode,
                arrayListOf(memberUid),
                false,
                "",
                object : IKickMemberOperateCallback {
                    override fun onResult(
                        errorCode: Int,
                        errorMessage: String?,
                        results: ArrayList<KickMemberResult>?,
                    ) {
                        val failed = results.orEmpty().firstOrNull { it.result != 0 }
                        val success = errorCode == 0 && failed == null
                        callback(
                            success,
                            if (success) {
                                null
                            } else {
                                errorMessage?.takeIf { it.isNotBlank() }
                                    ?: failed?.let { "成员操作失败 (${it.result})" }
                                    ?: "移出成员失败 ($errorCode)"
                            },
                        )
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "kick group member failed: group=$groupCode uid=$memberUid", it)
            callback(false, "移出成员请求失败")
        }.getOrDefault(false)
    }

    fun publishBulletin(
        groupCode: Long,
        oldFeedsId: String?,
        text: String,
        pinned: Boolean,
        role: MemberRole?,
        callback: (Boolean, String?) -> Unit,
    ): Boolean {
        if (groupCode <= 0L) {
            callback(false, "群号无效")
            return false
        }
        val content = text.trim()
        if (content.isBlank()) {
            callback(false, "群公告内容不能为空")
            return false
        }
        if (role != MemberRole.OWNER && role != MemberRole.ADMIN) {
            callback(false, "当前账号没有发布群公告的权限")
            return false
        }
        val service = kernelGroupService()
        if (service == null) {
            callback(false, "群公告服务不可用")
            return false
        }
        return runCatching {
            val request = GroupBulletinPublishReq().apply {
                this.oldFeedsId = oldFeedsId.orEmpty()
                this.text = content
                this.pinned = if (pinned) 1 else 0
            }
            Log.d(
                TAG,
                "publish group bulletin: group=$groupCode oldFeedsId=${oldFeedsId.orEmpty()} " +
                    "textLength=${content.length} pinned=${request.pinned} picInfo=${request.picInfo}",
            )
            service.publishGroupBulletin(
                groupCode,
                content,
                request,
                object : IOperateCallback {
                    override fun onResult(errorCode: Int, errorMessage: String?) {
                        Log.d(
                            TAG,
                            "publish group bulletin result: group=$groupCode code=$errorCode " +
                                "message=${errorMessage.orEmpty()}",
                        )
                        callback(
                            errorCode == 0,
                            errorMessage?.takeIf { it.isNotBlank() }
                                ?: if (errorCode == 0) null else "发布群公告失败 ($errorCode)",
                        )
                    }
                },
            )
            true
        }.onFailure {
            Log.w(TAG, "publish group bulletin failed: group=$groupCode", it)
            callback(false, "发布群公告请求失败")
        }.getOrDefault(false)
    }

    private fun kernelGroupService(): IKernelGroupService? {
        val groupService = KernelBridge.getGroupService() ?: return null
        return runCatching {
            groupService.invokeKernelServiceGetter()
        }.getOrNull()
    }

    private fun IGroupService.invokeKernelServiceGetter(): IKernelGroupService? {
        val getter = javaClass.methods.firstOrNull {
            it.name == "getService" && it.parameterTypes.isEmpty()
        }
        val service = getter?.invoke(this)
        if (service is IKernelGroupService) return service

        var type: Class<*>? = javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name == "service" }
            if (field != null) {
                field.isAccessible = true
                return field.get(this) as? IKernelGroupService
            }
            type = type.superclass
        }
        return null
    }
}
