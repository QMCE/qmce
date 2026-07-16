package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.viewmodel.QZoneViewModel

@Composable
fun QZoneCommentScreen(
    feed: QZoneViewModel.FeedItem,
    draft: String,
    replyTarget: QZoneViewModel.CommentReplyTarget?,
    sendState: QZoneViewModel.CommentSendState,
    onDraftChange: (String) -> Unit,
    onReply: (QZoneViewModel.FeedComment) -> Unit,
    onCancelReply: () -> Unit,
    onSendSucceeded: () -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(sendState) {
        if (sendState is QZoneViewModel.CommentSendState.Succeeded) onSendSucceeded()
    }

    val scheme = MaterialTheme.colorScheme
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = onSend,
                enabled = draft.isNotBlank() && sendState !is QZoneViewModel.CommentSendState.Sending,
                buttonSize = EdgeButtonSize.Small,
            ) { Text("发送") }
        },
        edgeButtonSpacing = 2.5.dp,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            replyTarget?.let { target ->
                item(key = "qzone-comment-reply-target:${target.commentId}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("回复 ${target.targetName}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "取消",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable(onClick = onCancelReply),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (sendState is QZoneViewModel.CommentSendState.Failed) {
                item(key = "qzone-comment-send-error") {
                    QZoneCommentHint("发送失败：${sendState.message}", transformationSpec)
                }
            }
            if (feed.comments.isEmpty()) {
                item(key = "qzone-comment-none") {
                    QZoneCommentHint("暂无评论", transformationSpec)
                }
            } else {
                feed.comments.takeLast(12).forEachIndexed { index, comment ->
                    item(key = "qzone-comment:$index:${comment.id}") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .graphicsLayer {
                                    with(SurfaceTransformation(transformationSpec)) {
                                        applyContainerTransformation()
                                        applyContentTransformation()
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                .background(scheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                                .clickable { onReply(comment) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(
                                comment.author,
                                color = scheme.primary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (comment.text.isNotBlank()) {
                                Text(
                                    comment.text,
                                    color = scheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            comment.replies.takeLast(4).forEach { reply ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = "回复",
                                        tint = scheme.onSurfaceVariant,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    androidx.compose.foundation.layout.Spacer(Modifier.width(3.dp))
                                    Text(
                                        "${reply.author}: ${reply.text}",
                                        color = scheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "qzone-comment-input") {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
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
                        .background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { innerTextField ->
                        if (draft.isBlank()) {
                            Text(
                                "写下评论…",
                                color = scheme.outline,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        innerTextField()
                    },
                )
            }
        }
    }
}

@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.QZoneCommentHint(
    text: String,
    transformationSpec: androidx.wear.compose.material3.lazy.TransformationSpec,
) {
    Text(
        text,
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
            .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}
