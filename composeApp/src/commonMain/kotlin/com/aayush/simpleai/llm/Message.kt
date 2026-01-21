package com.aayush.simpleai.llm

data class Message(
    val role: Role,
    val text: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolResponses: List<ToolResponse> = emptyList()
) {
    companion object {
        fun system(text: String) = Message(role = Role.SYSTEM, text = text)
        fun user(text: String) = Message(role = Role.USER, text = text)
        fun assistant(text: String, toolCalls: List<ToolCall> = emptyList()) =
            Message(role = Role.ASSISTANT, text = text, toolCalls = toolCalls)
        fun toolResponses(responses: List<ToolResponse>) =
            Message(role = Role.TOOL, toolResponses = responses)
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
