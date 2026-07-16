package rj.qmce.lite.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import com.tencent.mobileqq.ptt.IQQRecorder
import com.tencent.mobileqq.ptt.IQQRecorderUtils
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.utils.RecordParams
import com.tencent.mobileqq.utils.RecordParams.RecorderParam
import com.tencent.qqnt.watch.ptt.AudioFileWriterNT
import com.tencent.qqnt.watch.ptt.PttRecordCallback
import kotlinx.coroutines.delay
import mqq.app.MobileQQ
import java.io.File

private sealed interface VoiceRecordState {
    data object Idle : VoiceRecordState
    data object Recording : VoiceRecordState
    data object Finalizing : VoiceRecordState
    data class Ready(
        val file: File,
        val durationMillis: Long,
        val formatType: Int,
    ) : VoiceRecordState

    data class Error(val message: String) : VoiceRecordState
}

@Composable
fun VoiceRecordScreen(
    onSendVoice: (File, Long, Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var recorder by remember { mutableStateOf<IQQRecorder?>(null) }
    var recordState by remember { mutableStateOf<VoiceRecordState>(VoiceRecordState.Idle) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var discardWhenFinished by remember { mutableStateOf(false) }

    fun beginRecording() {
        if (recordState !is VoiceRecordState.Idle && recordState !is VoiceRecordState.Error) return
        runCatching {
            val recorderParam = RecordParams.b(MobileQQ.sMobileQQ.peekAppRuntime(), false)
            val baseDir = context.getExternalFilesDir("audio") ?: File(context.cacheDir, "audio")
            val recordingDir = File(baseDir, if (recorderParam.d == 1) "silk" else "amr")
            recordingDir.mkdirs()
            val outputPath = File(recordingDir, "voice_${System.currentTimeMillis()}").absolutePath
            val pcmPath = File(baseDir, "pcmforvad.pcm").absolutePath

            val callback = object : IQQRecorder.OnQQRecorderListener {
                override fun a() = Unit

                override fun b(
                    path: String?,
                    slice: ByteArray?,
                    size: Int,
                    maxAmplitude: Int,
                    time: Double,
                    recorderParam: RecorderParam?,
                ) = Unit

                override fun c(path: String?, recorderParam: RecorderParam?) = Unit
                override fun d(path: String?, recorderParam: RecorderParam?) = Unit
                override fun e(path: String?, recorderParam: RecorderParam?) = Unit
                override fun f(): Int = 250

                override fun g(path: String?, recorderParam: RecorderParam?, totalTime: Double) {
                    mainHandler.post {
                        recorder = null
                        val file = path?.let(::File)
                        if (discardWhenFinished) {
                            file?.delete()
                            discardWhenFinished = false
                            recordState = VoiceRecordState.Idle
                            elapsedMillis = 0L
                        } else if (file?.isFile == true && file.length() > 0L) {
                            recordState = VoiceRecordState.Ready(
                                file = file,
                                durationMillis = totalTime.toLong().coerceAtLeast(0L),
                                formatType = if (recorderParam?.d == 1) 1 else 0,
                            )
                        } else {
                            recordState = VoiceRecordState.Error("录音文件生成失败")
                        }
                    }
                }

                override fun h(path: String?, recorderParam: RecorderParam?, error: String?) {
                    mainHandler.post {
                        if (recordState is VoiceRecordState.Recording || recordState is VoiceRecordState.Finalizing) {
                            recorder = null
                            recordState = VoiceRecordState.Error(error?.takeIf(String::isNotBlank) ?: "录音失败")
                        }
                    }
                }

                override fun i(path: String?, recorderParam: RecorderParam?): Int = -1
                override fun j(state: Int) = Unit
            }
            val pttCallback = PttRecordCallback(null, AudioFileWriterNT(null)).apply {
                c = callback
            }
            val newRecorder = QRoute.api(IQQRecorderUtils::class.java).createQQRecorder(context)
            newRecorder.c(recorderParam)
            newRecorder.d(pcmPath)
            newRecorder.f(pttCallback)
            newRecorder.a(outputPath)
            recorder = newRecorder
            discardWhenFinished = false
            startedAt = SystemClock.elapsedRealtime()
            elapsedMillis = 0L
            recordState = VoiceRecordState.Recording
        }.onFailure {
            recorder = null
            recordState = VoiceRecordState.Error("无法开始录音: ${it.message ?: "未知错误"}")
        }
    }

    fun stopRecording(discard: Boolean) {
        if (recordState !is VoiceRecordState.Recording) return
        discardWhenFinished = discard
        recordState = VoiceRecordState.Finalizing
        val activeRecorder = recorder
        recorder = null
        runCatching { activeRecorder?.stop() }.onFailure {
            recordState = VoiceRecordState.Error("无法结束录音: ${it.message ?: "未知错误"}")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) beginRecording()
        else recordState = VoiceRecordState.Error("未获得录音权限")
    }

    LaunchedEffect(recordState, startedAt) {
        while (recordState is VoiceRecordState.Recording) {
            elapsedMillis = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            if (elapsedMillis >= 60_000L) {
                stopRecording(discard = false)
                break
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (recordState is VoiceRecordState.Recording || recordState is VoiceRecordState.Finalizing) {
                discardWhenFinished = true
                val activeRecorder = recorder
                recorder = null
                runCatching { activeRecorder?.stop() }
            }
        }
    }

    fun requestOrStartRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val stateLabel = when (val state = recordState) {
        VoiceRecordState.Idle -> "点击开始录音"
        VoiceRecordState.Recording -> "正在录音"
        VoiceRecordState.Finalizing -> "正在保存录音"
        is VoiceRecordState.Ready -> "录音已完成"
        is VoiceRecordState.Error -> state.message
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("语音消息", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (recordState is VoiceRecordState.Recording) formatVoiceRecordDuration(elapsedMillis) else stateLabel,
            color = if (recordState is VoiceRecordState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (recordState is VoiceRecordState.Recording) {
            Text("再次点击结束录音", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (recordState is VoiceRecordState.Ready) {
            val state = recordState as VoiceRecordState.Ready
            Text(
                text = formatVoiceRecordDuration(state.durationMillis),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(18.dp))

        when (recordState) {
            VoiceRecordState.Idle, is VoiceRecordState.Error -> {
                FilledIconButton(
                    onClick = ::requestOrStartRecording,
                    modifier = Modifier.touchTargetAwareSize(androidx.wear.compose.material3.IconButtonDefaults.LargeButtonSize),
                    colors = androidx.wear.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "开始录音")
                }
            }
            VoiceRecordState.Recording -> {
                FilledIconButton(
                    onClick = { stopRecording(discard = false) },
                    modifier = Modifier.touchTargetAwareSize(androidx.wear.compose.material3.IconButtonDefaults.LargeButtonSize),
                    colors = androidx.wear.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "结束录音")
                }
                Spacer(Modifier.height(8.dp))
                CompactButton(
                    onClick = { stopRecording(discard = true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    icon = { Icon(Icons.Default.Close, contentDescription = "取消录音") },
                )
            }
            VoiceRecordState.Finalizing -> CircularProgressIndicator(modifier = Modifier.size(42.dp), strokeWidth = 3.dp)
            is VoiceRecordState.Ready -> {
                val state = recordState as VoiceRecordState.Ready
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactButton(
                        onClick = {
                            state.file.delete()
                            recordState = VoiceRecordState.Idle
                            elapsedMillis = 0L
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "重录") },
                    )
                    CompactButton(
                        onClick = {
                            onSendVoice(state.file, state.durationMillis, state.formatType)
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送") },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        CompactButton(
            onClick = {
                if (recordState is VoiceRecordState.Recording) stopRecording(discard = true)
                onBack()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") },
        )
    }
}

private fun formatVoiceRecordDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
