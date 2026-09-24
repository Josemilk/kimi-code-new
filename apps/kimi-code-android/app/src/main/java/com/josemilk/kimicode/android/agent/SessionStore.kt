package com.josemilk.kimicode.android.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Persistent local sessions. No conversation data is sent anywhere except when the agent calls Kimi. */
class SessionStore(context: Context) {
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }

    fun create(workspace: String): AgentSession {
        val session = AgentSession(UUID.randomUUID().toString(), workspace)
        save(session)
        return session
    }

    fun save(session: AgentSession) {
        val root = JSONObject().put("id", session.id).put("workspace", session.workspace)
        val messages = JSONArray()
        session.messages.forEach { messages.put(JSONObject().put("role", it.role).put("content", it.content)) }
        root.put("messages", messages)
        File(dir, "${session.id}.json").writeText(root.toString())
    }

    fun load(id: String): AgentSession? = runCatching {
        val root = File(dir, "$id.json")
        if (!root.exists()) return null
        val obj = JSONObject(root.readText())
        val messages = mutableListOf<AgentMessage>()
        val array = obj.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until array.length()) {
            val m = array.getJSONObject(i)
            messages += AgentMessage(m.optString("role"), m.optString("content"))
        }
        AgentSession(obj.getString("id"), obj.getString("workspace"), messages)
    }.getOrNull()

    fun list(): List<String> = dir.listFiles()?.filter { it.extension == "json" }?.map { it.nameWithoutExtension }?.sorted().orEmpty()
}
