package com.josemilk.kimicode.android

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class KimiApi(private val context: Context) {
    private val prefs = context.getSharedPreferences("kimi", Context.MODE_PRIVATE)
    private val baseUrl = "https://api.kimi.com/coding/v1"

    fun setApiKey(key: String) { prefs.edit().putString("api_key", key.trim()).apply() }
    fun hasApiKey() = !prefs.getString("api_key", null).isNullOrBlank()

    fun chat(message: String, onText: (String) -> Unit, onDone: () -> Unit, onError: (String) -> Unit) {
        Thread {
            try {
                val key = prefs.getString("api_key", null).orEmpty()
                require(key.isNotBlank()) { "Kimi API key is not configured" }
                val conn = URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 120000
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val body = JSONObject().apply {
                    put("model", "k3")
                    put("stream", false)
                    put("messages", JSONArray().put(JSONObject().apply { put("role", "user"); put("content", message) }))
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                if (code !in 200..299) throw IllegalStateException("HTTP $code: $text")
                val root = JSONObject(text)
                val content = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
                android.os.Handler(context.mainLooper).post { onText(content); onDone() }
            } catch (t: Throwable) {
                android.os.Handler(context.mainLooper).post { onError(t.message ?: "Unknown error") }
            }
        }.start()
    }
}
