package rj.qmce.lite.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.touchTargetAwareSize
import com.tencent.mobileqq.ptt.IQQRecorder
import com.tencent.mobileqq.ptt.IQQRecorderUtils
import com.tencent.mobileqq.qroute.QRoute
import com.tencent.mobileqq.utils.RecordParams
import com.tencent.mobileqq.utils.RecordParams.RecorderParam
import com.tencent.qqnt.watch.ptt.AudioFileWriterNT
import com.tencent.qqnt.watch.ptt.PttRecordCallback
import com.tencent.qqnt.watch.ptt.api.ITranslateTextService
import kotlinx.coroutines.delay
import mqq.app.MobileQQ
import java.io.File

private sealed interface VoiceRecordState {
    data object Idle : VoiceRecordState
    data object Recording : VoiceRecordState
    data object Finalizing : VoiceRecordState
    data class Ready(
        val file: File,
        val pcmFile: File,
        val durationMillis: Long,
        val formatType: Int,
        val errorMessage: String? = null,
    ) : VoiceRecordState

    data class Translating(
        val file: File,
        val pcmFile: File,
        val durationMillis: Long,
        val formatType: Int,
        val text: String,
    ) : VoiceRecordState

    data class Transcribed(
        val file: File,
        val pcmFile: File,
        val durationMillis: Long,
        val formatType: Int,
        val text: String,
    ) : VoiceRecordState

    data class Error(val message: String) : VoiceRecordState
}

@Composable
fun VoiceRecordScreen(
    onSendVoice: (File, Long, Int) -> Unit,
    onBack: () -> Unit,
    onTranscribedText: (String) -> Unit = {},
    isGroup: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var recorder by remember { mutableStateOf<IQQRecorder?>(null) }
    var recordState by remember { mutableStateOf<VoiceRecordState>(VoiceRecordState.Idle) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var discardWhenFinished by remember { mutableStateOf(false) }
    var translationTaskKey by remember { mutableStateOf<String?>(null) }
    var translationRequestId by remember { mutableLongStateOf(0L) }

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
                                pcmFile = File(pcmPath),
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
                            recordState = VoiceRecordState.Error(
                                error?.takeIf(String::isNotBlank) ?: "录音失败"
                            )
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

    fun translationService(): ITranslateTextService? = runCatching {
        MobileQQ.sMobileQQ.peekAppRuntime()
            .getRuntimeService(ITranslateTextService::class.java, "")
    }.getOrNull()

    fun cancelTranslation() {
        translationRequestId++
        translationTaskKey?.let { key ->
            runCatching { translationService()?.cancelTask(key) }
        }
        translationTaskKey = null
        val state = recordState as? VoiceRecordState.Translating ?: return
        recordState = VoiceRecordState.Ready(
            file = state.file,
            pcmFile = state.pcmFile,
            durationMillis = state.durationMillis,
            formatType = state.formatType,
        )
    }

    fun startTranslation(state: VoiceRecordState.Ready) {
        if (!state.pcmFile.isFile || state.pcmFile.length() <= 0L) {
            recordState = state.copy(errorMessage = "转换所需的 PCM 文件不存在")
            return
        }
        val service = translationService()
        if (service == null) {
            recordState = state.copy(errorMessage = "语音转文字服务不可用")
            return
        }
        val requestId = translationRequestId + 1L
        translationRequestId = requestId
        translationTaskKey = null
        recordState = VoiceRecordState.Translating(
            file = state.file,
            pcmFile = state.pcmFile,
            durationMillis = state.durationMillis,
            formatType = state.formatType,
            text = "",
        )
        runCatching {
            service.translateText(
                isGroup,
                state.pcmFile,
                state.file,
                object : ITranslateTextService.AbsTranslateTextCallback() {
                    override fun b(
                        isSuccess: Boolean,
                        isLast: Boolean,
                        text: String,
                        curKey: String,
                    ) {
                        mainHandler.post {
                            if (requestId != translationRequestId) return@post
                            val current = recordState as? VoiceRecordState.Translating
                                ?: return@post
                            if (!isSuccess) {
                                translationTaskKey = null
                                recordState = VoiceRecordState.Ready(
                                    file = current.file,
                                    pcmFile = current.pcmFile,
                                    durationMillis = current.durationMillis,
                                    formatType = current.formatType,
                                    errorMessage = "转换失败，可重试或发送原语音",
                                )
                                return@post
                            }
                            val updatedText = text.trim()
                            if (isLast) {
                                translationTaskKey = null
                                recordState = if (updatedText.isBlank()) {
                                    VoiceRecordState.Ready(
                                        file = current.file,
                                        pcmFile = current.pcmFile,
                                        durationMillis = current.durationMillis,
                                        formatType = current.formatType,
                                        errorMessage = "没有识别到内容，可重试或发送原语音",
                                    )
                                } else {
                                    VoiceRecordState.Transcribed(
                                        file = current.file,
                                        pcmFile = current.pcmFile,
                                        durationMillis = current.durationMillis,
                                        formatType = current.formatType,
                                        text = updatedText,
                                    )
                                }
                            } else {
                                recordState = current.copy(text = updatedText)
                            }
                        }
                    }
                },
            ).also { key -> translationTaskKey = key }
        }.onFailure {
            if (requestId == translationRequestId) {
                translationTaskKey = null
                recordState = VoiceRecordState.Ready(
                    file = state.file,
                    pcmFile = state.pcmFile,
                    durationMillis = state.durationMillis,
                    formatType = state.formatType,
                    errorMessage = "转换失败，可重试或发送原语音",
                )
            }
        }
    }

    fun sendReadyVoice(state: VoiceRecordState.Ready) {
        onSendVoice(state.file, state.durationMillis, state.formatType)
        state.pcmFile.delete()
        onBack()
    }

    fun useTranscribedText(state: VoiceRecordState.Transcribed) {
        state.file.delete()
        state.pcmFile.delete()
        onTranscribedText(state.text)
        onBack()
    }

    fun resetReadyState(state: VoiceRecordState.Ready) {
        state.file.delete()
        state.pcmFile.delete()
        recordState = VoiceRecordState.Idle
        elapsedMillis = 0L
    }

    fun resetTranscribedState(state: VoiceRecordState.Transcribed) {
        state.file.delete()
        state.pcmFile.delete()
        recordState = VoiceRecordState.Idle
        elapsedMillis = 0L
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
            translationRequestId++
            translationTaskKey?.let { key ->
                runCatching { translationService()?.cancelTask(key) }
            }
            translationTaskKey = null
            if (recordState is VoiceRecordState.Recording || recordState is VoiceRecordState.Finalizing) {
                discardWhenFinished = true
                val activeRecorder = recorder
                recorder = null
                runCatching { activeRecorder?.stop() }
            }
            when (val state = recordState) {
                is VoiceRecordState.Ready -> state.pcmFile.delete()
                is VoiceRecordState.Translating -> state.pcmFile.delete()
                is VoiceRecordState.Transcribed -> state.pcmFile.delete()
                else -> Unit
            }
        }
    }

    fun requestOrStartRecording() {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            beginRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val stateLabel = when (val state = recordState) {
        VoiceRecordState.Idle -> "点击开始录音"
        VoiceRecordState.Recording -> "正在录音"
        VoiceRecordState.Finalizing -> "正在保存录音"
        is VoiceRecordState.Ready -> state.errorMessage ?: "录音已完成"
        is VoiceRecordState.Translating -> "正在转文字"
        is VoiceRecordState.Transcribed -> "转写完成"
        is VoiceRecordState.Error -> state.message
    }
    val hasError = recordState is VoiceRecordState.Error ||
        (recordState as? VoiceRecordState.Ready)?.errorMessage != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (recordState is VoiceRecordState.Recording) formatVoiceRecordDuration(
                elapsedMillis
            ) else stateLabel,
            color = if (hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (recordState is VoiceRecordState.Recording) {
            Text(
                "再次点击结束录音",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (recordState is VoiceRecordState.Ready) {
            val state = recordState as VoiceRecordState.Ready
            Text(
                text = formatVoiceRecordDuration(state.durationMillis),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (recordState is VoiceRecordState.Translating) {
            val state = recordState as VoiceRecordState.Translating
            if (state.text.isNotBlank()) {
                Text(
                    text = state.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (recordState is VoiceRecordState.Transcribed) {
            Text(
                text = (recordState as VoiceRecordState.Transcribed).text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
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

            VoiceRecordState.Finalizing -> CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                strokeWidth = 3.dp
            )

            is VoiceRecordState.Ready -> {
                val state = recordState as VoiceRecordState.Ready
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactButton(
                        onClick = { resetReadyState(state) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        icon = { Icon(Icons.Default.Refresh, contentDescription = "重录") },
                    )
                    CompactButton(
                        onClick = { startTranslation(state) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        icon = { Icon(Icons.Default.TextFields, contentDescription = "转文字") },
                    )
                    CompactButton(
                        onClick = { sendReadyVoice(state) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送"
                            )
                        },
                    )
                }
            }

            is VoiceRecordState.Translating -> {
                val state = recordState as VoiceRecordState.Translating
                CircularProgressIndicator(
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactButton(
                        onClick = { cancelTranslation() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        icon = { Icon(Icons.Default.Close, contentDescription = "取消转文字") },
                    )
                    CompactButton(
                        onClick = {
                            sendReadyVoice(
                                VoiceRecordState.Ready(
                                    file = state.file,
                                    pcmFile = state.pcmFile,
                                    durationMillis = state.durationMillis,
                                    formatType = state.formatType,
                                ),
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送原语音") },
                    )
                }
            }

            is VoiceRecordState.Transcribed -> {
                val state = recordState as VoiceRecordState.Transcribed
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactButton(
                        onClick = { resetTranscribedState(state) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        icon = { Icon(Icons.Default.Refresh, contentDescription = "重录") },
                    )
                    CompactButton(
                        onClick = { useTranscribedText(state) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        icon = { Icon(Icons.Default.Check, contentDescription = "使用文字") },
                    )
                    CompactButton(
                        onClick = {
                            sendReadyVoice(
                                VoiceRecordState.Ready(
                                    file = state.file,
                                    pcmFile = state.pcmFile,
                                    durationMillis = state.durationMillis,
                                    formatType = state.formatType,
                                ),
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送原语音") },
                    )
                }
            }
        }
    }
}

private fun formatVoiceRecordDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
