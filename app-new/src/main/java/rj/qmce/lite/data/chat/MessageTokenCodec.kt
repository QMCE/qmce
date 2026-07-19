package rj.qmce.lite.data.chat

/**
 * Invisible delimiters used by the composer to preserve non-text message elements in a draft.
 *
 * These are private-use characters rather than ASCII control characters so they survive ordinary
 * text handling without being emitted as user-visible message content.
 */
object MessageTokenCodec {
    const val BOUNDARY_START: Char = '\uE001'
    const val BOUNDARY_END: Char = '\uE002'

    fun wrap(token: String): String = "$BOUNDARY_START$token$BOUNDARY_END"

    /** Remove the ASCII-delimited tokens written by the pre-private-use implementation. */
    fun removeLegacyTokens(text: String): String {
        if (!text.any { it == '\u0001' || it == '\u0002' }) return text
        val result = StringBuilder(text.length)
        var cursor = 0
        while (cursor < text.length) {
            val start = text.indexOf('\u0001', cursor)
            if (start < 0) {
                result.append(text, cursor, text.length)
                break
            }
            result.append(text, cursor, start)
            val end = text.indexOf('\u0002', start + 1)
            if (end < 0) {
                result.append(text, start + 1, text.length)
                break
            }
            cursor = end + 1
        }
        return result.toString()
    }
}
