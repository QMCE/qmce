package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.ui.wear.QmceScreenScaffold
import rj.qmce.lite.ui.wear.SettingsListHeader
import rj.qmce.lite.ui.wear.SettingsNavButton
import rj.qmce.lite.ui.wear.SettingsSubHeader
import rj.qmce.lite.ui.wear.SettingsSwitch
import rj.qmce.lite.viewmodel.SettingsViewModel

@Composable
fun IntelligenceSettingsScreen(
    settingsVm: SettingsViewModel,
    onOpenAgentDetails: () -> Unit,
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
            item(key = "intel-header") {
                SettingsListHeader(
                    text = "智能",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "sec-ai") {
                SettingsSubHeader(
                    text = "AI",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "ai-builtin-hint") {
                Text(
                    "默认使用内置模型。启用自定义后需同时填写 Base URL、API Key 与 Model。",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item(key = "ai-custom-toggle") {
                SettingsSwitch(
                    checked = settings.aiCustomEnabled,
                    onCheckedChange = settingsVm::setAiCustomEnabled,
                    label = "启用自定义模型",
                    secondaryLabel = "覆盖内置 OpenAI 兼容接口",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            if (settings.aiCustomEnabled) {
                item(key = "ai-base-url") {
                    IntelligenceTextField(
                        label = "Base URL",
                        value = settings.aiBaseUrl,
                        onValueChange = settingsVm::setAiBaseUrl,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                    )
                }
                item(key = "ai-api-key") {
                    IntelligenceTextField(
                        label = "API Key",
                        value = settings.aiApiKey,
                        onValueChange = settingsVm::setAiApiKey,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                    )
                }
                item(key = "ai-model") {
                    IntelligenceTextField(
                        label = "Model",
                        value = settings.aiModel,
                        onValueChange = settingsVm::setAiModel,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                    )
                }
            }
            item(key = "sec-agent") {
                SettingsSubHeader(
                    text = "智能体",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "agent-enable") {
                SettingsSwitch(
                    checked = settings.agentEnabled,
                    onCheckedChange = settingsVm::setAgentEnabled,
                    label = "启用 Fluoxetine",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "agent-send-packet") {
                SettingsSwitch(
                    checked = settings.agentSendPacketEnabled,
                    onCheckedChange = settingsVm::setAgentSendPacketEnabled,
                    enabled = settings.agentEnabled,
                    label = "允许 send_packet",
                    secondaryLabel = "高风险，默认关闭",
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
            item(key = "agent-details") {
                SettingsNavButton(
                    icon = Icons.Default.SmartToy,
                    title = "工具与说明",
                    subtitle = "查看可执行操作与批准策略",
                    onClick = onOpenAgentDetails,
                    transformation = SurfaceTransformation(transformationSpec),
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}

@Composable
private fun IntelligenceTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
