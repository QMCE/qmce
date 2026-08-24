package rj.qmce.lite.data.chat

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

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
            title = json?.findFirstText("title", "prompt", "name", "app").orEmpty(),
            description = json?.findFirstText("desc", "description", "text", "content").orEmpty(),
            tag = json?.findFirstText("tag", "app"),
            previewUrl = json?.findFirstText("preview", "cover", "image", "thumb")
                ?.takeIf(::isHttpUrl),
            actionUrl = json?.findFirstText("jumpUrl", "jump_url", "jumpurl", "link", "url")
                ?.takeIf(::isHttpUrl),
        )
    }

    fun parseGroupAnnounceArk(raw: String?, confirmRequired: Boolean): CardMetadata {
        val json = raw?.takeIf(String::isNotBlank)?.let { value ->
            runCatching { JSONObject(value) }.getOrNull()
        }
        val prompt = json?.optString("prompt")?.trim()?.takeIf(String::isNotBlank)
        val metaFields = json?.collectMetaFields().orEmpty()
        val metaTitle = metaFields.firstNotNullOfOrNull { (key, value) ->
            value.takeIf { key == "title" }
        }
        val metaDesc = metaFields.firstNotNullOfOrNull { (key, value) ->
            value.takeIf { key in setOf("desc", "description") }
        }
        val metaText = metaFields.firstNotNullOfOrNull { (key, value) ->
            value.takeIf { key == "text" }
        }
        val title = prompt ?: metaTitle ?: metaText.orEmpty()
        val description = metaDesc?.takeUnless { it == title }.orEmpty()
        return CardMetadata(
            title = title,
            description = description,
            tag = if (confirmRequired) "群公告·待确认" else "群公告",
            previewUrl = json?.findFirstText("preview", "cover", "image", "thumb")
                ?.takeIf(::isHttpUrl),
            actionUrl = json?.findFirstText("jumpUrl", "jump_url", "jumpurl", "link", "url")
                ?.takeIf(::isHttpUrl),
        )
    }

    fun parseStructCard(xmlContent: String): StructCardMetadata {
        val fields = xmlContent.readXmlFields(setOf("title", "brief", "groupname", "groupcode"))
        return StructCardMetadata(
            title = fields["title"] ?: fields["groupname"].orEmpty(),
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

    private fun JSONObject.collectMetaFields(depth: Int = 0): List<Pair<String, String>> {
        if (depth > 6) return emptyList()
        val results = ArrayList<Pair<String, String>>()
        keys().asSequence().forEach { key ->
            when (val value = opt(key)) {
                is JSONObject -> {
                    if (key.equals("meta", ignoreCase = true)) {
                        value.keys().asSequence().forEach { metaKey ->
                            when (val metaValue = value.opt(metaKey)) {
                                is JSONObject -> metaValue.keys().asSequence().forEach { field ->
                                    metaValue.optString(field).trim()
                                        .takeIf(String::isNotBlank)
                                        ?.let { results += field.lowercase() to it.take(180) }
                                }

                                is String -> metaValue.trim()
                                    .takeIf(String::isNotBlank)
                                    ?.let { results += metaKey.lowercase() to it.take(180) }
                            }
                        }
                    } else {
                        results += value.collectMetaFields(depth + 1)
                    }
                }

                is JSONArray -> (0 until value.length()).forEach { index ->
                    (value.opt(index) as? JSONObject)?.let {
                        results += it.collectMetaFields(depth + 1)
                    }
                }
            }
        }
        return results
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
