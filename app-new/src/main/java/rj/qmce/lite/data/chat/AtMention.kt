package rj.qmce.lite.data.chat

data class AtMention(
    val uid: String,
    val nick: String,
    val atType: Int = 2,
)
