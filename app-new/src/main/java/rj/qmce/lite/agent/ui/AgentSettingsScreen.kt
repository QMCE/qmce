package rj.qmce.lite.agent.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.agent.KernelToolRegistry
import rj.qmce.lite.ui.wear.QmceListHeader
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import rj.qmce.lite.viewmodel.SettingsViewModel

/**
 * Agent settings screen: master toggle + the list of exposed kernel tools
 * (read-only vs approval-required).
 */
@Composable
fun AgentSettingsScreen(
    settingsVm: SettingsViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val settings by settingsVm.settings.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val scheme = MaterialTheme.colorScheme

    QmceScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "agent-header") {
                QmceListHeader(
                    text = "Fluoxetine智能体",
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            item(key = "agent-hint") {
                Text(
                    "Agent 像一个内置联系人，可发文字/语音指令。写操作（发消息、撤回、群管理等）会先征求你的批准；只读查询自动执行。使用 AI 接入里配置的模型。",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item(key = "agent-enable") {
                SwitchButton(
                    checked = settings.agentEnabled,
                    onCheckedChange = settingsVm::setAgentEnabled,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) { Text("启用 Fluoxetine") }
            }
            item(key = "agent-tools-header") {
                QmceListHeader(
                    text = "已暴露工具",
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
            val tools = KernelToolRegistry.all().sortedBy { it.name }
            items(
                items = tools,
                key = { it.name },
            ) { tool ->
                Text(
                    text = buildString {
                        append(tool.name)
                        append("  ·  ")
                        append(if (tool.requiresApproval) "需批准" else "只读")
                        if (tool.isEventMonitor) append(" · 事件")
                        if (tool.isTimer) append(" · 计时")
                    },
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                )
            }
        }
    }
}
