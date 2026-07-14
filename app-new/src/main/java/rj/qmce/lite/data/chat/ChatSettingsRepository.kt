package rj.qmce.lite.data.chat

import android.util.Log
import com.tencent.qqnt.kernel.nativeinterface.GroupMsgMask
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import rj.qmce.lite.kernel.KernelBridge

object ChatSettingsRepository {
    private const val TAG = "QMCE"

    fun setTop(
        chatType: Int,
        peerUid: String,
        peerUin: Long,
        enabled: Boolean,
        callback: (Boolean, String?) -> Unit,
    ): Boolean {
        val result = callbackAdapter(callback)
        return runCatching {
            if (chatType == 2) {
                val service = KernelBridge.getGroupService() ?: return@runCatching false
                if (peerUin <= 0L) return@runCatching false
                service.setTop(peerUin, enabled, result)
            } else {
                val service = KernelBridge.getBuddyService() ?: return@runCatching false
                if (peerUid.isBlank()) return@runCatching false
                service.setTop(peerUid, enabled, result)
            }
            true
        }.onFailure { Log.w(TAG, "setTop failed", it) }.getOrDefault(false)
    }

    fun setMuted(
        chatType: Int,
        peerUid: String,
        peerUin: Long,
        muted: Boolean,
        callback: (Boolean, String?) -> Unit,
    ): Boolean {
        val result = callbackAdapter(callback)
        return runCatching {
            if (chatType == 2) {
                val service = KernelBridge.getGroupService() ?: return@runCatching false
                if (peerUin <= 0L) return@runCatching false
                service.setGroupMsgMask(
                    peerUin,
                    if (muted) GroupMsgMask.SHIELD else GroupMsgMask.RECEIVE,
                    result,
                )
            } else {
                val service = KernelBridge.getBuddyService() ?: return@runCatching false
                if (peerUid.isBlank()) return@runCatching false
                service.setMsgNotify(peerUid, !muted, result)
            }
            true
        }.onFailure { Log.w(TAG, "setMuted failed", it) }.getOrDefault(false)
    }

    private fun callbackAdapter(callback: (Boolean, String?) -> Unit) = object : IOperateCallback {
        override fun onResult(errorCode: Int, errorMessage: String?) {
            callback(errorCode == 0, errorMessage)
        }
    }
}
