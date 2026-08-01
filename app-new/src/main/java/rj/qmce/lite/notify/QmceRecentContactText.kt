package rj.qmce.lite.notify

import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo

object QmceRecentContactText {
    fun displayName(contact: RecentContactInfo): String =
        contact.remark?.takeIf { it.isNotBlank() }
            ?: contact.peerName?.takeIf { it.isNotBlank() }
            ?: contact.peerUid?.takeIf { it.isNotBlank() }
            ?: contact.peerUin.takeIf { it > 0L }?.toString()
            ?: "会话"

    fun abstractText(contact: RecentContactInfo): String {
        val parts = contact.abstractContent
            ?.mapNotNull { it.content?.takeIf(String::isNotBlank) }
            .orEmpty()
        if (parts.isEmpty()) return "新消息"
        return parts.joinToString("")
    }
}
