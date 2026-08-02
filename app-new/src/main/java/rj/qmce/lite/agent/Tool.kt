package rj.qmce.lite.agent

/**
 * A kernel interface exposed to the Agent as a callable tool.
 *
 * Mirrors cocacode's `Tool` (tools/ReadTool.kt, tools/BashTool.kt): a name,
 * a human description, an OpenAI JSON-Schema input shape, and a suspend
 * [execute] body. The [requiresApproval] flag drives the approval policy:
 * write operations default to DENY->ASK, read-only operations run directly.
 */
abstract class Tool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>,
    val requiresApproval: Boolean,
    /** Event-monitor tools suspend until an event arrives instead of returning immediately. */
    val isEventMonitor: Boolean = false,
    /** Relative-time timer tools suspend for a duration then return. */
    val isTimer: Boolean = false,
) {
    abstract suspend fun execute(input: Map<String, Any>): ToolResult

    /** OpenAI function schema for the `tools` array in chat/completions. */
    fun toOpenAiFunction(): Map<String, Any> = mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to name,
            "description" to description,
            "parameters" to mapOf(
                "type" to "object",
                "properties" to inputSchema,
            ),
        ),
    )
}

data class ToolResult(
    val text: String,
    val isError: Boolean = false,
)

/** Read-only tool convenience base. */
abstract class ReadOnlyTool(
    name: String,
    description: String,
    inputSchema: Map<String, Any>,
) : Tool(name, description, inputSchema, requiresApproval = false)

/** Write tool convenience base (always requires user approval). */
abstract class WriteTool(
    name: String,
    description: String,
    inputSchema: Map<String, Any>,
) : Tool(name, description, inputSchema, requiresApproval = true)
