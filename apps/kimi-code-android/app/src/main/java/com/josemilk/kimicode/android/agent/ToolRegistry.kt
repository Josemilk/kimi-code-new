package com.josemilk.kimicode.android.agent

interface AgentTool {
    val name: String
    val description: String
    suspend fun execute(arguments: String): ToolResult
}

class ToolRegistry {
    private val tools = linkedMapOf<String, AgentTool>()
    fun register(tool: AgentTool) { tools[tool.name] = tool }
    fun get(name: String): AgentTool? = tools[name]
    fun all(): List<AgentTool> = tools.values.toList()
}
