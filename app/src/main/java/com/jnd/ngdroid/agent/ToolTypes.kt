package com.jnd.ngdroid.agent

/** Rich tool result: text observation plus optional vision images. */
data class ToolResult(
    val text: String,
    val images: List<LlmImage> = emptyList()
)

interface AgentTool {
    val name: String
    val description: String
    val parametersJsonSchema: String
    suspend fun execute(argsJson: String): String
    /** Rich result (text + optional vision images). Default wraps [execute]. */
    suspend fun executeEx(argsJson: String): ToolResult =
        ToolResult(execute(argsJson))
}

interface ToolRegistry {
    fun register(tool: AgentTool)
    fun list(): List<AgentTool>
    fun get(name: String): AgentTool?
}

class MapToolRegistry : ToolRegistry {
    private val tools = mutableMapOf<String, AgentTool>()

    override fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    override fun list(): List<AgentTool> = tools.values.toList()

    override fun get(name: String): AgentTool? = tools[name]
}
