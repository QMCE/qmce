package rj.qmce.lite.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import rj.qmce.lite.agent.AgentSession
import rj.qmce.lite.agent.AgentSubsystem
import rj.qmce.lite.agent.ui.AgentChatScreen
import rj.qmce.lite.ui.screens.ChatInputScreen
import rj.qmce.lite.ui.screens.VoiceRecordScreen
import rj.qmce.lite.viewmodel.ChatDetailViewModel

@Composable
fun AgentChatRoute(
    onOpenInput: () -> Unit,
    onBack: () -> Unit,
) {
    AgentChatScreen(
        onOpenInput = onOpenInput,
        onBack = onBack,
    )
}

/**
 * Agent input route: reuses ChatInputScreen (shared UI + voice-text backfill),
 * but routes plain-text send to the Agent subsystem instead of a real chat.
 */
@Composable
fun AgentChatInputRoute(
    chatDetailVm: ChatDetailViewModel,
    navController: NavHostController,
) {
    ChatInputScreen(
        vm = chatDetailVm,
        peerUid = AgentSession.PEER_UID,
        peerUin = "0",
        chatType = AgentSession.CHAT_TYPE,
        editingText = "",
        replyTarget = null,
        openToolsOnLaunch = false,
        onConsumeReplyTarget = chatDetailVm::consumePendingReplyTarget,
        onSend = { text, _ -> AgentSubsystem.sendUserMessage(text) },
        onSendEdited = { text -> AgentSubsystem.sendUserMessage(text) },
        onSendMixed = { _, _, _, _, _ ->
            // Mixed content (images/faces/@) not supported in Agent text input.
        },
        onSendVideo = { _ ->
            // Video sending to the Agent pseudo contact is not supported.
        },
        onOpenVoiceRecorder = {
            navController.navigate("agentVoiceRecord") {
                launchSingleTop = true
            }
        },
        onReportingPageChanged = {},
        onBack = { navController.popBackStack() },
    )
}

/**
 * Agent voice-to-text route: reuses VoiceRecordScreen; the transcribed text is
 * routed to the Agent input box via the shared pendingVoiceText backfill.
 */
@Composable
fun AgentVoiceRecordRoute(
    chatDetailVm: ChatDetailViewModel,
    navController: NavHostController,
) {
    VoiceRecordScreen(
        onSendVoice = { _, _, _ -> },
        onTranscribedText = { text ->
            chatDetailVm.setPendingVoiceText(text)
            navController.popBackStack()
            navController.navigate("agentChatInput") {
                launchSingleTop = true
            }
        },
        onBack = { navController.popBackStack() },
    )
}
