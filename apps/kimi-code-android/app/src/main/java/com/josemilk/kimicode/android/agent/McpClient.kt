package com.josemilk.kimicode.android.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/** Minimal MCP JSON-RPC client for HTTP MCP endpoints. Servers are user-configured and gated by permission. */
class McpClient(private val permissions: PermissionManager) {
    private val ids = AtomicInteger(1)

    suspend fun initialize(endpoint: String): JSONObject = call(endpoint, "initialize", JSONObject().put("protocolVersion", "2025-06-18").put("capabilities", JSONObject()), null)

    suspend fun listTools(endpoint: String): JSONArray = call(endpoint, "tools/list", JSONObject(), null).optJSONArray("tools") ?: JSONArray()

    suspend fun callTool(endpoint: String, name: String, arguments: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        permissions.require(PermissionManager.Capability.MCP)
        call(endpoint, "tools/call", JSONObject().put("name", name).put("arguments", arguments), null)
    }

    private suspend fun call(endpoint: String, method: String, params: JSONObject, id: Int?): JSONObject = withContext(Dispatchers.IO) {
        require(endpoint.startsWith("https://")) { "MCP endpoints must use HTTPS" }
        val requestId = id ?: ids.getAndIncrement()
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"; connection.connectTimeout = 15000; connection.readTimeout = 60000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        connection.doOutput = true
        val body = JSONObject().put("jsonrpc", "2.0").put("id", requestId).put("method", method).put("params", params)
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) error("MCP HTTP $code: $text")
        parseJsonRpc(text)
    }

    private fun parseJsonRpc(text: String): JSONObject {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) return JSONObject(trimmed)
        val data = trimmed.lineSequence().firstOrNull { it.startsWith("data:") }?.removePrefix("data:")?.trim()
        return JSONObject(data ?: error("Unsupported MCP response"))
    }
}
