package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
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

    LaunchedEffect(Unit) { contactsVm.loadBuddies(runtime) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                modifier = Modifier.align(Alignment.CenterStart).clickable(onClick = onBack).padding(horizontal = 5.dp),
                color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Medium,
            )
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 29.dp),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.outline, fontSize = 9.sp,
            )
        }

        val allBuddies = categories.flatMap { it.buddies }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            items(allBuddies, key = { it.uid }) { buddy ->
                val name = buddy.remark.takeIf { it.isNotBlank() } ?: buddy.nick
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(buddy.uid, buddy.uin, name) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                            modifier = Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF3A3442)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = name.take(1),
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = name,
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
