package rj.qmce.lite.agent

import android.content.Context
import rj.qmce.lite.agent.kernel.ApproveFriendTool
import rj.qmce.lite.agent.kernel.ApproveGroupNoticeTool
import rj.qmce.lite.agent.kernel.GetGroupInfoTool
import rj.qmce.lite.agent.kernel.KickGroupMemberTool
import rj.qmce.lite.agent.kernel.ListGroupsTool
import rj.qmce.lite.agent.kernel.ListSessionsTool
import rj.qmce.lite.agent.kernel.MarkReadTool
import rj.qmce.lite.agent.kernel.PublishGroupBulletinTool
import rj.qmce.lite.agent.kernel.ReadMessagesTool
import rj.qmce.lite.agent.kernel.RecallMessageTool
import rj.qmce.lite.agent.kernel.SendMessageTool
import rj.qmce.lite.agent.kernel.SendPacketTool
import rj.qmce.lite.agent.kernel.SetChatMutedTool
import rj.qmce.lite.agent.kernel.SetChatTopTool
import rj.qmce.lite.agent.kernel.SetGroupAllMutedTool
import rj.qmce.lite.viewmodel.SettingsViewModel

/**
 * Registers all kernel tools into [KernelToolRegistry].
 * Called from AgentSubsystem.ensure / setEnabled; rebuilds when send_packet toggles.
 */
object AgentToolRegistrar {

    @Volatile
    private var lastSendPacketEnabled: Boolean? = null

    fun ensure(context: Context? = null) {
        val sendPacket = context?.let { isSendPacketEnabled(it) } ?: false
        synchronized(this) {
            if (lastSendPacketEnabled == sendPacket && KernelToolRegistry.all().isNotEmpty()) return
            KernelToolRegistry.clear()
            KernelToolRegistry.register(ListSessionsTool())
            KernelToolRegistry.register(ReadMessagesTool())
            KernelToolRegistry.register(SendMessageTool())
            KernelToolRegistry.register(RecallMessageTool())
            KernelToolRegistry.register(MarkReadTool())
            KernelToolRegistry.register(ListGroupsTool())
            KernelToolRegistry.register(GetGroupInfoTool())
            KernelToolRegistry.register(SetGroupAllMutedTool())
            KernelToolRegistry.register(KickGroupMemberTool())
            KernelToolRegistry.register(PublishGroupBulletinTool())
            KernelToolRegistry.register(ApproveFriendTool())
            KernelToolRegistry.register(ApproveGroupNoticeTool())
            KernelToolRegistry.register(SetChatTopTool())
            KernelToolRegistry.register(SetChatMutedTool())
            if (sendPacket) {
                KernelToolRegistry.register(SendPacketTool())
            }
            KernelToolRegistry.register(EventMonitorTool())
            KernelToolRegistry.register(TimerTool())
            lastSendPacketEnabled = sendPacket
        }
    }

    private fun isSendPacketEnabled(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(SettingsViewModel.PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(SettingsViewModel.KEY_AGENT_SEND_PACKET, false)
    }
}
