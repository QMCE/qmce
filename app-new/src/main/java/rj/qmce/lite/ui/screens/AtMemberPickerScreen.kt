package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import rj.qmce.lite.data.chat.GroupMemberRepository

@Composable
fun AtMemberPickerScreen(
    query: String,
    members: List<GroupMemberRepository.Member>,
    errorMessage: String?,
    onQueryChange: (String) -> Unit,
    onSelect: (GroupMemberRepository.Member) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val scheme = MaterialTheme.colorScheme
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val normalizedQuery = query.trim()
    val visibleMembers = remember(members, normalizedQuery) {
        members.filter { member ->
            normalizedQuery.isBlank() || listOf(
                member.displayName,
                member.nick,
                member.cardName,
                member.uid,
                member.uin.toString(),
            ).any { it.contains(normalizedQuery, ignoreCase = true) }
        }.take(40)
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "at-member-header") {
                Text(
                    "@成员",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            item(key = "at-member-search") {
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
                        .background(scheme.surfaceContainerHigh, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (query.isBlank()) {
                            Text(
                                "搜索昵称、群名片、QQ号或 UID",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.outline,
                            )
                        }
                        inner()
                    },
                )
            }
            when {
                members.isEmpty() -> item(key = "at-member-unavailable") {
                    AtMemberPickerHint(
                        text = errorMessage ?: "暂无可用群成员",
                        transformationSpec = transformationSpec,
                    )
                }

                visibleMembers.isEmpty() -> item(key = "at-member-empty") {
                    AtMemberPickerHint(
                        text = "没有匹配成员",
                        transformationSpec = transformationSpec,
                    )
                }

                else -> visibleMembers.forEachIndexed { index, member ->
                    item(key = "at-member:$index:${member.uid}:${member.uin}") {
                        Button(
                            onClick = { onSelect(member) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurface,
                                secondaryContentColor = scheme.onSurfaceVariant,
                            ),
                            transformation = SurfaceTransformation(transformationSpec),
                            secondaryLabel = {
                                Text(
                                    member.uin.takeIf { it > 0L }?.toString() ?: member.uid,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        ) {
                            Text(member.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope.AtMemberPickerHint(
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
