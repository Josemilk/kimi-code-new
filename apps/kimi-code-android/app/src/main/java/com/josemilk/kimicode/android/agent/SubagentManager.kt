package com.josemilk.kimicode.android.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Lightweight Android subagent orchestration. Each subagent receives an isolated role prompt and returns a result. */
class SubagentManager(private val coreFactory: () -> KimiAgentCore, private val permissions: PermissionManager) {
    data class Subagent(val id: String = UUID.randomUUID().toString(), val role: String)

    suspend fun run(role: String, task: String, emit: suspend (AgentEvent) -> Unit): String = withContext(Dispatchers.IO) {
        permissions.require(PermissionManager.Capability.SUBAGENT)
        val core = coreFactory()
        core.run("You are a Kimi Code subagent. Role: $role\nTask:\n$task", emit)
    }
}
