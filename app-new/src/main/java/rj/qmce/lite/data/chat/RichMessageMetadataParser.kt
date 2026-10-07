package rj.qmce.lite.data.chat

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

object RichMessageMetadataParser {

    // Legacy QQ gray-tip pseudo-XML stores the display name inline as a
    // bare tag `<qquin="张三">` / `<uin="张三">` (no attribute name). The tag
    // must be exactly the name holder so well-formed `<qquin uin="123">` is not matched.
    private val LEGACY_NAME_ATTR_REGEX = Regex("""^(?:qquin|uin)="([^"]*)"\s*$""")

    // Attribute-borne copy used when a gray-tip tag carries its sentence as an
    // attribute value instead of text nodes, e.g. <tip content="…"/>.
    private val ATTR_TEXT_REGEX = Regex("""(?:content|text|tip|brief|title|msg|summary)\s*=\s*"([^"]*)"""")

    // Upper bound for the grey-tip text rendered on the round Wear screen, so a
    // long structured tip doesn't blow up the grey cell layout.
    private const val SYSTEM_TIP_MAX_LENGTH = 140

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
        // Single candidate, truncated once at the exit so every path is bounded.
        val candidate = fields["title"]?.takeIf(String::isNotBlank)
            ?: fields["brief"]?.takeIf(String::isNotBlank)
            ?: raw.extractXmlText()
            ?: raw.extractJsonText()
            ?: raw.stripHtmlTags().takeIf(String::isNotBlank)
        return candidate?.truncateForTip()?.takeIf(String::isNotBlank) ?: "系统消息"
    }

    /** Concatenates the character data of an HTML/XML snippet, e.g. `<gtip>..<qquin>..` → readable text. */
    private fun String.extractXmlText(): String? {
        if (isBlank() || !trimStart().startsWith("<")) return null
        // Preferred: well-formed markup whose display names are text nodes
        // (<qquin uin="..">张三</qquin>…). The pull parser concatenates the text.
        runCatching {
            val sb = StringBuilder()
            val parser = Xml.newPullParser().apply { setInput(StringReader(this@extractXmlText)) }
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.TEXT) sb.append(parser.text)
                event = parser.next()
            }
            sb.toString().decodeEntities().normalizeForTip()
                .takeIf(String::isNotBlank)?.let { return it }
        }
        // Legacy pseudo-XML: names are stored inline as qquin="name" / uin="name"
        // (<gtip align="center"><qquin="张三">邀请…). Inline those values.
        val builder = StringBuilder()
        var index = 0
        while (index < length) {
            val open = indexOf('<', index)
            if (open < 0) {
                builder.append(substring(index))
                break
            }
            builder.append(substring(index, open))
            val close = indexOf('>', open)
            if (close < 0) {
                builder.append(substring(open))
                break
            }
            val tag = substring(open + 1, close)
            LEGACY_NAME_ATTR_REGEX.find(tag)?.let { builder.append(it.groupValues[1]) }
            index = close + 1
        }
        builder.toString().decodeEntities().normalizeForTip()
            .takeIf(String::isNotBlank)?.let { return it }
        // No text nodes found — fall back to attribute-borne copy (e.g. <tip content="…"/>)
        // so the grey tip doesn't degrade to a bare "系统消息".
        return extractXmlAttributeText()
    }

    /** Pulls the first readable value out of a tag's own attributes (content/text/tip/brief/…). */
    private fun String.extractXmlAttributeText(): String? {
        for (m in ATTR_TEXT_REGEX.findAll(this)) {
            m.groupValues[1].trim().decodeEntities().normalizeForTip()
                .takeIf(String::isNotBlank)?.let { return it }
        }
        return null
    }

    /** Pulls a readable sentence out of a JSON gray-tip payload. */
    private fun String.extractJsonText(): String? {
        val obj = runCatching { JSONObject(this) }.getOrNull() ?: return null
        // "items" is a list of { type, txt, value, uin } building blocks.
        obj.optJSONArray("items")?.let { items ->
            val parts = ArrayList<String>()
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.optString("txt")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(parts::add)
            }
            if (parts.isNotEmpty()) {
                return parts.joinToString("").trim().takeIf(String::isNotBlank)
            }
        }
        return obj.findFirstText("text", "txt", "msg", "content", "brief", "title")
            ?.takeIf(String::isNotBlank)
    }

    private fun String.stripHtmlTags(): String =
        replace(Regex("<[^>]*>"), "").decodeEntities().normalizeForTip()

    private fun String.normalizeForTip(): String =
        replace(Regex("\\s+"), " ").trim()

    private fun String.truncateForTip(): String = normalizeForTip().take(SYSTEM_TIP_MAX_LENGTH)

    private fun String.decodeEntities(): String =
        replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")

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
