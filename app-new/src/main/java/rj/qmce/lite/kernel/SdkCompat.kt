package rj.qmce.lite.kernel

import com.tencent.qqnt.kernel.api.IBuddyService
import com.tencent.qqnt.kernel.api.IGroupService
import com.tencent.qqnt.kernel.api.IProfileService
import com.tencent.qqnt.kernel.api.IMsgService
import com.tencent.qqnt.kernel.api.IRecentContactService
import com.tencent.qqnt.kernel.invorker.IExpandNotificationListener
import com.tencent.qqnt.kernel.nativeinterface.IKernelBuddyListener
import com.tencent.qqnt.kernel.nativeinterface.AnchorPointContactInfo
import com.tencent.qqnt.kernel.nativeinterface.EnterOrExitMsgListInfo
import com.tencent.qqnt.kernel.nativeinterface.IGroupMemberListCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupListener
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgListener
import com.tencent.qqnt.kernel.nativeinterface.IKernelProfileListener
import com.tencent.qqnt.kernel.nativeinterface.IKernelRecentContactListener
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactListChangedInfo

/**
 * Prefers readable Kotlin method names on the single qq-sdk.jar; short JVM
 * names remain as a compatibility fallback.
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

    @Suppress("UNCHECKED_CAST")
    fun getRecentContactFromCache(
        recentService: IRecentContactService,
        listType: Int,
    ): List<RecentContactInfo>? {
        val raw = invokeReturning(
            recentService,
            IRecentContactService::class.java,
            listOf("getRecentContactFromCache", "D"),
            arrayOf(Int::class.javaPrimitiveType!!),
            listType,
        ) ?: return null
        return raw as? List<RecentContactInfo>
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

    fun addBuddyListener(buddyService: IBuddyService, listener: IKernelBuddyListener?) {
        invokeVoid(
            buddyService,
            IBuddyService::class.java,
            listOf("addBuddyListener", "v"),
            arrayOf(IKernelBuddyListener::class.java),
            listener,
        )
    }

    fun removeBuddyListener(buddyService: IBuddyService, listener: IKernelBuddyListener?) {
        invokeVoid(
            buddyService,
            IBuddyService::class.java,
            listOf("removeBuddyListener", "c"),
            arrayOf(IKernelBuddyListener::class.java),
            listener,
        )
    }

    /** Official short name `l` = setExpandNotificationListener. */
    fun setExpandNotificationListener(
        recentService: IRecentContactService,
        listener: IExpandNotificationListener?,
    ): Boolean = runCatching {
        invokeVoid(
            recentService,
            IRecentContactService::class.java,
            listOf("setExpandNotificationListener", "l"),
            arrayOf(IExpandNotificationListener::class.java),
            listener,
        )
        true
    }.getOrDefault(false)

    fun clearExpandNotificationListener(recentService: IRecentContactService): Boolean =
        setExpandNotificationListener(recentService, null)

    fun addKernelRecentContactListener(
        recentService: IRecentContactService,
        listener: IKernelRecentContactListener,
    ): Boolean = runCatching {
        invokeVoid(
            recentService,
            IRecentContactService::class.java,
            listOf("addKernelRecentContactListener", "g"),
            arrayOf(IKernelRecentContactListener::class.java),
            listener,
        )
        true
    }.getOrDefault(false)

    fun removeKernelRecentContactListener(
        recentService: IRecentContactService,
        listener: IKernelRecentContactListener,
    ): Boolean = runCatching {
        invokeVoid(
            recentService,
            IRecentContactService::class.java,
            listOf("removeKernelRecentContactListener", "x"),
            arrayOf(IKernelRecentContactListener::class.java),
            listener,
        )
        true
    }.getOrDefault(false)

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
