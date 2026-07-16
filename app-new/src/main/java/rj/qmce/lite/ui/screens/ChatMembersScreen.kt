package rj.qmce.lite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import coil3.compose.AsyncImage
import rj.qmce.lite.data.chat.GroupMemberRepository
import rj.qmce.lite.viewmodel.ChatDetailViewModel
import java.io.File
import java.util.Locale

@Composable
fun ChatMembersScreen(
    groupCode: Long,
    vm: ChatDetailViewModel,
    onBack: () -> Unit,
) {
    val members by vm.groupMembers.collectAsState()
    val loading by vm.groupMembersLoading.collectAsState()
    val error by vm.groupMembersError.collectAsState()
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val visibleMembers = remember(members, normalizedQuery) {
        if (normalizedQuery.isBlank()) members else members.filter { member ->
            listOf(member.displayName, member.nick, member.cardName, member.uid, member.uin.toString())
                .any { it.contains(normalizedQuery, ignoreCase = true) }
        }
    }

    LaunchedEffect(groupCode) {
        vm.loadGroupMembers(groupCode)
    }

    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "members-refresh") {
                androidx.wear.compose.material3.CompactButton(
                    onClick = { vm.loadGroupMembers(groupCode, forceRefresh = true) },
                    modifier = Modifier
                        .transformedHeight(this, transformationSpec)
                        .padding(vertical = 2.dp),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新群成员")
                }
            }
            item(key = "members-search") {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (query.isBlank()) Text("搜索昵称、群名片、QQ号或 UID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        inner()
                    },
                )
            }
            if (loading && members.isEmpty()) {
                item(key = "members-loading") {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .transformedHeight(this, transformationSpec),
                        strokeWidth = 2.dp,
                    )
                }
            } else if (error != null && members.isEmpty()) {
                item(key = "members-error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .graphicsLayer {
                                with(SurfaceTransformation(transformationSpec)) {
                                    applyContainerTransformation()
                                    applyContentTransformation()
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(error ?: "获取群成员失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { vm.loadGroupMembers(groupCode, forceRefresh = true) }) { Text("重试") }
                    }
                }
            } else if (visibleMembers.isEmpty()) {
                item(key = "members-empty") {
                    Text(
                        "没有匹配成员",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                    )
                }
            } else {
                items(visibleMembers, key = { memberKey(it) }) { member ->
                    GroupMemberRow(
                        member = member,
                        modifier = Modifier.transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
        }
    }
}

private fun memberKey(member: GroupMemberRepository.Member): String =
    member.uid.ifBlank { "uin:${member.uin}" }

@Composable
private fun GroupMemberRow(
    member: GroupMemberRepository.Member,
    modifier: Modifier,
    transformation: SurfaceTransformation,
) {
    val scheme = MaterialTheme.colorScheme
    val local = member.avatarPath.removePrefix("file://")
        .takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf(File::isFile)
    val model: Any? = local ?: member.uin.takeIf { it > 0L }?.let {
        "https://q1.qlogo.cn/g?b=qq&nk=$it&s=100"
    }
    Button(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        transformation = transformation,
        colors = ButtonDefaults.filledTonalButtonColors(),
        contentPadding = ButtonDefaults.ButtonWithLargeIconContentPadding,
        icon = {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(ButtonDefaults.LargeIconSize).clip(CircleShape).background(scheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(member.displayName.firstOrNull()?.toString() ?: "?", style = MaterialTheme.typography.bodyLarge, color = scheme.primary)
                if (model != null) {
                    AsyncImage(model, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                }
            }
        }, /* // don't need this
        secondaryLabel = {
            Text(member.uin.takeIf { it > 0L }?.toString() ?: member.uid, maxLines = 1)
        }, */
    ) { Text(member.displayName, maxLines = 1) }
}
