package rj.qmce.lite.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class QmceLatestMessageComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        QmceComplicationSupport.buildMessage(
            context = this,
            type = type,
            title = "好友",
            text = "你好，这是预览",
            tap = QmceComplicationSupport.launchMain(this),
        )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (!QmceComplicationSupport.complicationsEnabled(this)) return null
        val summary = QmceComplicationSupport.latestMessageSummary(this)
            ?: return QmceComplicationSupport.buildMessage(
                context = this,
                type = request.complicationType,
                title = "QMCE",
                text = "暂无消息",
                tap = QmceComplicationSupport.launchMain(this),
            )
        return QmceComplicationSupport.buildMessage(
            context = this,
            type = request.complicationType,
            title = summary.first,
            text = summary.second,
            tap = summary.third,
        )
    }
}
