package rj.qmce.lite.agent

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

/**
 * Registers all kernel tools into [KernelToolRegistry].
 * Called once from AgentSubsystem.ensure.
 */
object AgentToolRegistrar {

    @Volatile
    private var registered = false

    fun ensure() {
        if (registered) return
        synchronized(this) {
            if (registered) return
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
            KernelToolRegistry.register(SendPacketTool())
            KernelToolRegistry.register(EventMonitorTool())
            KernelToolRegistry.register(TimerTool())
            registered = true
        }
    }
}
