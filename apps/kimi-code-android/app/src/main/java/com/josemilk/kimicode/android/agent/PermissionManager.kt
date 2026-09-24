package com.josemilk.kimicode.android.agent

import android.content.Context

/** Fine-grained runtime policy for agent capabilities. All dangerous capabilities default to denied. */
class PermissionManager(context: Context) {
    private val prefs = context.getSharedPreferences("agent_permissions", Context.MODE_PRIVATE)

    enum class Capability { FILE_READ, FILE_WRITE, TERMINAL, GIT, NETWORK, MCP, SUBAGENT }

    fun isAllowed(capability: Capability): Boolean = prefs.getBoolean(capability.name, capability == Capability.FILE_READ)

    fun setAllowed(capability: Capability, allowed: Boolean) {
        prefs.edit().putBoolean(capability.name, allowed).apply()
    }

    fun require(capability: Capability) {
        check(isAllowed(capability)) { "Permission denied: ${capability.name}" }
    }
}
