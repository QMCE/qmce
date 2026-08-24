package rj.qmce.lite.agent.kernel

import rj.qmce.lite.agent.ToolResult
import rj.qmce.lite.agent.WriteTool
import rj.qmce.lite.data.packet.PacketSender
import rj.qmce.lite.data.packet.PacketTarget
import java.util.Base64

/**
 * Low-level packet tool. High-risk: exposes raw MSF/OIDB/Ark sends.
 * Always requires approval; used only for advanced automation.
 */
class SendPacketTool : WriteTool(
    name = "send_packet",
    description = "高级：发送原始协议包。mode 可选 pb/oidb/ark。pb 需要 command 与 payload（hex 或 base64）；oidb 需要 command、commandId、serviceType、body（hex/base64）；ark 需要 peerUid、chatType、arkJson。风险较高，需用户批准。",
    inputSchema = mapOf(
        "mode" to schemaString("pb / oidb / ark"),
        "command" to schemaString("命令名，如 ProfileService.Pb.ReqSystemMsgNew.Friend"),
        "payload" to schemaString("pb 载荷（hex 或 base64）"),
        "commandId" to schemaInt("oidb 命令号"),
        "serviceType" to schemaInt("oidb 服务类型"),
        "body" to schemaString("oidb 载荷（hex 或 base64）"),
        "peerUid" to schemaString("ark 目标会话 UID"),
        "chatType" to schemaInt("ark 目标会话类型"),
        "arkJson" to schemaString("ark 模板 JSON"),
    ),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val mode = requireString(input, "mode") ?: return err("缺少 mode")
        val sender = PacketSender()
        return when (mode.lowercase()) {
            "pb" -> {
                val command = requireString(input, "command") ?: return err("缺少 command")
                val payload = decodeBytes(requireString(input, "payload") ?: return err("缺少 payload"))
                sender.sendPb(command, payload).let { p ->
                    when (p) {
                        is rj.qmce.lite.data.packet.PacketResult.Queued ->
                            ok("PB 已发送: ${p.kind} ${p.byteCount} 字节")
                        is rj.qmce.lite.data.packet.PacketResult.Rejected ->
                            err("PB 发送被拒绝: ${p.message}")
                    }
                }
            }

            "oidb" -> {
                val command = requireString(input, "command") ?: return err("缺少 command")
                val commandId = requireInt(input, "commandId") ?: return err("缺少 commandId")
                val serviceType = requireInt(input, "serviceType") ?: 0
                val body = decodeBytes(requireString(input, "body") ?: return err("缺少 body"))
                sender.sendOidb(command, commandId, serviceType, "android", body).let { p ->
                    when (p) {
                        is rj.qmce.lite.data.packet.PacketResult.Queued ->
                            ok("OIDB 已发送: ${p.kind} ${p.byteCount} 字节")
                        is rj.qmce.lite.data.packet.PacketResult.Rejected ->
                            err("OIDB 发送被拒绝: ${p.message}")
                    }
                }
            }

            "ark" -> {
                val peerUid = requireString(input, "peerUid") ?: return err("缺少 peerUid")
                val chatType = requireInt(input, "chatType") ?: 1
                val arkJson = requireString(input, "arkJson") ?: return err("缺少 arkJson")
                val target = PacketTarget(chatType, peerUid, peerUid)
                val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                sender.sendArk(target, arkJson) { code, _ -> deferred.complete(code == 0) }
                    .let { p ->
                        when (p) {
                            is rj.qmce.lite.data.packet.PacketResult.Queued -> {
                                if (kotlinx.coroutines.withTimeoutOrNull(5_000) { deferred.await() } == true) {
                                    ok("Ark 已发送")
                                } else {
                                    err("Ark 发送失败")
                                }
                            }

                            is rj.qmce.lite.data.packet.PacketResult.Rejected ->
                                err("Ark 发送被拒绝: ${p.message}")
                        }
                    }
            }

            else -> err("未知 mode: $mode")
        }
    }

    private fun decodeBytes(encoded: String): ByteArray {
        val trimmed = encoded.trim().replace(" ", "")
        // Prefer hex if it looks like hex.
        if (trimmed.isNotEmpty() && trimmed.all { it in "0123456789abcdefABCDEF" } &&
            trimmed.length % 2 == 0
        ) {
            return trimmed.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        return runCatching { Base64.getDecoder().decode(trimmed) }
            .getOrElse { trimmed.toByteArray(Charsets.UTF_8) }
    }
}
