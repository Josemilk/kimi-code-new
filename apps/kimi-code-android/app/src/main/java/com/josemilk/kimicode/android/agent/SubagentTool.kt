package com.josemilk.kimicode.android.agent

import org.json.JSONObject

class SubagentTool(private val manager: SubagentManager) : AgentTool {
    override val name = "subagent"
    override val description = "Delegate a focused task to a permission-gated Kimi Code subagent."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"role\":{\"type\":\"string\"},\"task\":{\"type\":\"string\"}},\"required\":[\"role\",\"task\"]}"

    override suspend fun execute(arguments: String): ToolResult = runCatching {
        val obj = JSONObject(arguments)
        val result = manager.run(obj.getString("role"), obj.getString("task")) { }
        ToolResult("", result)
    }.getOrElse { ToolResult("", it.message ?: "subagent failed", true) }
}
