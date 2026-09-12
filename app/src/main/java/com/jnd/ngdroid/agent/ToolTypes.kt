package com.jnd.ngdroid.agent

interface AgentTool {
    val name: String
    val description: String
    val parametersJsonSchema: String
    suspend fun execute(argsJson: String): String
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
