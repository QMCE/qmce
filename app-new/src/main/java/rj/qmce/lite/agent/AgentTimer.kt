package rj.qmce.lite.agent

import android.os.SystemClock
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Relative-time timer tool. Parses a human duration string ("5m", "30s",
 * "2h"), waits for that wall-clock interval (calibrated with
 * [SystemClock.elapsedRealtime] so system time jumps don't skew it), then
 * returns so the engine can resume the conversation.
 *
 * Mirrors cocacode's ElapsedTime but adapted to a suspendable tool.
 */
object AgentTimer {

    private val timers = ConcurrentHashMap<String, TimerHandle>()
    private val seq = AtomicLong(0)

    private class TimerHandle(val durationMillis: Long, val label: String) {
        val startedAt: Long = SystemClock.elapsedRealtime()
    }

    /** Parse "5m" / "30s" / "2h" / "90" (seconds). */
    fun parseDuration(raw: String): Long? {
        val input = raw.trim().lowercase()
        if (input.isEmpty()) return null
        val match = Regex("^(\\d+)\\s*(ms|s|m|h)?$").find(input) ?: return null
        val value = match.groupValues[1].toLongOrNull() ?: return null
        return when (match.groupValues[2]) {
            "ms" -> value
            "", "s" -> value * 1000L
            "m" -> value * 60_000L
            "h" -> value * 3_600_000L
            else -> null
        }
    }

    fun formatDuration(millis: Long): String {
        val seconds = millis / 1000L
        return when {
            seconds >= 3600L -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            seconds >= 60L -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    /** Cancel a previously-started timer; returns true if found. */
    fun cancel(timerId: String): Boolean = timers.remove(timerId) != null

    fun activeTimers(): List<Pair<String, String>> =
        timers.entries.map { it.key to it.value.label }

    fun clearAll() = timers.clear()

    /**
     * Suspend for [durationMillis], then return a ToolResult the engine can
     * feed back. If cancelled (run aborted / logout), the timer is removed and
     * the suspension throws CancellationException, ending the run.
     */
    suspend fun wait(durationMillis: Long, label: String): ToolResult {
        val id = "timer-${seq.incrementAndGet()}"
        timers[id] = TimerHandle(durationMillis, label)
        try {
            delay(durationMillis)
            val waited = SystemClock.elapsedRealtime() - timers[id]!!.startedAt
            return ToolResult("计时到（$label，已等待 ${formatDuration(waited)}）")
        } finally {
            timers.remove(id)
        }
    }
}

/**
 * The `timer` tool exposed to the Agent.
 */
class TimerTool : Tool(
    name = "timer",
    description = "设置一个相对时间计时器，在指定时长后触发并返回。时长格式：如 \"30s\"、\"5m\"、\"2h\"。可用于提醒或定时任务。参数：duration（时长字符串）、label（提醒内容描述，可选）。该工具会挂起直到时间到或超时。",
    inputSchema = mapOf(
        "duration" to mapOf("type" to "string", "description" to "时长，如 30s / 5m / 2h"),
        "label" to mapOf("type" to "string", "description" to "提醒内容描述（可选）"),
    ),
    requiresApproval = true,
    isTimer = true,
    requiredParams = listOf("duration"),
) {
    override suspend fun execute(input: Map<String, Any>): ToolResult {
        val durationRaw = (input["duration"] as? String)?.takeIf { it.isNotBlank() }
            ?: return ToolResult("缺少 duration，格式如 30s / 5m / 2h", isError = true)
        val duration = AgentTimer.parseDuration(durationRaw)
            ?: return ToolResult("无法解析时长: $durationRaw", isError = true)
        if (duration > 6 * 3600_000L) {
            return ToolResult("计时器最长 6 小时", isError = true)
        }
        val label = (input["label"] as? String)?.takeIf { it.isNotBlank() }
            ?: durationRaw
        return AgentTimer.wait(duration, label)
    }
}
