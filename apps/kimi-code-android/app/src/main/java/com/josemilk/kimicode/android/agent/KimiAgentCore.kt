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

/** Android Agent Core mirroring Kimi Code v2 concepts while retaining Kimi as the official service. */
class KimiAgentCore(private val context: Context, workspace: File) {
    private val prefs = context.getSharedPreferences("kimi", Context.MODE_PRIVATE)
    private val tools = ToolRegistry()
    private val approvals = ApprovalManager()
    private val permissions = PermissionManager(context)
    private val skills = SkillManager(workspace)
    private val sessions = SessionStore(context)
    private val plan = PlanManager(workspace)
    private val background = BackgroundTaskManager()
    private val plugins = PluginManager(workspace)
    private val hooks = HookManager(workspace)
    private val workspaceRoot = workspace.canonicalFile
    private val baseUrl = "https://api.kimi.com/coding/v1/chat/completions"

    init {
        workspaceRoot.mkdirs()
        tools.register(ReadFileTool(workspaceRoot))
        tools.register(ListFilesTool(workspaceRoot))
        tools.register(WebFetchTool())
        tools.register(SearchFilesTool(workspaceRoot))
        tools.register(WorkspaceStatusTool(workspaceRoot))
        tools.register(TerminalTool(workspaceRoot, permissions))
        tools.register(GitTool(workspaceRoot, permissions))
        tools.register(PlanTool(plan))
        tools.register(PlanStatusTool(plan))
        tools.register(BackgroundTaskTool(background) { prompt ->
            KimiAgentCore(context, workspaceRoot).run(prompt) { }.let { it }
        })
        tools.register(TaskListTool(background))
        tools.register(TaskOutputTool(background))
        tools.register(TaskStopTool(background))
        tools.register(HookEventTool(hooks))
        tools.register(SubagentTool(SubagentManager({ KimiAgentCore(context, workspaceRoot) }, permissions)))
        prefs.getString("mcp_endpoint", null)?.takeIf { it.startsWith("https://") }?.let {
            tools.register(McpTool(McpClient(permissions), it))
        }
    }

    fun setApiKey(key: String) = prefs.edit().putString("api_key", key.trim()).apply()
    fun hasApiKey() = !prefs.getString("api_key", null).isNullOrBlank()
    fun setMcpEndpoint(endpoint: String?) { prefs.edit().putString("mcp_endpoint", endpoint?.trim()).apply() }
    fun pendingApprovalManager(): ApprovalManager = approvals
    fun permissionManager(): PermissionManager = permissions
    fun skillManager(): SkillManager = skills
    fun planManager(): PlanManager = plan
    fun backgroundTasks(): BackgroundTaskManager = background
    fun pluginManager(): PluginManager = plugins
    fun hookManager(): HookManager = hooks
    fun sessions(): SessionStore = sessions

    suspend fun run(prompt: String, emit: suspend (AgentEvent) -> Unit): String = withContext(Dispatchers.IO) {
        val key = prefs.getString("api_key", null).orEmpty()
        require(key.isNotBlank()) { "Kimi API key is not configured" }
        val sessionId = prefs.getString("current_session", null)
        val session = (sessionId?.let { sessions.load(it) } ?: sessions.create(workspaceRoot.path)).also {
            prefs.edit().putString("current_session", it.id).apply()
        }
        session.messages += AgentMessage("user", prompt)
        sessions.save(session)

        val hookedPrompt = hooks.run("before_turn", prompt)
        val system = buildString {
            append("You are Kimi Code on Android. Preserve the Kimi Code workflow: plan complex work, use tools, ask for approval before side effects, and report progress.\n")
            append(plan.systemPrompt()).append('\n')
            append(skills.systemPromptAddition()).append('\n')
            append(plugins.systemPrompt())
        }
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        session.messages.takeLast(40).forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        messages.put(JSONObject().put("role", "user").put("content", hookedPrompt))

        var finalText = ""
        repeat(16) {
            val response = request(key, messages)
            val message = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val content = message.optString("content")
            if (content.isNotBlank()) { finalText = content; emit(AgentEvent.TextDelta(content)) }
            val calls = message.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                val output = hooks.run("after_turn", finalText)
                session.messages += AgentMessage("assistant", output)
                sessions.save(session)
                emit(AgentEvent.Completed)
                return@withContext output
            }
            messages.put(message)
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i)
                val id = call.optString("id", UUID.randomUUID().toString())
                val fn = call.getJSONObject("function")
                val request = ToolRequest(id, fn.getString("name"), fn.optString("arguments", "{}"))
                emit(AgentEvent.ToolCall(request))
                val tool = tools.get(request.name)
                if (tool == null) {
                    val result = ToolResult(id, "Unknown tool: ${request.name}", true)
                    messages.put(toolMessage(result)); emit(AgentEvent.ToolFinished(result)); continue
                }
                emit(AgentEvent.ApprovalRequired(request))
                val result = if (approvals.request(request)) runCatching { tool.execute(request.arguments).copy(id = id) }.getOrElse { ToolResult(id, it.message ?: "Tool failed", true) }
                else ToolResult(id, "Tool execution denied by user", true)
                messages.put(toolMessage(result)); emit(AgentEvent.ToolFinished(result))
            }
        }
        emit(AgentEvent.Error("Agent loop reached the maximum number of tool rounds")); finalText
    }

    private fun request(key: String, messages: JSONArray): JSONObject {
        val connection = URL(baseUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"; connection.connectTimeout = 20_000; connection.readTimeout = 120_000
        connection.setRequestProperty("Authorization", "Bearer $key"); connection.setRequestProperty("Content-Type", "application/json"); connection.doOutput = true
        val body = JSONObject().put("model", "k3").put("stream", false).put("messages", messages).put("tools", toolDefinitions())
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = input.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("Kimi HTTP $code: $text")
        return JSONObject(text)
    }

    private fun toolDefinitions(): JSONArray = JSONArray().also { array ->
        tools.all().forEach { tool ->
            array.put(JSONObject().put("type", "function").put("function", JSONObject().put("name", tool.name).put("description", tool.description).put("parameters", JSONObject(tool.parametersSchema))))
        }
    }

    private fun toolMessage(result: ToolResult) = JSONObject().put("role", "tool").put("tool_call_id", result.id).put("content", result.output)
}

class PlanStatusTool(private val manager: PlanManager) : AgentTool {
    override val name = "plan_status"
    override val description = "Inspect the current execution plan and step statuses."
    override suspend fun execute(arguments: String): ToolResult = ToolResult("", JSONArray().apply { manager.snapshot().forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("status", it.status).put("detail", it.detail)) } }.toString())
}
