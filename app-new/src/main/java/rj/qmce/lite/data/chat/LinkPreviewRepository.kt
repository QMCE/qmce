package rj.qmce.lite.data.chat

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class LinkPreviewData(
    val url: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
)

sealed interface LinkPreviewState {
    data object Idle : LinkPreviewState

    data object Loading : LinkPreviewState

    data class Ready(val preview: LinkPreviewData) : LinkPreviewState

    data object Failed : LinkPreviewState
}

object LinkPreviewRepository {
    private const val TAG = "QMCE-LinkPreview"
    private const val MAX_DOCUMENT_BYTES = 256 * 1024
    private val states = ConcurrentHashMap<String, LinkPreviewState>()
    private val executor: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "QMCE-LinkPreview").apply { isDaemon = true }
    }

    @Volatile
    private var invalidationListener: (() -> Unit)? = null

    fun setInvalidationListener(listener: (() -> Unit)?) {
        invalidationListener = listener
    }

    fun state(url: String): LinkPreviewState = states[url] ?: LinkPreviewState.Idle

    fun firstSupportedUrl(text: String): String? = URL_REGEX.find(text)?.value

    fun request(url: String) {
        if (!isSupportedUrl(url)) return
        val current = state(url)
        if (current is LinkPreviewState.Loading ||
            current is LinkPreviewState.Ready ||
            current is LinkPreviewState.Failed
        ) {
            return
        }
        states[url] = LinkPreviewState.Loading
        invalidationListener?.invoke()
        executor.execute {
            val nextState = runCatching { LinkPreviewState.Ready(load(url)) }
                .onFailure { error -> Log.w(TAG, "preview request failed for $url", error) }
                .getOrElse { LinkPreviewState.Failed }
            states[url] = nextState
            invalidationListener?.invoke()
        }
    }

    private fun load(url: String): LinkPreviewData {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 5_000
            readTimeout = 5_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "QMCE-Lite-LinkPreview/1.0")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        try {
            val responseCode = connection.responseCode
            check(responseCode in 200..299) { "HTTP $responseCode" }
            val contentType = connection.contentType.orEmpty()
            check(
                contentType.contains(
                    "text/html",
                    ignoreCase = true
                ) || contentType.contains("xhtml", ignoreCase = true)
            ) {
                "非网页内容"
            }
            val html = connection.inputStream.use { input ->
                input.readTextLimited(MAX_DOCUMENT_BYTES)
            }
            val finalUrl = connection.url.toString()
            val title = html.metaContent("og:title", "twitter:title")
                ?: html.titleContent()
                ?: finalUrl
            val description =
                html.metaContent("og:description", "twitter:description", "description")
            val imageUrl = html.metaContent("og:image", "twitter:image")
                ?.let { image -> runCatching { URL(URL(finalUrl), image).toString() }.getOrNull() }
            return LinkPreviewData(
                url = finalUrl,
                title = title.normalizedPreviewText(MAX_TITLE_LENGTH),
                description = description?.normalizedPreviewText(MAX_DESCRIPTION_LENGTH),
                imageUrl = imageUrl,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readTextLimited(maxBytes: Int): String {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream(maxBytes.coerceAtMost(DEFAULT_BUFFER_SIZE))
        var remaining = maxBytes
        while (remaining > 0) {
            val count = read(buffer, 0, minOf(buffer.size, remaining))
            if (count <= 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun String.metaContent(vararg names: String): String? {
        val expected = names.map(String::lowercase).toSet()
        return META_TAG_REGEX.findAll(this).firstNotNullOfOrNull { match ->
            val tag = match.value
            val name = tag.attribute("property") ?: tag.attribute("name")
            if (name?.lowercase() in expected) tag.attribute("content")
                ?.takeIf(String::isNotBlank) else null
        }
    }

    private fun String.titleContent(): String? = TITLE_REGEX.find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.stripHtml()
        ?.takeIf(String::isNotBlank)

    private fun String.attribute(name: String): String? = ATTRIBUTE_TEMPLATE
        .replace("%NAME%", Regex.escape(name))
        .toRegex(RegexOption.IGNORE_CASE)
        .find(this)
        ?.groupValues
        ?.getOrNull(2)
        ?.trim()

    private fun String.stripHtml(): String = replace(HTML_TAG_REGEX, " ").decodeEntities().trim()

    private fun String.normalizedPreviewText(maxLength: Int): String =
        decodeEntities().replace(WHITESPACE_REGEX, " ").trim().take(maxLength)

    private fun String.decodeEntities(): String =
        replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

    private fun isSupportedUrl(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("http://")

    private val URL_REGEX = Regex("https?://[^\\s<>\\\"]+")
    private val META_TAG_REGEX = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private const val ATTRIBUTE_TEMPLATE = "\\b%NAME%\\s*=\\s*([\\\"'])(.*?)\\1"
    private val TITLE_REGEX = Regex(
        "<title[^>]*>(.*?)</title>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private const val MAX_TITLE_LENGTH = 120
    private const val MAX_DESCRIPTION_LENGTH = 180
}
