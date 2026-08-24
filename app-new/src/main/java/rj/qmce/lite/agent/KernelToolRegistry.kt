package rj.qmce.lite.agent

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of Agent tools. Mirrors cocacode's `ToolRegistry` (tools/ToolRegistry.kt).
 */
object KernelToolRegistry {

    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun get(name: String): Tool? = tools[name]

    fun all(): List<Tool> = tools.values.toList()

    fun clear() = tools.clear()
}
