package rj.qmce.lite.kernel

import com.tencent.qqnt.kernel.api.IGroupService
import com.tencent.qqnt.kernel.api.IProfileService
import com.tencent.qqnt.kernel.api.IMsgService
import com.tencent.qqnt.kernel.api.IRecentContactService
import com.tencent.qqnt.kernel.nativeinterface.AnchorPointContactInfo
import com.tencent.qqnt.kernel.nativeinterface.EnterOrExitMsgListInfo
import com.tencent.qqnt.kernel.nativeinterface.IGroupMemberListCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupListener
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgListener
import com.tencent.qqnt.kernel.nativeinterface.IKernelProfileListener
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactListChangedInfo
import kotlin.jvm.functions.Function1

/**
 * Bridges compile-time qq-sdk.jar Kotlin names to runtime qq-sdk-runtime.jar JVM names.
 */
object SdkCompat {

    fun addMsgListener(msgService: IMsgService, listener: IKernelMsgListener) {
        invokeVoid(
            msgService,
            IMsgService::class.java,
            listOf("addMsgListener", "o"),
            arrayOf(IKernelMsgListener::class.java),
            listener,
        )
    }

    fun removeMsgListener(msgService: IMsgService, listener: IKernelMsgListener) {
        invokeVoid(
            msgService,
            IMsgService::class.java,
            listOf("removeMsgListener", "d"),
            arrayOf(IKernelMsgListener::class.java),
            listener,
        )
    }

    fun addProfileListener(profileService: IProfileService, listener: IKernelProfileListener) {
        invokeVoid(
            profileService,
            IProfileService::class.java,
            listOf("addProfileListener", "M"),
            arrayOf(IKernelProfileListener::class.java),
            listener,
        )
    }

    fun addRecentContactListenerV2(
        recentService: IRecentContactService,
        listType: Int,
        listener: (RecentContactListChangedInfo) -> Unit,
    ) {
        invokeVoid(
            recentService,
            IRecentContactService::class.java,
            listOf("addRecentContactListener", "q"),
            arrayOf<Class<*>>(
                Int::class.javaPrimitiveType!!,
                Function1::class.java,
            ),
            listType,
            listener,
        )
    }

    fun getRecentContactFromCache(
        recentService: IRecentContactService,
        listType: Int,
    ): List<RecentContactInfo>? {
        return invokeReturning(
            recentService,
            IRecentContactService::class.java,
            listOf("getRecentContactFromCache", "D"),
            arrayOf(Int::class.javaPrimitiveType!!),
            listType,
        ) as? List<RecentContactInfo>
    }

    fun fetchRecentContactInfo(
        recentService: IRecentContactService,
        anchor: AnchorPointContactInfo,
        fetchOld: Boolean,
        listType: Int,
        count: Int,
        callback: IOperateCallback?,
    ) {
        invokeVoid(
            recentService,
            IRecentContactService::class.java,
            listOf("fetchRecentContactInfo", "I"),
            arrayOf<Class<*>>(
                AnchorPointContactInfo::class.java,
                Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                IOperateCallback::class.java,
            ),
            anchor,
            fetchOld,
            listType,
            count,
            callback,
        )
    }

    fun enterOrExitMsgList(
        recentService: IRecentContactService,
        enterOrExitInfo: EnterOrExitMsgListInfo,
        callback: IOperateCallback?,
    ) {
        invokeVoid(
            recentService,
            IRecentContactService::class.java,
            listOf("enterOrExitMsgList"),
            arrayOf(EnterOrExitMsgListInfo::class.java, IOperateCallback::class.java),
            enterOrExitInfo,
            callback,
        )
    }

    fun getMemberInfoForMqq(
        groupService: IGroupService,
        groupCode: Long,
        uids: ArrayList<String>,
        forceUpdate: Boolean,
        from: String,
        callback: IGroupMemberListCallback?,
    ) {
        invokeVoid(
            groupService,
            IGroupService::class.java,
            listOf("getMemberInfoForMqq", "j"),
            arrayOf(
                Long::class.javaPrimitiveType!!,
                ArrayList::class.java,
                Boolean::class.javaPrimitiveType!!,
                String::class.java,
                IGroupMemberListCallback::class.java,
            ),
            groupCode,
            uids,
            forceUpdate,
            from,
            callback,
        )
    }

    fun addGroupListener(groupService: IGroupService, listener: IKernelGroupListener?) {
        invokeVoid(
            groupService,
            IGroupService::class.java,
            listOf("addGroupListener", "i"),
            arrayOf(IKernelGroupListener::class.java),
            listener,
        )
    }

    fun removeGroupListener(groupService: IGroupService, listener: IKernelGroupListener?) {
        invokeVoid(
            groupService,
            IGroupService::class.java,
            listOf("removeGroupListener", "p"),
            arrayOf(IKernelGroupListener::class.java),
            listener,
        )
    }

    private fun invokeVoid(
        target: Any,
        iface: Class<*>,
        names: List<String>,
        paramTypes: Array<Class<*>>,
        vararg args: Any?,
    ) {
        invokeReturning(target, iface, names, paramTypes, *args)
    }

    private fun invokeReturning(
        target: Any,
        iface: Class<*>,
        names: List<String>,
        paramTypes: Array<Class<*>>,
        vararg args: Any?,
    ): Any? {
        for (name in names) {
            runCatching {
                return iface.getMethod(name, *paramTypes).invoke(target, *args)
            }
            runCatching {
                return target.javaClass.getMethod(name, *paramTypes).invoke(target, *args)
            }
        }
        error("${names.first()} unavailable on ${target.javaClass.name}")
    }
}
