package com.josemilk.kimicode.android.agent

data class AgentMessage(val role: String, val content: String)
data class AgentSession(val id: String, val workspace: String, val messages: MutableList<AgentMessage> = mutableListOf())
data class ToolRequest(val id: String, val name: String, val arguments: String)
data class ToolResult(val id: String, val output: String, val isError: Boolean = false)
sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ToolCall(val request: ToolRequest) : AgentEvent
    data class ApprovalRequired(val request: ToolRequest) : AgentEvent
    data class ToolFinished(val result: ToolResult) : AgentEvent
    data class Error(val message: String) : AgentEvent
    data object Completed : AgentEvent
}
