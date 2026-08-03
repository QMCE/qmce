package rj.qmce.lite.agent.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import rj.qmce.lite.agent.ApprovalController

/**
 * Global approval dialog host. Rendered once in MainActivity, outside the
 * NavHost. Shows the current pending write-tool request and lets the user
 * allow/deny it. Mirrors OtaUpdateDialogHost's collectAsState + AlertDialog.
 */
@Composable
fun AgentApprovalDialogHost() {
    val pending by ApprovalController.pending.collectAsState()
    val current = pending.firstOrNull() ?: return
    val total = pending.size
    val index = 1

    AlertDialog(
        visible = true,
        onDismissRequest = { ApprovalController.decide(current.id, allow = false) },
        title = {
            Text(
                if (total > 1) "Fluoxetine 请求批准（$index/$total）"
                else "Fluoxetine 请求批准",
            )
        },
        text = {
            Text(
                buildString {
                    append(current.toolName)
                    append("\n")
                    append(current.summary.take(160))
                },
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = {
            item {
                Button(
                    onClick = { ApprovalController.decide(current.id, allow = true) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text("允许") }
            }
            item {
                Button(
                    onClick = { ApprovalController.decide(current.id, allow = false) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                ) { Text("拒绝") }
            }
        },
    )
}
