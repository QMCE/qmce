package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import rj.qmce.lite.data.packet.PacketMode
import rj.qmce.lite.data.packet.PacketPayloadFormat
import rj.qmce.lite.data.packet.PacketTarget
import rj.qmce.lite.viewmodel.PacketToolViewModel

@Composable
fun PacketToolScreen(
    peerUid: String = "",
    peerName: String = "",
    chatType: Int = 0,
    vm: PacketToolViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(peerUid, peerName, chatType) {
        vm.setTarget(
            if (peerUid.isBlank()) null else PacketTarget(chatType, peerUid, peerName),
        )
    }
    val scheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "‹ 返回",
                    color = scheme.primary,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable(onClick = onBack).padding(vertical = 5.dp),
                )
                Text(
                    text = "发包工具",
                    color = scheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.weight(0.35f))
            }
        }
        item {
            ModeRow(
                selected = state.mode,
                onSelected = vm::setMode,
            )
        }
        if (state.mode == PacketMode.Ark) {
            item {
                Text(
                    text = state.target?.let { "发送到：${it.peerName.ifBlank { it.peerUid }}" }
                        ?: "Ark 请从聊天页进入",
                    color = if (state.target == null) scheme.error else scheme.outline,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item {
                PacketInput(
                    value = state.command,
                    onValueChange = vm::setCommand,
                    label = if (state.mode == PacketMode.Oidb) "服务命令" else "PB 命令",
                    hint = if (state.mode == PacketMode.Oidb) "例如 OidbSvcTrpcTcp.0x..." else "例如 MessageSvc.PbSendMsg",
                    singleLine = true,
                )
            }
            if (state.mode == PacketMode.Oidb) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        PacketInput(
                            value = state.commandId,
                            onValueChange = vm::setCommandId,
                            label = "Command ID",
                            hint = "十进制或 0x",
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        PacketInput(
                            value = state.serviceType,
                            onValueChange = vm::setServiceType,
                            label = "Service type",
                            hint = "例如 1",
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    PacketInput(
                        value = state.clientVersion,
                        onValueChange = vm::setClientVersion,
                        label = "客户端版本",
                        hint = "可留空",
                        singleLine = true,
                    )
                }
            } else {
                item {
                    FormatRow(
                        selected = state.payloadFormat,
                        onSelected = vm::setPayloadFormat,
                    )
                }
            }
        }
        item {
            PacketInput(
                value = state.payload,
                onValueChange = vm::setPayload,
                label = if (state.mode == PacketMode.Ark) "Ark JSON" else "Payload",
                hint = payloadHint(state.mode, state.payloadFormat),
                singleLine = false,
                modifier = Modifier.height(if (state.mode == PacketMode.Ark) 132.dp else 116.dp),
            )
        }
        item {
            Button(
                onClick = vm::send,
                enabled = !state.sending,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    disabledContainerColor = scheme.surfaceContainer,
                    disabledContentColor = scheme.outline,
                ),
            ) {
                Text(if (state.sending) "发送中…" else "发送", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (state.status.isNotBlank()) {
            item {
                Text(
                    text = state.status,
                    color = if (state.status.contains("失败") || state.status.contains("不可用")) scheme.error else scheme.outline,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ModeRow(selected: PacketMode, onSelected: (PacketMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PacketChoice("PB", selected == PacketMode.Pb, { onSelected(PacketMode.Pb) }, Modifier.weight(1f))
        PacketChoice("OIDB", selected == PacketMode.Oidb, { onSelected(PacketMode.Oidb) }, Modifier.weight(1f))
        PacketChoice("Ark", selected == PacketMode.Ark, { onSelected(PacketMode.Ark) }, Modifier.weight(1f))
    }
}

@Composable
private fun FormatRow(selected: PacketPayloadFormat, onSelected: (PacketPayloadFormat) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PacketChoice("字段 JSON", selected == PacketPayloadFormat.FieldJson, { onSelected(PacketPayloadFormat.FieldJson) }, Modifier.weight(1f))
        PacketChoice("Hex", selected == PacketPayloadFormat.Hex, { onSelected(PacketPayloadFormat.Hex) }, Modifier.weight(1f))
        PacketChoice("Base64", selected == PacketPayloadFormat.Base64, { onSelected(PacketPayloadFormat.Base64) }, Modifier.weight(1f))
    }
}

@Composable
private fun PacketChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        ),
    ) {
        Text(text, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun PacketInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = scheme.primary,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 5.dp, bottom = 2.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = scheme.onSurface, fontSize = 11.sp),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(hint, color = scheme.outline, fontSize = 10.sp)
                }
                innerTextField()
            },
        )
    }
}

private fun payloadHint(mode: PacketMode, format: PacketPayloadFormat): String = when {
    mode == PacketMode.Ark -> "例如 {\"app\":\"com.tencent.mobileqq\",...}"
    format == PacketPayloadFormat.FieldJson -> "字段号 JSON，例如 {\"1\":123,\"2\":{\"1\":\"hello\"}}"
    format == PacketPayloadFormat.Hex -> "例如 0A03616263"
    else -> "Base64 编码内容"
}
