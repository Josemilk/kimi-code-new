package com.josemilk.kimicode.android.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SearchFilesTool(private val root: File) : AgentTool {
    override val name = "search_files"
    override val description = "Search text recursively inside the approved workspace."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"}},\"required\":[\"query\"]}"
    override suspend fun execute(arguments: String): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(arguments); val query = j.getString("query"); val start = File(root, j.optString("path", ".")).canonicalFile
            require(start.path == root.canonicalPath || start.path.startsWith(root.canonicalPath + File.separator)) { "Path escapes workspace" }
            val hits = JSONArray(); var count = 0
            start.walkTopDown().onEnter { !it.path.contains(File.separator + ".git") }.forEach { f ->
                if (count >= 200 || !f.isFile || f.length() > 2_000_000) return@forEach
                runCatching { f.readText(Charsets.UTF_8).lineSequence().forEachIndexed { n, line -> if (line.contains(query, true) && count < 200) { hits.put(JSONObject().put("file", f.relativeTo(root).path).put("line", n + 1).put("text", line.take(500))); count++ } } }
            }
            ToolResult("", hits.toString())
        }.getOrElse { ToolResult("", it.message ?: "search_files failed", true) }
    }
}

class WorkspaceStatusTool(private val root: File) : AgentTool {
    override val name = "workspace_status"
    override val description = "Return a compact status summary of the current workspace."
    override suspend fun execute(arguments: String): ToolResult = withContext(Dispatchers.IO) {
        val files = root.walkTopDown().onEnter { !it.path.contains(File.separator + ".git") }.count { it.isFile }
        ToolResult("", JSONObject().put("path", root.canonicalPath).put("file_count", files).put("has_git", File(root, ".git").exists()).toString())
    }
}
