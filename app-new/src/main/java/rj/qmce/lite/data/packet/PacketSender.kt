package rj.qmce.lite.data.packet

import android.util.Log
import com.tencent.common.app.AppInterface
import com.tencent.mobileqq.pb.ByteStringMicro
import com.tencent.qphone.base.remote.ToServiceMsg
import com.tencent.qqnt.kernel.nativeinterface.ArkElement
import com.tencent.qqnt.kernel.nativeinterface.Contact
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import mqq.app.AppRuntime
import rj.qmce.lite.QmceApplication
import rj.qmce.lite.data.chat.ChatRepository

class PacketSender(
    private val runtimeProvider: () -> AppRuntime? = { QmceApplication.ensureRuntime() },
) {
    fun sendPb(command: String, payload: ByteArray): PacketResult {
        val app = currentAppInterface() ?: return PacketResult.Rejected("登录运行时不可用")
        val normalizedCommand = command.trim()
        if (normalizedCommand.isBlank()) return PacketResult.Rejected("请输入命令")
        return runCatching {
            val message = ToServiceMsg("mobileqq.service", app.currentAccountUin, normalizedCommand)
            message.putWupBuffer(payload)
            message.addAttribute("req_pb_protocol_flag", true)
            message.extraData.putBoolean("req_pb_protocol_flag", true)
            app.sendToService(message)
            Log.d(TAG, "packet: queued PB command=$normalizedCommand bytes=${payload.size}")
            PacketResult.Queued("PB", payload.size)
        }.getOrElse { error ->
            Log.w(TAG, "packet: send PB failed", error)
            PacketResult.Rejected(error.message ?: "PB 发送失败")
        }
    }

    fun sendOidb(
        command: String,
        commandId: Int,
        serviceType: Int,
        clientVersion: String,
        body: ByteArray,
    ): PacketResult {
        val app = currentAppInterface() ?: return PacketResult.Rejected("登录运行时不可用")
        val normalizedCommand = command.trim()
        if (normalizedCommand.isBlank()) return PacketResult.Rejected("请输入命令")
        return runCatching {
            val pkg = tencent.im.oidb.oidb_sso.OIDBSSOPkg().apply {
                uint32_command.set(commandId)
                uint32_service_type.set(serviceType)
                str_client_version.set(clientVersion.ifBlank { "android" })
                bytes_bodybuffer.set(ByteStringMicro.copyFrom(body))
            }
            val message = ToServiceMsg("mobileqq.service", app.currentAccountUin, normalizedCommand)
            message.putWupBuffer(pkg.toByteArray())
            message.addAttribute("req_pb_protocol_flag", true)
            message.extraData.putBoolean("req_pb_protocol_flag", true)
            app.sendToService(message)
            Log.d(TAG, "packet: queued OIDB command=$normalizedCommand id=$commandId bytes=${body.size}")
            PacketResult.Queued("OIDB", body.size)
        }.getOrElse { error ->
            Log.w(TAG, "packet: send OIDB failed", error)
            PacketResult.Rejected(error.message ?: "OIDB 发送失败")
        }
    }

    fun sendArk(
        target: PacketTarget,
        arkJson: String,
        callback: (errorCode: Int, errorMessage: String?) -> Unit,
    ): PacketResult {
        if (target.peerUid.isBlank()) return PacketResult.Rejected("聊天目标不可用")
        val runtime = runtimeProvider() ?: return PacketResult.Rejected("登录运行时不可用")
        if (!runtime.isLogin()) return PacketResult.Rejected("当前账号未登录")
        return runCatching {
            val repository = ChatRepository()
            when (val connection = repository.connect(runtime)) {
                is ChatRepository.Connection.Ready -> Unit
                ChatRepository.Connection.KernelUnavailable -> error("内核不可用")
                is ChatRepository.Connection.MsgServiceUnavailable -> error("消息服务不可用")
            }
            val element = MsgElement().apply {
                elementType = 10
                elementId = 0L
                arkElement = ArkElement(arkJson, null, null)
            }
            val contact = Contact(target.chatType, target.peerUid, "")
            if (!repository.sendMessage(
                    contact,
                    arrayListOf(element),
                    callback,
                )
            ) {
                error("消息服务不可用")
            }
            Log.d(TAG, "packet: queued Ark target=${target.peerUid} bytes=${arkJson.toByteArray().size}")
            PacketResult.Queued("Ark", arkJson.toByteArray().size)
        }.getOrElse { error ->
            Log.w(TAG, "packet: send Ark failed", error)
            PacketResult.Rejected(error.message ?: "Ark 发送失败")
        }
    }

    private fun currentAppInterface(): AppInterface? {
        val runtime = runtimeProvider() ?: return null
        if (!runtime.isLogin()) return null
        return runtime as? AppInterface
    }

    private companion object {
        const val TAG = "QMCE-Packet"
    }
}
