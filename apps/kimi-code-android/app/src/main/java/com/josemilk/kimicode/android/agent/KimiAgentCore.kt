package com.josemilk.kimicode.android.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Android implementation of the Kimi Code agent loop.
 * It keeps Kimi as the model/service while adapting tools, permissions, skills,
 * sessions and workspace operations to Android's sandbox.
 */
class KimiAgentCore(private val context: Context, workspace: File) {
    private val prefs = context.getSharedPreferences("kimi", Context.MODE_PRIVATE)
    private val tools = ToolRegistry()
    private val approvals = ApprovalManager()
    private val permissions = PermissionManager(context)
    private val skills = SkillManager(workspace)
    private val workspaceRoot = workspace.canonicalFile
    private val baseUrl = "https://api.kimi.com/coding/v1/chat/completions"

    init {
        workspaceRoot.mkdirs()
        tools.register(ReadFileTool(workspaceRoot))
        tools.register(ListFilesTool(workspaceRoot))
        tools.register(WebFetchTool())
        tools.register(TerminalTool(workspaceRoot, permissions))
        tools.register(GitTool(workspaceRoot, permissions))
    }

    fun setApiKey(key: String) = prefs.edit().putString("api_key", key.trim()).apply()
    fun hasApiKey() = !prefs.getString("api_key", null).isNullOrBlank()
    fun pendingApprovalManager(): ApprovalManager = approvals
    fun permissionManager(): PermissionManager = permissions
    fun skillManager(): SkillManager = skills

    suspend fun run(prompt: String, emit: suspend (AgentEvent) -> Unit): String = withContext(Dispatchers.IO) {
        val key = prefs.getString("api_key", null).orEmpty()
        require(key.isNotBlank()) { "Kimi API key is not configured" }
        val messages = JSONArray()
        val skillPrompt = skills.systemPromptAddition()
        val system = "You are Kimi Code running in the Android workspace. Respect tool permissions and user approvals.\n" + skillPrompt
        messages.put(JSONObject().put("role", "system").put("content", system))
        messages.put(JSONObject().put("role", "user").put("content", prompt))
        var finalText = ""
        repeat(16) {
            val response = request(key, messages)
            val message = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val content = message.optString("content")
            if (content.isNotBlank()) { finalText = content; emit(AgentEvent.TextDelta(content)) }
            val calls = message.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                emit(AgentEvent.Completed)
                return@withContext finalText
            }
            messages.put(message)
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i)
                val id = call.optString("id", UUID.randomUUID().toString())
                val fn = call.getJSONObject("function")
                val name = fn.getString("name")
                val args = fn.optString("arguments", "{}")
                val request = ToolRequest(id, name, args)
                emit(AgentEvent.ToolCall(request))
                val tool = tools.get(name)
                if (tool == null) {
                    val result = ToolResult(id, "Unknown tool: $name", true)
                    messages.put(toolMessage(result)); emit(AgentEvent.ToolFinished(result)); continue
                }
                emit(AgentEvent.ApprovalRequired(request))
                val approved = approvals.request(request)
                if (!approved) {
                    val result = ToolResult(id, "Tool execution denied by user", true)
                    messages.put(toolMessage(result)); emit(AgentEvent.ToolFinished(result)); continue
                }
                val result = tool.execute(args).copy(id = id)
                messages.put(toolMessage(result))
                emit(AgentEvent.ToolFinished(result))
            }
        }
        emit(AgentEvent.Error("Agent loop reached the maximum number of tool rounds"))
        finalText
    }

    private fun request(key: String, messages: JSONArray): JSONObject {
        val connection = URL(baseUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("Authorization", "Bearer $key")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        val body = JSONObject().apply {
            put("model", "k3")
            put("stream", false)
            put("messages", messages)
            put("tools", toolDefinitions())
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = input.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("Kimi HTTP $code: $text")
        return JSONObject(text)
    }

    private fun toolDefinitions(): JSONArray = JSONArray().also { array ->
        tools.all().forEach { tool ->
            array.put(JSONObject().put("type", "function").put("function", JSONObject()
                .put("name", tool.name)
                .put("description", tool.description)
                .put("parameters", JSONObject(tool.parametersSchema))))
        }
    }

    private fun toolMessage(result: ToolResult) = JSONObject()
        .put("role", "tool")
        .put("tool_call_id", result.id)
        .put("content", result.output)
}
