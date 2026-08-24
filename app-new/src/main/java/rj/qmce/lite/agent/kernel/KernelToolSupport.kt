package rj.qmce.lite.agent.kernel

import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import rj.qmce.lite.agent.ToolResult

/** Best-effort single-line summary of a MsgRecord's text content. */
fun msgRecordToText(record: MsgRecord): String {
    val sb = StringBuilder()
    record.elements?.forEach { element ->
        sb.append(elementText(element))
    }
    val text = sb.toString().trim()
    return text.ifBlank { "[非文本消息]" }
}

private fun elementText(element: MsgElement): String = when {
    element.textElement != null -> element.textElement.content.orEmpty()
    element.faceElement != null -> element.faceElement.faceText?.takeIf { it.isNotBlank() }
        ?: "[表情]"
    element.pttElement != null -> "[语音]"
    element.picElement != null -> "[图片]"
    element.videoElement != null -> "[视频]"
    element.fileElement != null -> "[文件]"
    element.marketFaceElement != null ->
        element.marketFaceElement.faceName?.takeIf { it.isNotBlank() } ?: "[表情]"
    element.replyElement != null -> "[回复]"
    element.arkElement != null -> "[卡片]"
    else -> "[消息]"
}

/** Read-only kernel tools helpers. */

fun schemaString(description: String): Map<String, Any> =
    mapOf("type" to "string", "description" to description)

fun schemaInt(description: String): Map<String, Any> =
    mapOf("type" to "integer", "description" to description)

fun schemaBool(description: String): Map<String, Any> =
    mapOf("type" to "boolean", "description" to description)

fun schemaOf(vararg fields: Pair<String, Map<String, Any>>): Map<String, Any> =
    fields.toMap()

fun requireString(input: Map<String, Any>, key: String): String? =
    (input[key] as? String)?.trim()?.takeIf { it.isNotBlank() }

fun requireInt(input: Map<String, Any>, key: String): Int? =
    (input[key] as? Number)?.toInt()

fun requireLong(input: Map<String, Any>, key: String): Long? =
    when (val v = input[key]) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull()
        else -> null
    }

fun ok(text: String): ToolResult = ToolResult(text)

fun err(text: String): ToolResult = ToolResult(text, isError = true)
