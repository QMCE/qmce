package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.viewmodel.ChatDetailViewModel

@Composable
fun ChatMessageSearchScreen(
    query: String,
    matches: List<ChatDetailViewModel.UiMsg>,
    onQueryChange: (String) -> Unit,
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
            item(key = "message-search-header") {
                Text(
                    "搜索消息",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            item(key = "message-search-input") {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .background(scheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                "搜索当前已加载消息",
                                color = scheme.outline,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        inner()
                    },
                )
            }
            if (query.isNotBlank() && matches.isEmpty()) {
                item(key = "message-search-empty") {
                    ChatMessageSearchHint(
                        text = "没有匹配消息；可先向上滑动加载漫游消息",
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
