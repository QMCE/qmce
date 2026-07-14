package rj.qmce.lite.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rj.qmce.lite.data.packet.PacketEncoder
import rj.qmce.lite.data.packet.PacketMode
import rj.qmce.lite.data.packet.PacketPayloadFormat
import rj.qmce.lite.data.packet.PacketResult
import rj.qmce.lite.data.packet.PacketSender
import rj.qmce.lite.data.packet.PacketTarget

data class PacketToolState(
    val mode: PacketMode = PacketMode.Pb,
    val payloadFormat: PacketPayloadFormat = PacketPayloadFormat.FieldJson,
    val command: String = "",
    val commandId: String = "",
    val serviceType: String = "",
    val clientVersion: String = "android",
    val payload: String = "",
    val target: PacketTarget? = null,
    val sending: Boolean = false,
    val status: String = "",
)

class PacketToolViewModel(
    private val sender: PacketSender = PacketSender(),
) : ViewModel() {
    private val _state = MutableStateFlow(PacketToolState())
    val state: StateFlow<PacketToolState> = _state.asStateFlow()

    fun setMode(mode: PacketMode) = update { copy(mode = mode, status = "") }
    fun setPayloadFormat(format: PacketPayloadFormat) = update { copy(payloadFormat = format, status = "") }
    fun setCommand(value: String) = update { copy(command = value) }
    fun setCommandId(value: String) = update { copy(commandId = value) }
    fun setServiceType(value: String) = update { copy(serviceType = value) }
    fun setClientVersion(value: String) = update { copy(clientVersion = value) }
    fun setPayload(value: String) = update { copy(payload = value) }

    fun setTarget(target: PacketTarget?) {
        if (_state.value.target != target) {
            _state.value = _state.value.copy(target = target)
        }
    }

    fun send() {
        val snapshot = _state.value
        if (snapshot.sending) return
        update { copy(sending = true, status = "正在发送…") }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                sendInternal(snapshot)
            }
            when (result) {
                is PacketResult.Queued -> update {
                    copy(
                        sending = false,
                        status = "${result.kind} 已提交（${result.byteCount} 字节）",
                    )
                }
                is PacketResult.Rejected -> update { copy(sending = false, status = result.message) }
            }
        }
    }

    private fun sendInternal(snapshot: PacketToolState): PacketResult {
        return runCatching {
            when (snapshot.mode) {
                PacketMode.Pb -> {
                    val payload = PacketEncoder.decodePayload(snapshot.payload, snapshot.payloadFormat)
                    sender.sendPb(snapshot.command, payload)
                }
                PacketMode.Oidb -> {
                    val payload = PacketEncoder.decodePayload(snapshot.payload, snapshot.payloadFormat)
                    sender.sendOidb(
                        command = snapshot.command,
                        commandId = parseNumber(snapshot.commandId, "OIDB command id"),
                        serviceType = parseNumber(snapshot.serviceType, "service type"),
                        clientVersion = snapshot.clientVersion,
                        body = payload,
                    )
                }
                PacketMode.Ark -> {
                    val target = snapshot.target
                        ?: return PacketResult.Rejected("Ark 请从聊天页进入发包工具")
                    sender.sendArk(target, snapshot.payload) { code, message ->
                        _state.value = _state.value.copy(
                            status = if (code == 0) {
                                "Ark 发送成功"
                            } else {
                                "Ark 发送失败（$code）${message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}"
                            },
                        )
                    }
                }
            }
        }.getOrElse { error ->
            PacketResult.Rejected(error.message ?: "发送失败")
        }
    }

    private fun parseNumber(value: String, label: String): Int {
        val normalized = value.trim()
        val parsed = if (normalized.startsWith("0x", ignoreCase = true)) {
            normalized.substring(2).toLongOrNull(16)
        } else {
            normalized.toLongOrNull()
        }
        require(parsed != null && parsed in 0..Int.MAX_VALUE) { "$label 无效" }
        return parsed.toInt()
    }

    private inline fun update(transform: PacketToolState.() -> PacketToolState) {
        _state.value = _state.value.transform()
    }
}
