package rj.qmce.lite.data.packet

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object PacketEncoder {
    private const val MAX_PAYLOAD_BYTES = 256 * 1024

    fun decodePayload(text: String, format: PacketPayloadFormat): ByteArray {
        val bytes = when (format) {
            PacketPayloadFormat.FieldJson -> encodeFieldJson(text)
            PacketPayloadFormat.Hex -> decodeHex(text)
            PacketPayloadFormat.Base64 -> Base64.decode(text.trim(), Base64.DEFAULT)
        }
        require(bytes.size <= MAX_PAYLOAD_BYTES) {
            "Payload 不能超过 ${MAX_PAYLOAD_BYTES / 1024} KB"
        }
        return bytes
    }

    fun encodeFieldJson(text: String): ByteArray {
        val root = JSONObject(text)
        return encodeMessage(root)
    }

    private fun encodeMessage(objectValue: JSONObject): ByteArray {
        val output = ByteArrayOutputStream()
        val keys = objectValue.keys().asSequence().toList()
        for (key in keys) {
            val fieldNumber = key.toIntOrNull()
                ?: throw IllegalArgumentException("字段名必须是数字: $key")
            require(fieldNumber in 1..0x1fffffff) { "字段号无效: $fieldNumber" }
            encodeValue(output, fieldNumber, objectValue.get(key))
        }
        return output.toByteArray()
    }

    private fun encodeValue(output: ByteArrayOutputStream, fieldNumber: Int, value: Any?) {
        when (value) {
            JSONObject.NULL, null -> Unit
            is JSONObject -> writeMessage(output, fieldNumber, encodeMessage(value))
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    encodeValue(output, fieldNumber, value.get(index))
                }
            }
            is Boolean -> writeVarintField(output, fieldNumber, if (value) 1L else 0L)
            is Byte, is Short, is Int, is Long -> writeVarintField(output, fieldNumber, (value as Number).toLong())
            is Float, is Double -> throw IllegalArgumentException("暂不支持浮点字段，请改用整数或字符串")
            is String -> {
                val bytes = if (value.startsWith("hex->")) {
                    decodeHex(value.removePrefix("hex->"))
                } else {
                    value.toByteArray(StandardCharsets.UTF_8)
                }
                writeMessage(output, fieldNumber, bytes)
            }
            else -> throw IllegalArgumentException("不支持的字段类型: ${value.javaClass.simpleName}")
        }
    }

    private fun writeMessage(output: ByteArrayOutputStream, fieldNumber: Int, bytes: ByteArray) {
        writeVarint(output, (fieldNumber.toLong() shl 3) or 2L)
        writeVarint(output, bytes.size.toLong())
        output.write(bytes)
    }

    private fun writeVarintField(output: ByteArrayOutputStream, fieldNumber: Int, value: Long) {
        writeVarint(output, fieldNumber.toLong() shl 3)
        writeVarint(output, value)
    }

    private fun writeVarint(output: ByteArrayOutputStream, value: Long) {
        var remaining = value
        while ((remaining and 0x7f.inv().toLong()) != 0L) {
            output.write(((remaining and 0x7f) or 0x80).toInt())
            remaining = remaining ushr 7
        }
        output.write(remaining.toInt())
    }

    private fun decodeHex(text: String): ByteArray {
        val normalized = text.trim().removePrefix("0x").removePrefix("0X")
        require(normalized.length % 2 == 0) { "Hex 内容长度必须是偶数" }
        require(normalized.all { it in "0123456789abcdefABCDEF" }) { "Hex 内容无效" }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
