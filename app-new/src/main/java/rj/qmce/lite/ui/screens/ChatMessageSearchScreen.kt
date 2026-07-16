package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import androidx.compose.material3.TextField as MaterialTextField
import androidx.compose.material3.TextFieldDefaults as MaterialTextFieldDefaults

@Composable
fun ChatMessageSearchScreen(
    query: String,
    matches: List<ChatDetailViewModel.UiMsg>,
    isLoadingOlder: Boolean,
    canLoadOlder: Boolean,
    onQueryChange: (String) -> Unit,
    onLoadOlder: () -> Unit,
    onSelect: (ChatDetailViewModel.UiMsg) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val scheme = MaterialTheme.colorScheme
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "message-search-input") {
                MaterialTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    placeholder = {
                        Text(
                            "搜索当前已加载消息",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    shape = CircleShape,
                    colors = MaterialTextFieldDefaults.colors(
                        focusedContainerColor = scheme.surfaceContainerHigh,
                        unfocusedContainerColor = scheme.surfaceContainerHigh,
                        focusedTextColor = scheme.onSurface,
                        unfocusedTextColor = scheme.onSurface,
                        cursorColor = scheme.primary,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
            }
            if (query.isBlank()) {
                item(key = "message-search-hint") {
                    ChatMessageSearchHint(
                        text = "输入关键词后，可继续加载更早消息",
                        transformationSpec = transformationSpec,
                    )
                }
            }
            if (query.isNotBlank() && matches.isEmpty()) {
                item(key = "message-search-empty") {
                    ChatMessageSearchHint(
                        text = if (canLoadOlder) "当前已加载消息中没有匹配" else "没有匹配消息",
                        transformationSpec = transformationSpec,
                    )
                }
            }
            matches.forEachIndexed { index, message ->
                item(key = "message-search:$index:${message.stableKey}") {
                    Button(
                        onClick = { onSelect(message) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.surfaceContainerHigh,
                            contentColor = scheme.onSurface,
                            secondaryContentColor = scheme.onSurfaceVariant,
                        ),
                        contentPadding = ButtonDefaults.ContentPadding,
                        transformation = SurfaceTransformation(transformationSpec),
                        secondaryLabel = {
                            Text(
                                message.text.ifBlank { "[非文本消息]" },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    ) {
                        Text(
                            message.senderNick.ifBlank { if (message.isSelf) "我" else "消息" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (canLoadOlder) {
                item(key = "message-search-load-older") {
                    Button(
                        onClick = onLoadOlder,
                        enabled = !isLoadingOlder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        transformation = SurfaceTransformation(transformationSpec),
                    ) {
                        Text(if (isLoadingOlder) "正在加载…" else "加载更早消息")
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.ChatMessageSearchHint(
    text: String,
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.outline,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, transformationSpec)
            .graphicsLayer {
                with(SurfaceTransformation(transformationSpec)) {
                    applyContainerTransformation()
                    applyContentTransformation()
                }
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    )
}
