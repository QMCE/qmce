package rj.qmce.lite.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback
import com.tencent.qqnt.kernel.nativeinterface.IKernelScanEndCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rj.qmce.lite.data.chat.RichMediaRepository
import rj.qmce.lite.kernel.KernelBridge
import java.io.File
import kotlin.coroutines.resume

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    data class CacheState(
        val kernelCacheBytes: Long? = null,
        val appCacheBytes: Long? = null,
        val scanning: Boolean = false,
        val clearing: Boolean = false,
        val statusMessage: String = "",
    ) {
        val totalCacheBytes: Long?
            get() {
                if (kernelCacheBytes == null && appCacheBytes == null) return null
                return (kernelCacheBytes ?: 0L) + (appCacheBytes ?: 0L)
            }
    }

    companion object {
        private const val TAG = "QMCE-Storage"
        private const val SCAN_TIMEOUT_MS = 20_000L
    }

    private val _state = MutableStateFlow(CacheState())
    val state: StateFlow<CacheState> = _state.asStateFlow()

    fun refreshCacheSizes() {
        if (_state.value.scanning || _state.value.clearing) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(scanning = true, statusMessage = "") }
            val (kernelBytes, appBytes) = coroutineScope {
                val kernelDeferred = async { scanKernelCacheBytes() }
                val appDeferred = async { measureAppCacheBytes() }
                kernelDeferred.await() to appDeferred.await()
            }
            _state.update {
                it.copy(
                    scanning = false,
                    kernelCacheBytes = kernelBytes,
                    appCacheBytes = appBytes,
                )
            }
            Log.d(
                TAG,
                "cache scan: kernel=${kernelBytes ?: -1}, app=$appBytes",
            )
        }
    }

    fun clearAllCache() {
        if (_state.value.clearing) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(clearing = true, statusMessage = "正在清理缓存…") }
            val appFreedBytes = coroutineScope {
                val appDeferred = async { clearAppCacheDir() }
                val kernelDeferred = async { clearKernelChatCache() }
                RichMediaRepository.invalidateAllRequests()
                val kernelOk = kernelDeferred.await()
                val appBytes = appDeferred.await()
                if (!kernelOk) {
                    Log.w(TAG, "kernel chat cache clear failed or unavailable")
                }
                appBytes to kernelOk
            }
            val (appBytes, kernelOk) = appFreedBytes
            _state.update {
                it.copy(
                    clearing = false,
                    kernelCacheBytes = if (kernelOk) 0L else it.kernelCacheBytes,
                    appCacheBytes = 0L,
                    statusMessage = when {
                        kernelOk ->
                            "缓存已清理（应用临时 ${formatBytes(appBytes)}，聊天媒体已清除）"
                        else ->
                            "应用临时已清理 ${formatBytes(appBytes)}，内核聊天缓存清理失败"
                    },
                )
            }
            if (!kernelOk) {
                refreshCacheSizes()
            }
        }
    }

    fun clearStatusMessage() {
        _state.update { it.copy(statusMessage = "") }
    }

    private suspend fun scanKernelCacheBytes(): Long? {
        val storageService = KernelBridge.getKernelService()?.getStorageCleanService()
            ?: return null
        return withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine<Long?> { continuation ->
                runCatching {
                    storageService.startScan(object : IKernelScanEndCallback {
                        override fun onResult(code: Int, sizes: ArrayList<Long>) {
                            if (!continuation.isActive) return
                            val total = sizes.sumOf { it.coerceAtLeast(0L) }
                            continuation.resume(
                                when {
                                    total > 0L -> total
                                    code == 0 -> 0L
                                    else -> null
                                },
                            )
                        }
                    })
                }.onFailure { error ->
                    Log.w(TAG, "scanCache failed", error)
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private suspend fun clearKernelChatCache(): Boolean {
        val storageService = KernelBridge.getKernelService()?.getStorageCleanService()
            ?: return false
        return withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                runCatching {
                    storageService.clearAllChatCacheInfo(object : IOperateCallback {
                        override fun onResult(code: Int, errMsg: String?) {
                            if (!continuation.isActive) return
                            Log.d(TAG, "clearAllChatCacheInfo: code=$code, errMsg=$errMsg")
                            continuation.resume(code == 0)
                        }
                    })
                }.onFailure { error ->
                    Log.w(TAG, "clearAllChatCacheInfo failed", error)
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        } ?: false
    }

    private fun measureAppCacheBytes(): Long =
        measureDirectorySize(getApplication<Application>().cacheDir)

    private fun clearAppCacheDir(): Long {
        val cacheDir = getApplication<Application>().cacheDir
        var freed = 0L
        cacheDir.listFiles()?.forEach { child ->
            freed += deleteRecursively(child)
        }
        return freed
    }

    private fun measureDirectorySize(root: File): Long {
        if (!root.exists()) return 0L
        return root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun deleteRecursively(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) {
            val size = file.length()
            return if (file.delete()) size else 0L
        }
        return file.listFiles()?.sumOf { deleteRecursively(it) } ?: 0L
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 0L) return "未知"
    if (bytes < 1024L) return "${bytes} B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex < 0) {
        "${bytes} B"
    } else {
        String.format("%.1f %s", value, units[unitIndex])
    }
}
