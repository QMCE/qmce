package rj.qmce.lite.ui.call

import android.content.pm.PackageManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.tencent.av.opengl.ui.GLRootView
import kotlinx.coroutines.delay
import rj.qmce.lite.data.call.CallMode
import rj.qmce.lite.data.call.CallPhase
import rj.qmce.lite.data.call.CallUiState
import rj.qmce.lite.data.call.QmceCallController
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportLifecycle
import rj.qmce.lite.data.reporting.OfficialReportTargetBox

@Composable
fun QmceCallScreen(onFinish: () -> Unit) {
    val state by QmceCallController.state.collectAsState()
    val peer = state.peer
    val context = LocalContext.current
    var incomingSession by remember { mutableStateOf(state.phase == CallPhase.Incoming) }
    LaunchedEffect(state.phase) {
        if (state.phase == CallPhase.Incoming) incomingSession = true
    }
    val reportPageId = when {
        state.phase == CallPhase.Incoming -> OfficialReportBridge.PageIds.INVITED_INTERFACE
        incomingSession -> OfficialReportBridge.PageIds.VOICE
        state.phase == CallPhase.Idle -> null
        else -> OfficialReportBridge.PageIds.DIAL_INTERFACE
    }
    OfficialReportLifecycle(
        pageId = reportPageId,
        params = mapOf("page_model" to if (state.mode.isOnlyAudio) 1 else 2),
    )
    val permissionResultHandler = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val hasAllPermissions =
            QmceCallController.requiredPermissions(state.mode).all { permission ->
                grants[permission] == true ||
                        ContextCompat.checkSelfPermission(
                            context,
                            permission
                        ) == PackageManager.PERMISSION_GRANTED
            }
        if (hasAllPermissions) {
            QmceCallController.acceptIncoming()
        } else {
            QmceCallController.rejectIncoming()
            onFinish()
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == CallPhase.Ended) {
            delay(900)
            onFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (state.mode == CallMode.Video && peer != null && state.phase in videoPhases) {
            CallVideoSurface(
                peerUin = peer.uin,
                localHasVideo = state.hasLocalVideo,
                remoteHasVideo = state.hasRemoteVideo,
                channelReady = state.channelReady,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            CallIdentity(state)
            CallControls(
                state = state,
                onFinish = onFinish,
                onAcceptIncoming = {
                    QmceCallController.silenceIncomingAlert()
                    val missingPermissions =
                        QmceCallController.requiredPermissions(state.mode).filter { permission ->
                            ContextCompat.checkSelfPermission(
                                context,
                                permission
                            ) != PackageManager.PERMISSION_GRANTED
                        }
                    if (missingPermissions.isEmpty()) {
                        QmceCallController.acceptIncoming()
                    } else {
                        permissionResultHandler.launch(missingPermissions.toTypedArray())
                    }
                },
            )
        }
    }
}

@Composable
private fun CallIdentity(state: CallUiState) {
    val peer = state.peer
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = peer?.name?.take(1)?.uppercase().orEmpty().ifBlank { "Q" },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = peer?.name ?: "QQ 通话",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = callStatus(state),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CallControls(
    state: CallUiState,
    onFinish: () -> Unit,
    onAcceptIncoming: () -> Unit,
) {
    if (state.phase == CallPhase.Incoming) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OfficialReportTargetBox(
                key = "call:reject",
                elementId = OfficialReportBridge.ElementIds.HANG_UP,
            ) { reportTarget ->
                ControlButton(
                    text = "拒绝",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = {
                        OfficialReportBridge.reportElementClick(
                            target = reportTarget,
                            elementId = OfficialReportBridge.ElementIds.HANG_UP,
                        )
                        QmceCallController.rejectIncoming()
                        onFinish()
                    },
                )
            }
            OfficialReportTargetBox(
                key = "call:answer",
                elementId = OfficialReportBridge.ElementIds.ANSWER_CALL,
            ) { reportTarget ->
                ControlButton(
                    text = "接听",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = {
                        OfficialReportBridge.reportElementClick(
                            target = reportTarget,
                            elementId = OfficialReportBridge.ElementIds.ANSWER_CALL,
                        )
                        onAcceptIncoming()
                    },
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OfficialReportTargetBox(
                key = "call:microphone",
                elementId = OfficialReportBridge.ElementIds.MICROPHONE,
            ) { reportTarget ->
                ControlButton(
                    text = if (state.isMuted) "开麦" else "静音",
                    onClick = {
                        OfficialReportBridge.reportElementClick(
                            target = reportTarget,
                            elementId = OfficialReportBridge.ElementIds.MICROPHONE,
                        )
                        QmceCallController.toggleMute()
                    },
                )
            }
            ControlButton(
                text = if (state.isSpeakerOn) "听筒" else "扬声器",
                onClick = QmceCallController::toggleSpeaker,
            )
            if (state.mode == CallMode.Video) {
                OfficialReportTargetBox(
                    key = "call:camera",
                    elementId = OfficialReportBridge.ElementIds.CAMERA,
                ) { reportTarget ->
                    ControlButton(
                        text = if (state.hasLocalVideo) "关视频" else "开视频",
                        onClick = {
                            OfficialReportBridge.reportElementClick(
                                target = reportTarget,
                                elementId = OfficialReportBridge.ElementIds.CAMERA,
                            )
                            QmceCallController.toggleVideo()
                        },
                    )
                }
                ControlButton(
                    text = "翻转",
                    onClick = QmceCallController::switchCamera,
                )
            }
        }
        OfficialReportTargetBox(
            key = "call:hang-up",
            elementId = OfficialReportBridge.ElementIds.HANG_UP,
        ) { reportTarget ->
            ControlButton(
                text = if (state.phase == CallPhase.Ending) "挂断中" else "挂断",
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                enabled = state.phase != CallPhase.Ending,
                onClick = {
                    OfficialReportBridge.reportElementClick(
                        target = reportTarget,
                        elementId = OfficialReportBridge.ElementIds.HANG_UP,
                    )
                    QmceCallController.hangUp()
                },
            )
        }
    }
}

@Composable
private fun ControlButton(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    CompactButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        label = { Text(text) },
    )
}

@Composable
private fun CallVideoSurface(
    peerUin: String,
    localHasVideo: Boolean,
    remoteHasVideo: Boolean,
    channelReady: Boolean,
) {
    val context = LocalContext.current
    val selfUin = remember {
        QmceCallController.currentSelfUin()
    }
    var layer by remember { mutableStateOf<QmceVideoLayer?>(null) }

    AndroidView(
        factory = { viewContext ->
            GLRootView(viewContext, null).also { root ->
                QmceVideoLayer(viewContext).also { createdLayer ->
                    createdLayer.bind(selfUin, peerUin)
                    root.setContentPane(createdLayer)
                    createdLayer.updateMediaState(
                        localHasVideo = localHasVideo,
                        remoteHasVideo = remoteHasVideo,
                        channelReady = channelReady,
                    )
                    layer = createdLayer
                }
            }
        },
        update = {
            layer?.updateMediaState(
                localHasVideo = localHasVideo,
                remoteHasVideo = remoteHasVideo,
                channelReady = channelReady,
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
    DisposableEffect(context, layer) {
        onDispose {
            layer?.let { currentLayer ->
                runCatching { currentLayer.release() }
            }
        }
    }
}

@Composable
private fun callStatus(state: CallUiState): String {
    val connectedAt = state.connectedAtElapsedRealtime
    var elapsed by remember(connectedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(connectedAt) {
        if (connectedAt == null) return@LaunchedEffect
        while (true) {
            elapsed = SystemClock.elapsedRealtime() - connectedAt
            delay(1_000)
        }
    }
    return when (state.phase) {
        CallPhase.Idle -> ""
        CallPhase.Outgoing -> if (state.mode == CallMode.Voice) "正在呼叫…" else "正在发起视频通话…"
        CallPhase.Incoming -> if (state.mode == CallMode.Voice) "邀请你语音通话" else "邀请你视频通话"
        CallPhase.Connecting -> "正在连接…"
        CallPhase.Active -> formatDuration(elapsed)
        CallPhase.Ending -> "正在挂断…"
        CallPhase.Ended -> state.endMessage.orEmpty()
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private val videoPhases = setOf(
    CallPhase.Outgoing,
    CallPhase.Connecting,
    CallPhase.Active,
    CallPhase.Ending,
)
