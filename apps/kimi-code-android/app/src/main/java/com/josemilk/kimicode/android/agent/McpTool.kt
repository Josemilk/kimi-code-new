package com.josemilk.kimicode.android.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Exposes a configured MCP server as an agent tool. Endpoint is explicitly configured by the user. */
class McpTool(private val client: McpClient, private val endpoint: String) : AgentTool {
    override val name = "mcp_call"
    override val description = "Call a tool exposed by the configured Model Context Protocol server."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"arguments\":{\"type\":\"object\"}},\"required\":[\"name\"]}"

    override suspend fun execute(arguments: String): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val obj = JSONObject(arguments)
            val name = obj.getString("name")
            val args = obj.optJSONObject("arguments") ?: JSONObject()
            val result = client.callTool(endpoint, name, args)
            ToolResult("", result.toString(), result.has("error"))
        }.getOrElse { ToolResult("", it.message ?: "mcp_call failed", true) }
    }
}
