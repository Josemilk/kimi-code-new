package com.josemilk.kimicode.android.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class TerminalTool(private val root: File, private val permissions: PermissionManager) : AgentTool {
    override val name = "terminal"
    override val description = "Run an approved shell command in the Android workspace."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"}},\"required\":[\"command\"]}"

    override suspend fun execute(arguments: String): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            permissions.require(PermissionManager.Capability.TERMINAL)
            val command = JSONObject(arguments).getString("command")
            require(command.length <= 4000) { "Command too long" }
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(root)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText().take(100_000) }
            if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Command timed out") }
            ToolResult("", "exit=${process.exitValue()}\n$output", process.exitValue() != 0)
        }.getOrElse { ToolResult("", it.message ?: "terminal failed", true) }
    }
}

class GitTool(private val root: File, private val permissions: PermissionManager) : AgentTool {
    override val name = "git"
    override val description = "Run a Git operation inside the approved workspace."
    override val parametersSchema = "{\"type\":\"object\",\"properties\":{\"arguments\":{\"type\":\"string\"}},\"required\":[\"arguments\"]}"

    override suspend fun execute(arguments: String): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            permissions.require(PermissionManager.Capability.GIT)
            val args = JSONObject(arguments).getString("arguments")
            require(args.length <= 2000) { "Git arguments too long" }
            val command = "git $args"
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(root).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText().take(100_000) }
            if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Git timed out") }
            ToolResult("", "exit=${process.exitValue()}\n$output", process.exitValue() != 0)
        }.getOrElse { ToolResult("", it.message ?: "git failed", true) }
    }
}
