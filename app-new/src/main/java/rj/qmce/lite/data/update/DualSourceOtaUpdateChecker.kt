package rj.qmce.lite.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rj.qmce.lite.BuildConfig
import rj.qmce.lite.util.QmceLog
import java.net.HttpURLConnection
import java.net.URL

class DualSourceOtaUpdateChecker : OtaUpdateChecker {
    override suspend fun checkForUpdate(mode: OtaSourceMode): OtaCheckResult =
        withContext(Dispatchers.IO) {
            when (mode) {
                OtaSourceMode.GitHub -> {
                    QmceLog.d(TAG, "check mode=GitHub")
                    checkGitHub()
                }
                OtaSourceMode.Server -> {
                    QmceLog.d(TAG, "check mode=Server")
                    checkServer()
                }
                OtaSourceMode.Auto -> {
                    val resolved = resolveSource(OtaSourceMode.Auto)
                    QmceLog.d(TAG, "check mode=Auto resolved=$resolved")
                    when (resolved) {
                        OtaSourceMode.GitHub -> checkGitHub().recoverWithServer()
                        else -> checkServer()
                    }
                }
            }
        }

    suspend fun probeLatencies(): OtaLatencyReport = withContext(Dispatchers.IO) {
        val githubMs = measureRtt(GITHUB_PROBE_URL)
        val serverMs = measureRtt(serverCheckUrl())
        val auto = when {
            githubMs != null && githubMs <= GITHUB_RTT_THRESHOLD_MS -> OtaSourceMode.GitHub
            else -> OtaSourceMode.Server
        }
        QmceLog.d(TAG, "probe githubRttMs=$githubMs serverRttMs=$serverMs auto=$auto")
        OtaLatencyReport(githubMs, serverMs, auto)
    }

    private fun resolveSource(mode: OtaSourceMode): OtaSourceMode {
        if (mode != OtaSourceMode.Auto) return mode
        val rtt = measureRtt(GITHUB_PROBE_URL)
        return if (rtt != null && rtt <= GITHUB_RTT_THRESHOLD_MS) {
            OtaSourceMode.GitHub
        } else {
            OtaSourceMode.Server
        }
    }

    private fun OtaCheckResult.recoverWithServer(): OtaCheckResult {
        if (this !is OtaCheckResult.Unavailable) return this
        QmceLog.w(TAG, "github check unavailable: $reason; trying server")
        return checkServer()
    }

    private fun checkGitHub(): OtaCheckResult {
        return runCatching {
            val body = httpGet(GITHUB_RELEASES_URL) ?: return OtaCheckResult.Unavailable("GitHub 无响应")
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets")
                ?: return OtaCheckResult.Unavailable("GitHub 无 assets")
            var bestUrl: String? = null
            var bestName: String? = null
            var bestCode: Int? = null
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                if (!name.contains("QMCE", ignoreCase = true) &&
                    !name.contains("qmce", ignoreCase = true)
                ) {
                    continue
                }
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val code = Regex("""-(\d+)\.apk$""", RegexOption.IGNORE_CASE)
                    .find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val url = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    ?: continue
                if (bestCode == null || (code != null && code >= bestCode)) {
                    bestCode = code
                    bestUrl = url
                    bestName = name
                }
            }
            if (bestUrl == null) {
                return OtaCheckResult.Unavailable("GitHub 未找到 APK")
            }
            val remoteCode = bestCode
                ?: json.optString("tag_name").filter { it.isDigit() }.toIntOrNull()
            if (remoteCode == null) {
                return OtaCheckResult.Unavailable("无法解析 GitHub versionCode")
            }
            if (remoteCode <= BuildConfig.VERSION_CODE) {
                return OtaCheckResult.UpToDate
            }
            val versionName = json.optString("tag_name").ifBlank { bestName.orEmpty() }
            OtaCheckResult.Available(
                versionName = versionName,
                versionCode = remoteCode,
                message = json.optString("body").takeIf { it.isNotBlank() },
                downloadUrl = bestUrl,
                changelog = json.optString("body").takeIf { it.isNotBlank() },
                source = "github",
            )
        }.getOrElse {
            QmceLog.w(TAG, "github check failed", it)
            OtaCheckResult.Unavailable("GitHub 检查失败: ${it.message}")
        }
    }

    private fun checkServer(): OtaCheckResult {
        return runCatching {
            val body = httpGet(serverCheckUrl())
                ?: return OtaCheckResult.Unavailable("服务器无响应")
            val json = JSONObject(body)
            val remoteCode = json.optInt("versionCode", -1)
            if (remoteCode < 0) {
                return OtaCheckResult.Unavailable("服务器响应缺少 versionCode")
            }
            if (remoteCode <= BuildConfig.VERSION_CODE) {
                return OtaCheckResult.UpToDate
            }
            val downloadUrl = json.optString("downloadUrl").takeIf { it.isNotBlank() }
                ?: return OtaCheckResult.Unavailable("服务器未提供 downloadUrl")
            OtaCheckResult.Available(
                versionName = json.optString("versionName").ifBlank { "v$remoteCode" },
                versionCode = remoteCode,
                message = json.optString("changelog").takeIf { it.isNotBlank() },
                downloadUrl = downloadUrl,
                sha256 = json.optString("sha256").takeIf { it.isNotBlank() },
                changelog = json.optString("changelog").takeIf { it.isNotBlank() },
                forceUpdate = json.optBoolean("forceUpdate", false),
                size = json.optLong("size", 0L),
                source = "server",
            )
        }.getOrElse {
            QmceLog.w(TAG, "server check failed", it)
            OtaCheckResult.Unavailable("服务器检查失败: ${it.message}")
        }
    }

    private fun serverCheckUrl(): String =
        "$SERVER_BASE/api/ota/check?app=qmce&channel=stable&versionCode=${BuildConfig.VERSION_CODE}"

    private fun measureRtt(url: String): Long? {
        val start = System.nanoTime()
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = PROBE_TIMEOUT_MS
                readTimeout = PROBE_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
        }.getOrElse {
            QmceLog.d(TAG, "rtt failed url=$url err=${it.message}")
            null
        }
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }.also {
                if (code !in 200..299) {
                    QmceLog.w(TAG, "httpGet code=$code url=$url body=${it?.take(200)}")
                }
            }?.takeIf { code in 200..299 }
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        private const val TAG = "QmceOta"
        private const val SERVER_BASE = "http://8.217.8.106:8080"
        private const val GITHUB_PROBE_URL = "https://api.github.com/"
        private const val GITHUB_RELEASES_URL =
            "https://api.github.com/repos/QMCE/qmce/releases/latest"
        private const val GITHUB_RTT_THRESHOLD_MS = 600L
        private const val PROBE_TIMEOUT_MS = 2_000
        private const val HTTP_TIMEOUT_MS = 12_000
        private val USER_AGENT = "QMCE/${BuildConfig.VERSION_NAME}"
    }
}
