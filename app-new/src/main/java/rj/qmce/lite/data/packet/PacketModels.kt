package rj.qmce.lite.data.packet

enum class PacketMode {
    Pb,
    Oidb,
    Ark,
}

enum class PacketPayloadFormat {
    FieldJson,
    Hex,
    Base64,
}

data class PacketTarget(
    val chatType: Int,
    val peerUid: String,
    val peerName: String,
)

sealed interface PacketResult {
    data class Queued(
        val kind: String,
        val byteCount: Int,
    ) : PacketResult

    data class Rejected(val message: String) : PacketResult
}
