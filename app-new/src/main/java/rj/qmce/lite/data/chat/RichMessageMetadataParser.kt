package rj.qmce.lite.data.chat

import android.util.Xml
import java.io.StringReader
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

object RichMessageMetadataParser {

    data class CardMetadata(
        val title: String,
        val description: String,
        val tag: String?,
        val previewUrl: String?,
        val actionUrl: String?,
    )

    data class StructCardMetadata(
        val title: String,
        val description: String,
        val groupCode: String?,
    )

    data class ForwardMetadata(
        val title: String,
        val preview: List<String>,
    )

    fun parseArkCard(raw: String?): CardMetadata {
        val json = raw?.takeIf(String::isNotBlank)?.let { value ->
            runCatching { JSONObject(value) }.getOrNull()
        }
        return CardMetadata(
            title = json?.findFirstText("title", "prompt", "name", "app") ?: "分享卡片",
            description = json?.findFirstText("desc", "description", "text", "content").orEmpty(),
            tag = json?.findFirstText("tag", "app"),
            previewUrl = json?.findFirstText("preview", "cover", "image", "thumb")
                ?.takeIf(::isHttpUrl),
            actionUrl = json?.findFirstText("jumpUrl", "jump_url", "jumpurl", "link", "url")
                ?.takeIf(::isHttpUrl),
        )
    }

    fun parseStructCard(xmlContent: String): StructCardMetadata {
        val fields = xmlContent.readXmlFields(setOf("title", "brief", "groupname", "groupcode"))
        return StructCardMetadata(
            title = fields["title"] ?: fields["groupname"] ?: "群邀请",
            description = fields["brief"].orEmpty(),
            groupCode = fields["groupcode"],
        )
    }

    fun parseForward(xmlContent: String): ForwardMetadata {
        val fields = xmlContent.readXmlFields(setOf("title", "summary", "brief", "item"))
        return ForwardMetadata(
            title = fields["title"] ?: "聊天记录",
            preview = listOfNotNull(fields["summary"], fields["brief"], fields["item"])
                .flatMap { value -> value.lines() }
                .map(String::trim)
                .filter(String::isNotEmpty)
                .take(3),
        )
    }

    fun parseSystemTip(raw: String?): String {
        if (raw.isNullOrBlank()) return "系统消息"
        val fields = raw.readXmlFields(setOf("title", "brief", "summary"))
        return fields["title"]
            ?: fields["brief"]
            ?: raw.take(140).replace(Regex("\\s+"), " ").takeIf(String::isNotBlank)
            ?: "系统消息"
    }

    private fun JSONObject.findFirstText(vararg keys: String): String? {
        val wanted = keys.map(String::lowercase).toSet()
        fun visit(value: Any?, depth: Int): String? {
            if (depth > 5 || value == null) return null
            return when (value) {
                is JSONObject -> value.keys().asSequence().firstNotNullOfOrNull { key ->
                    val nested = value.opt(key)
                    if (key.lowercase() in wanted && nested is String && nested.isNotBlank()) nested
                    else visit(nested, depth + 1)
                }
                is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { index ->
                    visit(value.opt(index), depth + 1)
                }
                else -> null
            }
        }
        return visit(this, 0)?.trim()?.take(180)
    }

    private fun String.readXmlFields(names: Set<String>): Map<String, String> {
        if (isBlank() || !trimStart().startsWith("<")) return emptyMap()
        return runCatching {
            val fields = LinkedHashMap<String, String>()
            val parser = Xml.newPullParser().apply { setInput(StringReader(this@readXmlFields)) }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name in names) {
                    val value = parser.nextText().trim()
                    if (value.isNotEmpty()) fields.putIfAbsent(parser.name, value)
                }
                event = parser.next()
            }
            fields
        }.getOrDefault(emptyMap())
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://") || value.startsWith("http://")
}
