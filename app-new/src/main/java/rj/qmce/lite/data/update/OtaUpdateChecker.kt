package rj.qmce.lite.data.update

/** Result of an OTA / APK update check. */
sealed class OtaCheckResult {
    data object UpToDate : OtaCheckResult()

    data class Available(
        val versionName: String,
        val versionCode: Int? = null,
        val message: String? = null,
        val downloadUrl: String? = null,
        val sha256: String? = null,
        val changelog: String? = null,
        val forceUpdate: Boolean = false,
        val size: Long = 0L,
        val source: String = "",
    ) : OtaCheckResult()

    data class Unavailable(
        val reason: String,
    ) : OtaCheckResult()
}

fun interface OtaUpdateChecker {
    suspend fun checkForUpdate(mode: OtaSourceMode): OtaCheckResult
}

class NoOpOtaUpdateChecker : OtaUpdateChecker {
    override suspend fun checkForUpdate(mode: OtaSourceMode): OtaCheckResult =
        OtaCheckResult.Unavailable("暂未接入升级通道")
}
