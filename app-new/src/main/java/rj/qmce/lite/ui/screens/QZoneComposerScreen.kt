package rj.qmce.lite.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextField as MaterialTextField
import androidx.compose.material3.TextFieldDefaults as MaterialTextFieldDefaults
import rj.qmce.lite.data.reporting.OfficialReportBridge
import rj.qmce.lite.data.reporting.OfficialReportTargetBox

@Composable
fun QZoneComposerScreen(
    draft: String,
    selectedUris: List<Uri>,
    publishState: QZoneViewModel.PublishState,
    onDraftChange: (String) -> Unit,
    onPickMedia: () -> Unit,
    onPublish: () -> Unit,
    onPublishSucceeded: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    BackHandler(enabled = publishState is QZoneViewModel.PublishState.Publishing) {}
    LaunchedEffect(publishState) {
        if (publishState is QZoneViewModel.PublishState.Succeeded) onPublishSucceeded()
    }

    val scheme = MaterialTheme.colorScheme
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val canPublish = draft.isNotBlank() || selectedUris.isNotEmpty()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            val params = mapOf("have_picture" to if (selectedUris.isEmpty()) "0" else "1")
            OfficialReportTargetBox(
                key = "qzone:publish",
                elementId = OfficialReportBridge.ElementIds.PUBLISH,
                params = params,
            ) { reportTarget ->
                EdgeButton(
                    onClick = {
                        OfficialReportBridge.reportElementClick(
                            target = reportTarget,
                            elementId = OfficialReportBridge.ElementIds.PUBLISH,
                            params = params,
                        )
                        onPublish()
                    },
                    enabled = canPublish && publishState !is QZoneViewModel.PublishState.Publishing,
                    buttonSize = EdgeButtonSize.Small,
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发表动态") }
            }
        },
        edgeButtonSpacing = 2.5.dp,
    ) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (publishState is QZoneViewModel.PublishState.Failed) {
                item(key = "qzone-composer-error") {
                    Text("发表失败：${publishState.message}", modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
            item(key = "qzone-composer-input") {
                MaterialTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .graphicsLayer {
                            with(SurfaceTransformation(transformationSpec)) {
                                applyContainerTransformation()
                                applyContentTransformation()
                            }
                        }
                        .padding(horizontal = 10.dp)
                        .defaultMinSize(minHeight = 112.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    placeholder = { Text("写点什么…", style = MaterialTheme.typography.bodySmall) },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    colors = MaterialTextFieldDefaults.colors(
                        focusedContainerColor = scheme.surfaceContainerHigh,
                        unfocusedContainerColor = scheme.surfaceContainerHigh,
                        disabledContainerColor = scheme.surfaceContainerHigh,
                        focusedTextColor = scheme.onSurface,
                        unfocusedTextColor = scheme.onSurface,
                        cursorColor = scheme.primary,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedPlaceholderColor = scheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
                    ),
                )
            }
            item(key = "qzone-composer-media") {
                Button(
                    onClick = onPickMedia,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    transformation = SurfaceTransformation(transformationSpec),
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    icon = {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "选择图片",
                            modifier = Modifier.size(ButtonDefaults.LargeIconSize),
                        )
                    },
                ) {
                    Text(if (selectedUris.isEmpty()) "添加图片" else "已选 ${selectedUris.size} 张图片 · 继续添加")
                }
            }
        }
    }
}
