package rj.qmce.lite.data.chat

import android.util.Log
import com.tencent.qqnt.kernel.api.IGroupService
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupService
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
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
