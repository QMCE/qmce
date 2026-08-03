package rj.qmce.lite.notify

import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.kernelpublic.nativeinterface.MsgAbstractElement

object QmceRecentContactText {
    fun displayName(contact: RecentContactInfo): String =
        contact.remark?.takeIf { it.isNotBlank() }
            ?: contact.peerName?.takeIf { it.isNotBlank() }
            ?: contact.peerUid?.takeIf { it.isNotBlank() }
            ?: contact.peerUin.takeIf { it > 0L }?.toString()
            ?: "会话"

    fun abstractText(contact: RecentContactInfo): String {
        extractAbstractElements(contact.abstractContent)?.let { return it }
        extractAbstractElements(contact.draft)?.let { return it }
        return ""
    }

    /** Tile/complication subtitle: abstract → peer name → placeholder. */
    fun tileSubtitle(contact: RecentContactInfo?, fallbackName: String = ""): String {
        if (contact != null) {
            abstractText(contact).takeIf { it.isNotBlank() }?.let { return it }
            displayName(contact).takeIf { it.isNotBlank() && it != "会话" }?.let { return it }
        }
        fallbackName.takeIf { it.isNotBlank() }?.let { return it }
        return "暂无消息"
    }

    private fun extractAbstractElements(
        elements: ArrayList<MsgAbstractElement>?,
    ): String? {
        val parts = elements
            ?.mapNotNull(::extractElementText)
            .orEmpty()
        if (parts.isEmpty()) return null
        return parts.joinToString("").takeIf { it.isNotBlank() }
    }

    private fun extractElementText(element: MsgAbstractElement): String? =
        sequenceOf(
            element.content,
            element.customContent,
            element.mdSummary,
            element.fileName,
        ).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .firstOrNull()
}
