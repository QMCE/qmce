package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.viewmodel.ChatDetailViewModel

@Composable
internal fun MessageSummaryScreen(
    state: ChatDetailViewModel.MessageSummaryState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val selectedCount = when (state) {
        ChatDetailViewModel.MessageSummaryState.Idle -> 0
        is ChatDetailViewModel.MessageSummaryState.Loading -> state.selectedCount
        is ChatDetailViewModel.MessageSummaryState.Success -> state.selectedCount
        is ChatDetailViewModel.MessageSummaryState.Error -> state.selectedCount
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "summary-back") {
                CompactButton(
                    onClick = onBack,
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    icon = { Icon(Icons.Default.Close, contentDescription = "关闭总结") },
                )
            }
            item(key = "summary-title") {
                Text(
                    text = if (selectedCount > 0) "AI总结 · ${selectedCount}条" else "AI总结",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            when (state) {
                ChatDetailViewModel.MessageSummaryState.Idle -> Unit
                is ChatDetailViewModel.MessageSummaryState.Loading -> {
                    item(key = "summary-loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在总结…")
                        }
                    }
                    if (state.text.isNotBlank()) {
                        summaryTextItem(state.text, transformationSpec)
                    }
                }

                is ChatDetailViewModel.MessageSummaryState.Success -> {
                    summaryTextItem(state.text, transformationSpec)
                    item(key = "summary-done") {
                        Button(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("完成")
                        }
                    }
                }

                is ChatDetailViewModel.MessageSummaryState.Error -> {
                    item(key = "summary-error") {
                        Text(
                            text = state.message,
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (state.retryable) {
                        item(key = "summary-retry") {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                transformation = SurfaceTransformation(transformationSpec),
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun TransformingLazyColumnScope.summaryTextItem(
    text: String,
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
    key: String = "summary-content",
) {
    item(key = key) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .transformedHeight(this, transformationSpec)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
