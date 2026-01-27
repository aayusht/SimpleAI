package com.aayush.simpleai.llm

import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class Message(
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val role: Role,
    val fullText: String = "",
    val visibleText: String = "",
    val isLoading: Boolean = false,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResponses: List<ToolResponse> = emptyList()
) {
    companion object {
        fun system(text: String) = Message(role = Role.SYSTEM, fullText = text)
        fun user(text: String) = Message(role = Role.USER, fullText = text, visibleText = text)
        fun assistant(
            fullText: String = "",
            visibleText: String = "",
            toolCalls: List<ToolCall> = emptyList(),
            isLoading: Boolean = false,
        ) = Message(
            role = Role.ASSISTANT,
            fullText = fullText,
            visibleText = visibleText,
            toolCalls = toolCalls,
            isLoading = isLoading,
        )
        fun toolResponses(responses: List<ToolResponse>) = Message(
            role = Role.TOOL,
            toolResponses = responses,
        )
    }
}

data class ToolCall(
    val name: String,
    val arguments: Map<String, Any?>
)

data class ToolResponse(
    val name: String,
    val response: String
)
