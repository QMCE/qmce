package rj.qmce.lite.data.update

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rj.qmce.lite.util.QmceLog
import rj.qmce.lite.viewmodel.SettingsViewModel

sealed class OtaUiState {
    data object Idle : OtaUiState()
    data object NeedWifi : OtaUiState()
    data object Checking : OtaUiState()
    data object Probing : OtaUiState()
    data class ProbeDone(val report: OtaLatencyReport) : OtaUiState()
    data class Confirm(val available: OtaCheckResult.Available) : OtaUiState()
    data class PickMethod(val available: OtaCheckResult.Available) : OtaUiState()
    data class Downloading(
        val available: OtaCheckResult.Available,
        val percent: Int,
        val indeterminate: Boolean,
        val forceUpdate: Boolean,
    ) : OtaUiState()
    data class DownloadFailed(val reason: String) : OtaUiState()
    data class Status(val message: String) : OtaUiState()
}

/**
 * App-scoped OTA dialog/download coordinator. Foreground shows Dialog progress;
 * background shows progress notification / Live Updates.
 */
object OtaUpdateSession {
    private const val TAG = "QmceOta"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val checker = DualSourceOtaUpdateChecker()

    private val _ui = MutableStateFlow<OtaUiState>(OtaUiState.Idle)
    val ui: StateFlow<OtaUiState> = _ui.asStateFlow()

    @Volatile private var app: Application? = null
    @Volatile private var foreground = true
    @Volatile private var cancelRequested = false
    private var downloadJob: Job? = null
    private var pendingAvailable: OtaCheckResult.Available? = null
    private var lastPercent: Int = 0
    private var lastIndeterminate: Boolean = true
    private var lifecycleBound = false

    fun ensure(application: Application) {
        app = application
        if (lifecycleBound) return
        lifecycleBound = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
                onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
                onBackground()
            }
        })
    }

    fun dismiss() {
        val s = _ui.value
        if (s is OtaUiState.Downloading) return
        if (s is OtaUiState.Confirm && s.available.forceUpdate) return
        if (s is OtaUiState.PickMethod && s.available.forceUpdate) return
        if (s is OtaUiState.Checking || s is OtaUiState.Probing) return
        _ui.value = OtaUiState.Idle
        if (s !is OtaUiState.PickMethod) {
            pendingAvailable = null
        }
    }

    fun openWifiSettings() {
        app?.let { OtaNetworkGate.openWifiSettings(it) }
        _ui.value = OtaUiState.Idle
    }

    fun checkForUpdate() {
        val context = app ?: return
        if (!OtaNetworkGate.mayProceed(context)) {
            _ui.value = OtaUiState.NeedWifi
            return
        }
        val mode = readSourceMode(context)
        _ui.value = OtaUiState.Checking
        scope.launch {
            val result = checker.checkForUpdate(mode)
            QmceLog.important(TAG, "check result=$result mode=$mode")
            _ui.value = when (result) {
                is OtaCheckResult.UpToDate -> OtaUiState.Status("已是最新版本")
                is OtaCheckResult.Available -> {
                    pendingAvailable = result
                    OtaUiState.Confirm(result)
                }
                is OtaCheckResult.Unavailable -> OtaUiState.Status(result.reason)
            }
        }
    }

    fun probeLatencies() {
        val context = app ?: return
        if (!OtaNetworkGate.mayProceed(context)) {
            _ui.value = OtaUiState.NeedWifi
            return
        }
        _ui.value = OtaUiState.Probing
        scope.launch {
            val report = checker.probeLatencies()
            QmceLog.d(TAG, "probe ${report.summary()}")
            _ui.value = OtaUiState.ProbeDone(report)
        }
    }

    fun confirmContinue() {
        val available = pendingAvailable ?: (_ui.value as? OtaUiState.Confirm)?.available ?: return
        pendingAvailable = available
        _ui.value = OtaUiState.PickMethod(available)
    }

    fun confirmCancel() {
        val available = (_ui.value as? OtaUiState.Confirm)?.available
        if (available?.forceUpdate == true) return
        pendingAvailable = null
        _ui.value = OtaUiState.Idle
    }

    fun pickMethod(mode: OtaDownloadMode) {
        val context = app ?: return
        val available = pendingAvailable
            ?: (_ui.value as? OtaUiState.PickMethod)?.available
            ?: return
        SettingsViewModel.setOtaLastDownloadMode(context, mode)
        val url = available.downloadUrl
        if (url.isNullOrBlank()) {
            _ui.value = OtaUiState.Status("缺少下载地址")
            return
        }
        when (mode) {
            OtaDownloadMode.WatchBrowser -> scope.launch {
                _ui.value = when (val r = OtaUpdateDelivery.openWatchBrowser(context, url)) {
                    is OtaDeliveryResult.Opened -> OtaUiState.Status("已在手表浏览器打开")
                    is OtaDeliveryResult.Failed -> OtaUiState.Status(r.reason)
                    else -> OtaUiState.Status("已处理")
                }
            }
            OtaDownloadMode.Phone -> scope.launch {
                _ui.value = when (val r = OtaUpdateDelivery.openOnPhone(context, url)) {
                    is OtaDeliveryResult.Opened -> OtaUiState.Status("已请求在手机打开")
                    is OtaDeliveryResult.Failed -> OtaUiState.Status(r.reason)
                    else -> OtaUiState.Status("已处理")
                }
            }
            OtaDownloadMode.InApp -> startInAppDownload(available)
        }
    }

    fun cancelDownload() {
        val s = _ui.value
        if (s is OtaUiState.Downloading && s.forceUpdate) return
        // Only set the flag; do not cancel the Job so downloadApk can return Cancelled
        // and the session can update dialog / notification consistently.
        cancelRequested = true
    }

    private fun startInAppDownload(available: OtaCheckResult.Available) {
        val context = app ?: return
        if (!OtaNetworkGate.mayProceed(context)) {
            pendingAvailable = available
            _ui.value = OtaUiState.NeedWifi
            return
        }
        if (downloadJob?.isActive == true) return
        cancelRequested = false
        lastPercent = 0
        lastIndeterminate = true
        pendingAvailable = available
        _ui.value = OtaUiState.Downloading(
            available = available,
            percent = 0,
            indeterminate = true,
            forceUpdate = available.forceUpdate,
        )
        downloadJob = scope.launch {
            val result = OtaUpdateDelivery.downloadApk(
                context = context,
                available = available,
                onProgress = { pct ->
                    val indeterminate = pct < 0
                    val percent = pct.coerceAtLeast(0)
                    lastPercent = percent
                    lastIndeterminate = indeterminate
                    if (foreground) {
                        OtaProgressNotifier.cancel(context)
                        _ui.value = OtaUiState.Downloading(
                            available = available,
                            percent = percent,
                            indeterminate = indeterminate,
                            forceUpdate = available.forceUpdate,
                        )
                    } else {
                        OtaProgressNotifier.showProgress(context, percent, indeterminate)
                    }
                },
                isCancelled = { cancelRequested },
            )
            when (result) {
                is OtaDeliveryResult.InstalledIntent -> onDownloadFinished(result.file)
                is OtaDeliveryResult.Cancelled -> {
                    OtaProgressNotifier.cancel(context)
                    _ui.value = OtaUiState.Status("已取消下载")
                }
                is OtaDeliveryResult.Failed -> {
                    if (foreground) {
                        OtaProgressNotifier.cancel(context)
                        _ui.value = OtaUiState.DownloadFailed(result.reason)
                    } else {
                        OtaProgressNotifier.showFailed(context, result.reason)
                        _ui.value = OtaUiState.Idle
                    }
                }
                else -> _ui.value = OtaUiState.Idle
            }
        }
    }

    private fun onDownloadFinished(file: File) {
        val context = app ?: return
        QmceLog.important(TAG, "ota download complete path=${file.absolutePath}")
        if (foreground) {
            OtaProgressNotifier.cancel(context)
            _ui.value = OtaUiState.Status("下载完成，正在安装…")
            runCatching { OtaUpdateDelivery.launchSystemInstall(context, file) }
                .onFailure {
                    QmceLog.w(TAG, "install launch failed", it)
                    _ui.value = OtaUiState.DownloadFailed(it.message ?: "无法启动安装")
                }
        } else {
            OtaProgressNotifier.showCompleted(context, file)
            _ui.value = OtaUiState.Idle
        }
    }

    private fun onForeground() {
        val context = app ?: return
        val s = _ui.value
        if (s is OtaUiState.Downloading || downloadJob?.isActive == true) {
            OtaProgressNotifier.cancel(context)
            val available = pendingAvailable ?: (s as? OtaUiState.Downloading)?.available ?: return
            _ui.value = OtaUiState.Downloading(
                available = available,
                percent = lastPercent,
                indeterminate = lastIndeterminate,
                forceUpdate = available.forceUpdate,
            )
        }
    }

    private fun onBackground() {
        val context = app ?: return
        val s = _ui.value
        if (s is OtaUiState.Downloading || downloadJob?.isActive == true) {
            OtaProgressNotifier.showProgress(context, lastPercent, lastIndeterminate)
        }
    }

    private fun readSourceMode(context: android.content.Context): OtaSourceMode =
        OtaSourceMode.fromPref(
            context.getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, 0)
                .getString(SettingsViewModel.KEY_OTA_SOURCE_MODE, OtaSourceMode.Auto.pref),
        )
}
