package com.josemilk.kimicode.android.agent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Lightweight Android counterparts for the Agent Core v2 concepts used by Kimi Code. */

data class PlanStep(val id: String, val title: String, var status: String = "pending", val detail: String = "")

class PlanManager(private val root: File) {
    private val file = File(root, ".kimi/android-plan.json")
    private val lock = Mutex()
    private var steps = mutableListOf<PlanStep>()

    suspend fun replace(newSteps: List<PlanStep>) = lock.withLock {
        steps = newSteps.toMutableList(); persist()
    }
    suspend fun update(id: String, status: String) = lock.withLock {
        steps.find { it.id == id }?.status = status; persist()
    }
    suspend fun snapshot(): List<PlanStep> = lock.withLock { steps.toList() }
    private fun persist() {
        file.parentFile?.mkdirs()
        file.writeText(JSONArray().apply { steps.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("status", it.status).put("detail", it.detail)) } }.toString())
    }
    fun systemPrompt(): String = "Use a plan for multi-step work. Keep steps explicit and update their status as work progresses."
}

data class BackgroundTask(
    val id: String,
    val description: String,
    @Volatile var status: String = "running",
    @Volatile var output: String = "",
    @Volatile var error: String? = null,
    @Volatile var startedAt: Long = System.currentTimeMillis(),
    @Volatile var finishedAt: Long? = null
)

class BackgroundTaskManager(private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
    private val tasks = ConcurrentHashMap<String, BackgroundTask>()
    private val jobs = ConcurrentHashMap<String, Job>()

    fun launch(description: String, block: suspend () -> String): BackgroundTask {
        val task = BackgroundTask("task-${UUID.randomUUID()}", description)
        tasks[task.id] = task
        jobs[task.id] = scope.launch {
            try { task.output = block(); task.status = "completed" }
            catch (t: Throwable) { task.error = t.message ?: t.toString(); task.status = "failed" }
            finally { task.finishedAt = System.currentTimeMillis(); jobs.remove(task.id) }
        }
        return task
    }
    fun list(): List<BackgroundTask> = tasks.values.sortedByDescending { it.startedAt }
    fun get(id: String): BackgroundTask? = tasks[id]
    fun stop(id: String): Boolean { val job = jobs[id] ?: return false; job.cancel(); tasks[id]?.apply { status = "killed"; finishedAt = System.currentTimeMillis() }; return true }
}

class BackgroundTaskTool(private val manager: BackgroundTaskManager, private val runner: suspend (String) -> String) : AgentTool {
    override val name = "task_run_background"
    override val description = "Run an agent task in the background and return its task ID."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"prompt\":{\"type\":\"string\"}},\"required\":[\"prompt\"]}"
    override suspend fun execute(arguments: String): ToolResult { val prompt = JSONObject(arguments).getString("prompt"); val task = manager.launch(prompt) { runner(prompt) }; return ToolResult("", JSONObject().put("task_id", task.id).put("status", task.status).toString()) }
}

class TaskListTool(private val manager: BackgroundTaskManager) : AgentTool {
    override val name = "task_list"
    override val description = "List background tasks and their current status."
    override suspend fun execute(arguments: String): ToolResult = ToolResult("", JSONArray().apply { manager.list().forEach { put(JSONObject().put("task_id", it.id).put("description", it.description).put("status", it.status)) } }.toString())
}

class TaskOutputTool(private val manager: BackgroundTaskManager) : AgentTool {
    override val name = "task_output"
    override val description = "Retrieve a snapshot of a running or completed background task."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"task_id\":{\"type\":\"string\"}},\"required\":[\"task_id\"]}"
    override suspend fun execute(arguments: String): ToolResult { val t = manager.get(JSONObject(arguments).getString("task_id")) ?: return ToolResult("", "Task not found", true); return ToolResult("", JSONObject().put("task_id", t.id).put("status", t.status).put("output", t.output).put("error", t.error).toString()) }
}

class TaskStopTool(private val manager: BackgroundTaskManager) : AgentTool {
    override val name = "task_stop"
    override val description = "Stop a running background task."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"task_id\":{\"type\":\"string\"}},\"required\":[\"task_id\"]}"
    override suspend fun execute(arguments: String): ToolResult { val id = JSONObject(arguments).getString("task_id"); return ToolResult("", if (manager.stop(id)) "Task stopped" else "Task not running", !manager.list().any { it.id == id }) }
}

class PluginManager(private val root: File) {
    data class Plugin(val id: String, val name: String, val version: String, val entry: String, val enabled: Boolean)
    fun discover(): List<Plugin> {
        val dir = File(root, ".kimi/plugins")
        return dir.listFiles()?.filter { it.isDirectory }?.mapNotNull { d ->
            val manifest = File(d, "plugin.json")
            if (!manifest.isFile) null else runCatching { val j = JSONObject(manifest.readText()); Plugin(d.name, j.optString("name", d.name), j.optString("version", "0.0.0"), j.optString("entry", ""), j.optBoolean("enabled", true)) }.getOrNull()
        }.orEmpty()
    }
    fun systemPrompt(): String = discover().filter { it.enabled }.joinToString("\n") { "Plugin ${it.id} ${it.version}: ${it.name}; entry=${it.entry}" }
}

class HookManager(private val root: File) {
    private val dir = File(root, ".kimi/hooks")
    fun run(event: String, payload: String): String {
        val hook = File(dir, "$event.json")
        if (!hook.isFile) return payload
        return runCatching { JSONObject(hook.readText()).optString("transform", payload) }.getOrDefault(payload)
    }
    fun availableEvents(): List<String> = dir.listFiles()?.map { it.nameWithoutExtension }?.sorted().orEmpty()
}

class PlanTool(private val manager: PlanManager) : AgentTool {
    override val name = "plan_update"
    override val description = "Create or update the current execution plan."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"steps\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},\"title\":{\"type\":\"string\"},\"status\":{\"type\":\"string\"}}}}},\"required\":[\"steps\"]}"
    override suspend fun execute(arguments: String): ToolResult { val arr = JSONObject(arguments).getJSONArray("steps"); val steps = (0 until arr.length()).map { val j = arr.getJSONObject(it); PlanStep(j.optString("id", "step-${it + 1}"), j.getString("title"), j.optString("status", "pending")) }; manager.replace(steps); return ToolResult("", JSONArray(steps.map { JSONObject().put("id", it.id).put("title", it.title).put("status", it.status) }).toString()) }
}

class HookEventTool(private val hooks: HookManager) : AgentTool {
    override val name = "hook_event"
    override val description = "Run a local Kimi hook transformation for an agent lifecycle event."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"event\":{\"type\":\"string\"},\"payload\":{\"type\":\"string\"}},\"required\":[\"event\"]}"
    override suspend fun execute(arguments: String): ToolResult { val j = JSONObject(arguments); return ToolResult("", hooks.run(j.getString("event"), j.optString("payload", ""))) }
}
