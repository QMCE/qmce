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
}
