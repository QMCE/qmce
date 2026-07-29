package rj.qmce.lite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import rj.qmce.lite.ui.theme.LocalQmceAdaptive
import rj.qmce.lite.ui.wear.QmceEmptyOrErrorState

private enum class NotificationSubPage { None, FriendRequests, GroupNotices }

@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
) {
    var subPage by remember { mutableStateOf(NotificationSubPage.None) }

    BackHandler(enabled = subPage != NotificationSubPage.None) {
        subPage = NotificationSubPage.None
    }

    when (subPage) {
        NotificationSubPage.FriendRequests -> NotificationSubPageContent(
            title = "新朋友",
            onBack = { subPage = NotificationSubPage.None },
        )
        NotificationSubPage.GroupNotices -> NotificationSubPageContent(
            title = "群通知",
            onBack = { subPage = NotificationSubPage.None },
        )
        NotificationSubPage.None -> NotificationCenterMain(
            onOpenFriendRequests = { subPage = NotificationSubPage.FriendRequests },
            onOpenGroupNotices = { subPage = NotificationSubPage.GroupNotices },
            onBack = onBack,
        )
    }
}

@Composable
private fun NotificationCenterMain(
    onOpenFriendRequests: () -> Unit,
    onOpenGroupNotices: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val adaptive = LocalQmceAdaptive.current

    ScreenScaffold(
        scrollState = listState,
        edgeButtonSpacing = adaptive.edgeButtonSpacing,
        edgeButton = {
            EdgeButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                buttonSize = EdgeButtonSize.Small,
            ) {
                Text("返回")
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "title") {
                Text(
                    text = "通知中心",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "friend-requests") {
                Button(
                    onClick = onOpenFriendRequests,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    icon = {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                    },
                ) {
                    Text("新朋友")
                }
            }
            item(key = "group-notices") {
                Button(
                    onClick = onOpenGroupNotices,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    icon = {
                        Icon(Icons.Outlined.Group, contentDescription = null)
                    },
                ) {
                    Text("群通知")
                }
            }
        }
    }
}

@Composable
private fun NotificationSubPageContent(
    title: String,
    onBack: () -> Unit,
) {
    QmceEmptyOrErrorState(
        message = "暂无$title",
        actionLabel = "返回",
        onAction = onBack,
    )
}
