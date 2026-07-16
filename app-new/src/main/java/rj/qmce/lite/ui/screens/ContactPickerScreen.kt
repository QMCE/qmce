package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil3.compose.AsyncImage
import mqq.app.AppRuntime
import rj.qmce.lite.viewmodel.ContactsViewModel
import java.io.File

@Composable
fun ContactPickerScreen(
    title: String = "转发给",
    runtime: AppRuntime? = null,
    contactsVm: ContactsViewModel,
    onSelect: (uid: String, uin: Long, name: String) -> Unit,
    onBack: () -> Unit,
) {
    val categories by contactsVm.categories.collectAsState()
    val statusText by contactsVm.statusText.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    LaunchedEffect(Unit) { contactsVm.loadBuddies(runtime) }
    BackHandler(onBack = onBack)

    val allBuddies = categories.flatMap { it.buddies }
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (statusText.isNotEmpty()) {
                item(key = "contact-picker-status") {
                    Text(
                        text = statusText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(allBuddies, key = { "${it.categoryId}:${it.uid}" }) { buddy ->
                val name = buddy.remark.takeIf { it.isNotBlank() } ?: buddy.nick
                Button(
                    onClick = { onSelect(buddy.uid, buddy.uin, name) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .padding(vertical = 2.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
                    transformation = SurfaceTransformation(transformationSpec),
                    icon = {
                    val localAvatar = buddy.avatarPath
                        .takeIf { it.isNotBlank() }
                        ?.let { File(it) }
                        ?.takeIf(File::isFile)
                    if (localAvatar != null) {
                        AsyncImage(
                            model = localAvatar,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = name.take(1),
                                color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    },
                ) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
