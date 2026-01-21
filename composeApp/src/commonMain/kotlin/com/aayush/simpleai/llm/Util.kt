package com.aayush.simpleai.llm


private const val TOOL_CODE_FENCE_START = "```tool_code"
private const val TOOL_CODE_FENCE_END = "```"

private fun suffixPrefixOverlap(a: String, b: String): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    val maxOverlap = minOf(a.length, b.length)
    for (len in maxOverlap downTo 1) {
        if (a.substring(a.length - len) == b.take(len)) {
            return len
        }
    }
    return 0
}

internal fun jsonEscape(value: String): String {
    val sb = StringBuilder(value.length)
    for (ch in value) {
        when (ch) {
            '\\' -> sb.append("\\\\")
            '\"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

internal fun parseArgumentsFromJson(jsonContent: String): Map<String, Any?> {
    val arguments = mutableMapOf<String, Any?>()
    val argPattern = """"(\w+)"\s*:\s*(?:"([^"]*)"|(\d+(?:\.\d+)?)|(\w+))""".toRegex()

    argPattern.findAll(jsonContent).forEach { match ->
        val name = match.groupValues[1]
        when {
            match.groupValues[2].isNotEmpty() -> arguments[name] = match.groupValues[2]
            match.groupValues[3].isNotEmpty() -> {
                val numStr = match.groupValues[3]
                arguments[name] = if (numStr.contains(".")) {
                    numStr.toDoubleOrNull() ?: numStr
                } else {
                    numStr.toIntOrNull() ?: numStr
                }
            }
            match.groupValues[4].isNotEmpty() -> {
                val value = match.groupValues[4]
                arguments[name] = when (value.lowercase()) {
                    "true" -> true
                    "false" -> false
                    "null" -> null
                    else -> value
                }
            }
        }
    }
    return arguments
}

internal fun parsePythonStyleArguments(argsString: String): Map<String, Any?> {
    val arguments = mutableMapOf<String, Any?>()
    val argPattern = """(\w+)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\d+(?:\.\d+)?)|(\w+))""".toRegex()

    argPattern.findAll(argsString).forEach { match ->
        val name = match.groupValues[1]
        when {
            match.groupValues[2].isNotEmpty() -> arguments[name] = match.groupValues[2]
            match.groupValues[3].isNotEmpty() -> arguments[name] = match.groupValues[3]
            match.groupValues[4].isNotEmpty() -> {
                val numStr = match.groupValues[4]
                arguments[name] =
                    if (numStr.contains(".")) numStr.toDoubleOrNull() ?: numStr
                    else numStr.toIntOrNull() ?: numStr
            }
            match.groupValues[5].isNotEmpty() -> {
                val value = match.groupValues[5]
                arguments[name] =
                    when (value.lowercase()) {
                        "true" -> true
                        "false" -> false
                        "none", "null" -> null
                        else -> value
                    }
            }
        }
    }
    return arguments
}

internal fun renderFullPrompt(
    systemPrompt: String?,
    toolsJson: String?,
    messages: List<Message>,
): String {
    val sb = StringBuilder()
    if (!systemPrompt.isNullOrBlank()) {
        sb.append("System: ${systemPrompt.trim()}\n\n")
    }
    if (!toolsJson.isNullOrBlank()) {
        sb.append("""
            To execute a search, output the following json, with included backticks:
            ```tool_code
            print(default_api.function_name(arg1="value1", arg2="value2"))
            ```
        """.trimIndent())
        sb.append("You will receive a response from the tool, and then can answer the " +
                "question accordingly after receiving context.\n")
        sb.append("The user is not the one executing the tool, so treat the tool response " +
                "conversationally as information you found, not information that was " +
                "provided to you.\n")
        sb.append("Tools: $toolsJson\n\n")
    }
    sb.append(renderMessages(messages))
    if (messages.isEmpty() || messages.last().role != Role.ASSISTANT) {
        sb.append("Assistant: ")
    }
    return sb.toString()
}

internal fun renderIncrementalPrompt(newMessages: List<Message>): String {
    val sb = StringBuilder()
    sb.append(renderMessages(newMessages))
    if (newMessages.isEmpty() || newMessages.last().role != Role.ASSISTANT) {
        sb.append("Assistant: ")
    }
    return sb.toString()
}

private  fun buildToolResponseJson(responses: List<ToolResponse>): String {
    if (responses.isEmpty()) return "[]"
    val items = responses.joinToString(separator = ",") { response ->
        val escaped = jsonEscape(value = response.response)
        val name = jsonEscape(value = response.name)
        "{\"type\":\"tool_response\",\"name\":\"$name\",\"response\":\"$escaped\"}"
    }
    return "[$items]"
}

private fun renderMessages(messages: List<Message>): String {
    val sb = StringBuilder()
    for (message in messages) {
        val prefix = message.role.prefix
        val content = when (message.role) {
            Role.TOOL -> buildToolResponseJson(message.toolResponses)
            else -> message.text
        }
        sb.append("$prefix: $content\n")
    }
    return sb.toString()
}

internal class ToolFenceFilter(
    private val codeFenceStart: String = TOOL_CODE_FENCE_START,
    private val codeFenceEnd: String = TOOL_CODE_FENCE_END
) {
    private val accumulated = StringBuilder()
    private var cursor = 0
    private var insideToolCall = false

    fun consume(chunk: String): String {
        if (chunk.isEmpty()) return ""
        accumulated.append(chunk)
        val output = StringBuilder()

        while (cursor < accumulated.length) {
            if (!insideToolCall) {
                val startPos = accumulated.indexOf(codeFenceStart, cursor)
                if (startPos >= 0) {
                    output.append(accumulated.substring(cursor, startPos))
                    cursor = startPos
                    insideToolCall = true
                } else {
                    val overlap = suffixPrefixOverlap(
                        a = accumulated.substring(cursor),
                        b = codeFenceStart
                    )
                    if (overlap > 0) {
                        val possibleStart = accumulated.length - overlap
                        output.append(accumulated.substring(cursor, possibleStart))
                        cursor = possibleStart
                        break
                    } else {
                        output.append(accumulated.substring(cursor))
                        cursor = accumulated.length
                    }
                }
            }

            if (insideToolCall) {
                val searchFrom = cursor + codeFenceStart.length
                val endPos = accumulated.indexOf(codeFenceEnd, searchFrom)
                if (endPos >= 0) {
                    cursor = endPos + codeFenceEnd.length
                    insideToolCall = false
                } else {
                    break
                }
            }
        }
        return output.toString()
    }
}
