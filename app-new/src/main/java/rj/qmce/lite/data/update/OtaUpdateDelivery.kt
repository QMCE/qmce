package rj.qmce.lite.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rj.qmce.lite.util.QmceLog
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class OtaDeliveryResult {
    data object Opened : OtaDeliveryResult()
    data class InstalledIntent(val file: File) : OtaDeliveryResult()
    data class Failed(val reason: String) : OtaDeliveryResult()
    data object Cancelled : OtaDeliveryResult()
}

object OtaUpdateDelivery {
    private const val TAG = "QmceOta"

    suspend fun openWatchBrowser(context: Context, url: String): OtaDeliveryResult =
        withContext(Dispatchers.Main) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                OtaDeliveryResult.Opened
            }.getOrElse {
                QmceLog.w(TAG, "watch browser open failed", it)
                OtaDeliveryResult.Failed("无法用手表浏览器打开")
            }
        }

    suspend fun openOnPhone(context: Context, url: String): OtaDeliveryResult =
        withContext(Dispatchers.Main) {
            runCatching {
                val helper = RemoteActivityHelper(context.applicationContext)
                val intent = Intent(Intent.ACTION_VIEW).setData(url.toUri())
                val future = helper.startRemoteActivity(intent)
                suspendCancellableCoroutine { cont ->
                    Futures.addCallback(
                        future,
                        object : FutureCallback<Void> {
                            override fun onSuccess(result: Void?) {
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onFailure(t: Throwable) {
                                if (cont.isActive) cont.resumeWithException(t)
                            }
                        },
                        MoreExecutors.directExecutor(),
                    )
                }
                OtaDeliveryResult.Opened
            }.getOrElse { phoneErr ->
                QmceLog.w(TAG, "open on phone failed; fallback watch", phoneErr)
                when (openWatchBrowser(context, url)) {
                    is OtaDeliveryResult.Opened -> OtaDeliveryResult.Opened
                    else -> OtaDeliveryResult.Failed("无法在手机或手表打开下载链接")
                }
            }
        }

    suspend fun downloadApk(
        context: Context,
        available: OtaCheckResult.Available,
        onProgress: (percent: Int) -> Unit,
        isCancelled: () -> Boolean,
    ): OtaDeliveryResult = withContext(Dispatchers.IO) {
        val url = available.downloadUrl
            ?: return@withContext OtaDeliveryResult.Failed("缺少下载地址")
        val dir = File(context.cacheDir, "ota").apply { mkdirs() }
        val out = File(dir, "update-${available.versionCode ?: "latest"}.apk")
        runCatching {
            if (out.exists()) out.delete()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "QMCE")
            }
            conn.inputStream.use { input ->
                val total = when {
                    available.size > 0L -> available.size
                    conn.contentLengthLong > 0L -> conn.contentLengthLong
                    else -> -1L
                }
                out.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var readTotal = 0L
                    var lastPct = -1
                    while (true) {
                        if (isCancelled()) throw CancellationException("cancelled")
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        val pct = if (total > 0) {
                            ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
            conn.disconnect()
            val expected = available.sha256?.trim()?.takeIf { it.isNotBlank() }
            if (expected != null) {
                val actual = sha256Hex(out)
                if (!actual.equals(expected, ignoreCase = true)) {
                    out.delete()
                    return@withContext OtaDeliveryResult.Failed("校验失败（sha256 不匹配）")
                }
            }
            OtaDeliveryResult.InstalledIntent(out)
        }.getOrElse {
            if (it is CancellationException) {
                out.delete()
                OtaDeliveryResult.Cancelled
            } else {
                QmceLog.w(TAG, "download failed", it)
                out.delete()
                OtaDeliveryResult.Failed(it.message ?: "下载失败")
            }
        }
    }

    fun launchSystemInstall(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }
}
