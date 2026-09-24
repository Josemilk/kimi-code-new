package com.josemilk.kimicode.android.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ReadFileTool(private val root: File) : AgentTool {
    override val name = "read_file"
    override val description = "Read a UTF-8 text file inside the approved workspace."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}"
    override suspend fun execute(arguments: String): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val path = JSONObject(arguments).getString("path")
            val file = securePath(root, path)
            require(file.isFile) { "Not a file" }
            ToolResult("", file.readText())
        }.getOrElse { ToolResult("", it.message ?: "read_file failed", true) }
    }
}

class ListFilesTool(private val root: File) : AgentTool {
    override val name = "list_files"
    override val description = "List files and directories inside the approved workspace."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}"
    override suspend fun execute(arguments: String): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val dir = securePath(root, JSONObject(arguments).optString("path", "."))
            require(dir.isDirectory) { "Not a directory" }
            val output = dir.listFiles()?.sortedBy { it.name }?.joinToString("\n") { if (it.isDirectory) "${it.name}/" else it.name }.orEmpty()
            ToolResult("", output)
        }.getOrElse { ToolResult("", it.message ?: "list_files failed", true) }
    }
}

class WebFetchTool : AgentTool {
    override val name = "web_fetch"
    override val description = "Fetch an HTTP(S) page for agent research."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}"
    override suspend fun execute(arguments: String): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = JSONObject(arguments).getString("url")
            require(url.startsWith("https://") || url.startsWith("http://")) { "Only HTTP(S) URLs are supported" }
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000; c.requestMethod = "GET"
            val body = (if (c.responseCode in 200..399) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
            ToolResult("", body.take(500_000), c.responseCode !in 200..399)
        }.getOrElse { ToolResult("", it.message ?: "web_fetch failed", true) }
    }
}

private fun securePath(root: File, relative: String): File {
    val canonicalRoot = root.canonicalFile
    val target = File(canonicalRoot, relative).canonicalFile
    require(target.path == canonicalRoot.path || target.path.startsWith(canonicalRoot.path + File.separator)) { "Path escapes workspace" }
    require(target.path != File(canonicalRoot, ".git").canonicalPath && !target.path.startsWith(File(canonicalRoot, ".git").canonicalPath + File.separator)) { "Git metadata is not accessible" }
    return target
}
