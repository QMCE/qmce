package rj.qmce.lite.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

class QmceLaunchComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        QmceComplicationSupport.buildLaunch(this, type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (!QmceComplicationSupport.complicationsEnabled(this)) return null
        return QmceComplicationSupport.buildLaunch(this, request.complicationType)
    }
}
