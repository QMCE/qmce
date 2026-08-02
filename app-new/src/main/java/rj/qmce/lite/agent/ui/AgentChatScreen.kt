package rj.qmce.lite.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.agent.AgentRunStatus
import rj.qmce.lite.agent.AgentSession
import rj.qmce.lite.agent.AgentUiMsg
import rj.qmce.lite.ui.wear.QmceScreenScaffold

private val AgentBubbleRadius = RoundedCornerShape(18.dp)
private val AgentBubbleMaxWidth = 280.dp

/**
 * Chat screen for the Agent pseudo contact. Renders the Agent conversation
 * from [AgentSession.uiMessages]; input and voice go through the shared
 * routes (reusing ChatInputScreen / VoiceRecordScreen).
 */
@Composable
fun AgentChatScreen(
    onOpenInput: () -> Unit,
    onBack: () -> Unit,
) {
    val uiMessages by AgentSession.uiMessages.collectAsState()
    val runStatus by AgentSession.runStatus.collectAsState()

    LaunchedEffect(Unit) { AgentSession.setInChat(true) }
    DisposableEffect(Unit) { onDispose { AgentSession.setInChat(false) } }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    QmceScreenScaffold(
        scrollState = listState,
        edgeButton = {
            Button(
                onClick = onOpenInput,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) { Text("输入") }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "agent-header") {
                AgentHeader(runStatus = runStatus)
            }
            items(
                items = uiMessages,
                key = { it.stableKey },
            ) { message ->
                AgentBubble(message)
            }
            if (runStatus == AgentRunStatus.Running) {
                item(key = "agent-typing") {
                    Text(
                        text = "Agent 思考中…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentHeader(runStatus: AgentRunStatus) {
    val title = when (runStatus) {
        AgentRunStatus.Idle -> "Fluoxetine"
        AgentRunStatus.Running -> "Fluoxetine 思考中…"
        AgentRunStatus.WaitingApproval -> "等待你批准操作…"
    }
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun AgentBubble(message: AgentUiMsg) {
    val containerColor = if (message.isSelf) {
        MaterialTheme.colorScheme.primary
    } else if (message.isSystem) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (message.isSelf) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val alignment = if (message.isSelf) Arrangement.End else Arrangement.Start
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = alignment,
    ) {
        Column(
            modifier = Modifier.widthIn(max = AgentBubbleMaxWidth),
        ) {
            Text(
                text = message.text,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                maxLines = if (message.streaming) 12 else 40,
                modifier = Modifier
                    .background(containerColor, AgentBubbleRadius)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
