package rj.qmce.lite.data.chat

import com.tencent.qqnt.kernel.nativeinterface.ContactMsgBoxInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import com.tencent.qqnt.kernel.nativeinterface.SpecificEventTypeInfoInMsgBox

data class MessageNavigationPoint(
    val sequence: Long,
    val label: String,
)

data class MessageNavigationSnapshot(
    val unreadCount: Int = 0,
    val firstUnreadSequence: Long? = null,
    val importantPoints: List<MessageNavigationPoint> = emptyList(),
) {
    fun mergeFallback(fallback: MessageNavigationSnapshot): MessageNavigationSnapshot {
        val mergedPoints = (importantPoints + fallback.importantPoints)
            .asSequence()
            .filter { it.sequence > 0L }
            .distinctBy(MessageNavigationPoint::sequence)
            .sortedByDescending(MessageNavigationPoint::sequence)
            .toList()
        return MessageNavigationSnapshot(
            unreadCount = maxOf(unreadCount, fallback.unreadCount),
            firstUnreadSequence = firstUnreadSequence ?: fallback.firstUnreadSequence,
            importantPoints = mergedPoints,
        )
    }

    companion object {
        fun fromRecentContact(contact: RecentContactInfo?): MessageNavigationSnapshot {
            if (contact == null) return MessageNavigationSnapshot()
            val unreadCount = contact.unreadCnt.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            return MessageNavigationSnapshot(
                unreadCount = unreadCount,
                importantPoints = if (unreadCount > 0) {
                    contact.listOfSpecificEventTypeInfosInMsgBox.toNavigationPoints()
                } else {
                    emptyList()
                },
            )
        }

        fun fromMessageBox(info: ContactMsgBoxInfo?): MessageNavigationSnapshot {
            if (info == null) return MessageNavigationSnapshot()
            val unreadCount =
                info.unreadCnt?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0
            return MessageNavigationSnapshot(
                unreadCount = unreadCount,
                firstUnreadSequence = info.firstUnreadMsgInfo?.msgSeq?.takeIf { it > 0L },
                importantPoints = if (unreadCount > 0) {
                    info.listOfSpecificEventTypeInfosInMsgBox.toNavigationPoints()
                } else {
                    emptyList()
                },
            )
        }
    }
}

private fun List<SpecificEventTypeInfoInMsgBox>?.toNavigationPoints(): List<MessageNavigationPoint> =
    orEmpty()
        .asSequence()
        .flatMap { event ->
            event.msgInfos.orEmpty().asSequence().mapNotNull { message ->
                val label = message.highlightDigest
                    ?.takeIf(String::isNotBlank)
                    ?: EVENT_TYPE_LABELS[event.eventTypeInMsgBox]
                if (message.msgSeq > 0L && label != null) {
                    MessageNavigationPoint(message.msgSeq, label)
                } else {
                    null
                }
            }
        }
        .distinctBy(MessageNavigationPoint::sequence)
        .sortedByDescending(MessageNavigationPoint::sequence)
        .toList()

private val EVENT_TYPE_LABELS = mapOf(
    1000 to "有人@我",
    1001 to "有人@我",
    1002 to "有人回复我",
    2000 to "有人@全体成员",
    2001 to "有新文件",
    2003 to "有新作业",
    2004 to "有新公告",
)
