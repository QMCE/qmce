package rj.qmce.lite.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import rj.qmce.lite.kernel.KernelBridge
import rj.qmce.lite.kernel.SdkCompat
import rj.qmce.lite.notify.QmceRecentContactText

class QmcePinnedMessageComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        QmceComplicationSupport.buildMessage(
            context = this,
            type = type,
            title = "指定会话",
            text = "点按绑定",
            tap = QmceComplicationSupport.openGroupPicker(this),
        )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (!QmceComplicationSupport.complicationsEnabled(this)) return null
        val pinned = QmcePinnedComplicationStore.load(this)
            ?: return QmceComplicationSupport.buildMessage(
                context = this,
                type = request.complicationType,
                title = "未绑定",
                text = "点按绑定",
                tap = QmceComplicationSupport.openGroupPicker(this),
            )
        val recent = KernelBridge.getRecentContactService()
        val contact = recent?.let {
            runCatching { SdkCompat.getRecentContactFromCache(it, 0) }.getOrNull()
        }.orEmpty().firstOrNull {
            it.peerUid == pinned.peerUid && it.chatType == pinned.chatType
        }
        val peerUin = contact?.peerUin ?: pinned.peerUid.toLongOrNull() ?: 0L
        val abstract = contact?.let(QmceRecentContactText::abstractText) ?: "打开会话"
        return QmceComplicationSupport.buildMessage(
            context = this,
            type = request.complicationType,
            title = pinned.name,
            text = abstract,
            tap = QmceComplicationSupport.openChat(
                this,
                pinned.peerUid,
                peerUin,
                pinned.chatType,
                pinned.name,
            ),
        )
    }
}
