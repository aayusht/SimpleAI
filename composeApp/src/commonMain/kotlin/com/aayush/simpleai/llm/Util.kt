package com.aayush.simpleai.llm


private const val TOOL_CODE_FENCE_START = "```tool_code"
private const val TOOL_CODE_FENCE_END = "```"
private const val GEMMA3_START_OF_TURN = "<start_of_turn>"
private const val GEMMA3_END_OF_TURN = "<end_of_turn>"

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
    toolRegistry: ToolRegistry,
    messages: List<Message>,
): String {
    val toolsJson = toolRegistry.getOpenApiToolsJson()
    return renderGemma3Turns(
        systemPrompt = systemPrompt,
        toolRegistry = toolRegistry,
        toolsJson = toolsJson,
        messages = messages,
        includeInstructionBlock = true,
    )
}

internal fun renderIncrementalPrompt(newMessages: List<Message>): String {
    // Keep incremental prompts minimal, but still in Gemma3 turn format.
    // (No system/instruction splice here; the session is already prefixed.)
    return renderGemma3Turns(
        systemPrompt = null,
        toolRegistry = ToolRegistry(emptyList()),
        toolsJson = "[]",
        messages = newMessages,
        includeInstructionBlock = false,
    )
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

private fun renderGemma3Turns(
    systemPrompt: String?,
    toolRegistry: ToolRegistry,
    toolsJson: String,
    messages: List<Message>,
    includeInstructionBlock: Boolean,
): String {
    val sb = StringBuilder()

    // Gemma3-style system splicing:
    // - If the first message is a system message, splice it into the first user turn.
    // - Also splice `systemPrompt` (ConversationConfig.systemPrompt) into the first user turn.
    var loopMessages = messages
    val prefixParts = mutableListOf<String>()

    if (loopMessages.isNotEmpty() && loopMessages.first().role == Role.SYSTEM) {
        val sys = loopMessages.first().text.trim()
        if (sys.isNotEmpty()) prefixParts.add("System: $sys")
        loopMessages = loopMessages.drop(1)
    }

    if (!systemPrompt.isNullOrBlank()) {
        prefixParts.add("System: ${systemPrompt.trim()}")
    }

    if (includeInstructionBlock && toolsJson != "[]") {
        val instructions = buildString {
            append("========= START SYSTEM INSTRUCTIONS =========\n\n")
            append(
                """
                If and only if you need more info to answer the user, to execute a tool, output the following json, with included backticks and no preceding text:
                ```tool_code
                print(default_api.web_search(query="example query"))
                ```
                """.trimIndent()
            )
            append("\n")
            append(
                "You will receive a response from the tool, and then can answer the " +
                    "question accordingly after receiving context.\n"
            )
            append(
                "Don't make tool calls if information isn't required! eg if they're just saying hi or thanking you or asking about very simple facts\n"
            )
            append(
                "I repeat, only use tool calls when the immediately prior user message needs factual context. It's likely that if you've already finished replying to a message, a tool call is not likely.\n"
            )
            append("Tools spec: $toolsJson\n\n")
            append("Example invocations: \n\n")
            for (tool in toolRegistry.toolList) {
                append("default_api.${tool.name}(${tool.pyArgsString})\n\n")
            }
            append("========= END SYSTEM INSTRUCTIONS =========")
        }
        prefixParts.add(instructions)
    }

    val firstUserPrefix = prefixParts.joinToString(separator = "\n\n").trim()

    fun appendTurn(role: String, content: String) {
        sb.append(GEMMA3_START_OF_TURN).append(role).append("\n")
        if (content.isNotEmpty()) sb.append(content)
        sb.append(GEMMA3_END_OF_TURN).append("\n")
    }

    for ((idx, message) in loopMessages.withIndex()) {
        val isFirst = idx == 0
        when (message.role) {
            Role.USER -> {
                val base = "User: ${message.text}"
                val content =
                    if (isFirst && firstUserPrefix.isNotEmpty()) {
                        firstUserPrefix + "\n\n" + base
                    } else {
                        base
                    }
                appendTurn(role = "user", content = content.trimEnd())
            }

            Role.ASSISTANT -> {
                appendTurn(role = "model", content = ("Assistant: ${message.text}").trimEnd())
            }

            Role.TOOL -> {
                val toolJson = buildToolResponseJson(message.toolResponses)
                appendTurn(role = "user", content = ("Tool: $toolJson").trimEnd())
            }

            Role.SYSTEM -> {
                // Non-leading system messages are uncommon in this app.
                // Preserve the prior "System: ..." transcript style but wrap in a user turn.
                appendTurn(role = "user", content = ("System: ${message.text}").trimEnd())
            }
        }
    }

    // Generation prompt. Keep your previous "Assistant: " prefix since your tool-calling
    // instructions and parsing assume this transcript style.
    if (loopMessages.isEmpty() || loopMessages.last().role != Role.ASSISTANT) {
        sb.append(GEMMA3_START_OF_TURN).append("model\n")
        sb.append("Assistant: ")
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
