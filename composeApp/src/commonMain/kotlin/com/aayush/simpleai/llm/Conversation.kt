package com.aayush.simpleai.llm

import com.aayush.simpleai.util.EnginePtr
import com.aayush.simpleai.util.NativeInputData
import com.aayush.simpleai.util.NativeStreamCallback
import com.aayush.simpleai.util.PlatformLock
import com.aayush.simpleai.util.SessionPtr
import com.aayush.simpleai.util.nativeEngineCreateSession
import com.aayush.simpleai.util.nativeSessionConfigCreate
import com.aayush.simpleai.util.nativeSessionConfigDelete
import com.aayush.simpleai.util.nativeSessionConfigSetMaxOutputTokens
import com.aayush.simpleai.util.nativeSessionDelete
import com.aayush.simpleai.util.nativeSessionGenerateContentStream
import com.aayush.simpleai.util.nativeSessionRunPrefill
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Role(val prefix: String) {
    SYSTEM(prefix = "System"),
    USER(prefix = "User"),
    ASSISTANT(prefix = "Assistant"),
    TOOL(prefix = "Tool"),
}

class Conversation internal constructor(
    private val engine: EnginePtr,
    private val config: ConversationConfig
) : AutoCloseable {
    private val lock = PlatformLock()
    private val toolRegistry = ToolRegistry(config.tools)
    private val history = mutableListOf<Message>()
    private var sessionPtr: SessionPtr? = null
    private var prefilled = false
    private var isClosed = false

    init {
        createSession()
        if (config.prefillMessages.isNotEmpty()) {
            prefillHistory(config.prefillMessages)
        }
    }

    fun getHistory(): List<Message> = history.toList()

    fun restoreHistory(messages: List<Message>) {
        lock.withLock {
            ensureAlive()
            resetSession()
            prefillHistory(messages)
        }
    }

    suspend fun sendMessageAsync(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: (String) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        try {
            val response = streamWithToolSupport(
                messageToPrefill = Message.user(prompt),
                onToken = onToken,
                toolCallCount = 0
            )
            onDone(response)
        } catch (e: Throwable) {
            onError(e)
        }
    }

    override fun close() {
        lock.withLock {
            if (isClosed) return
            sessionPtr?.let { nativeSessionDelete(it) }
            sessionPtr = null
            isClosed = true
        }
    }

    private fun createSession() {
        val sessionConfig = nativeSessionConfigCreate(
            samplerType = config.samplerConfig.type,
            topK = config.samplerConfig.topK,
            topP = config.samplerConfig.topP.toFloat(),
            temperature = config.samplerConfig.temperature.toFloat(),
            seed = config.samplerConfig.seed
        )
        try {
            config.maxOutputTokens?.let { maxOutputTokens ->
                nativeSessionConfigSetMaxOutputTokens(sessionConfig, maxOutputTokens)
            }
            sessionPtr = nativeEngineCreateSession(engine, sessionConfig)
        } finally {
            nativeSessionConfigDelete(sessionConfig)
        }
    }

    private fun resetSession() {
        sessionPtr?.let { nativeSessionDelete(it) }
        sessionPtr = null
        history.clear()
        prefilled = false
        createSession()
    }

    private fun prefillHistory(messages: List<Message>) {
        if (messages.isEmpty()) return
        val prompt = renderFullPrompt(
            systemPrompt = config.systemPrompt,
            toolsJson = toolRegistry.getOpenApiToolsJson(),
            messages = messages
        )
        if (prompt.isNotBlank()) {
            val session = sessionPtr ?: error("Session is not initialized.")
            nativeSessionRunPrefill(session, arrayOf(NativeInputData(text = prompt)))
            history.addAll(messages)
            prefilled = true
        }
    }

    private suspend fun streamWithToolSupport(
        messageToPrefill: Message,
        onToken: (String) -> Unit,
        toolCallCount: Int
    ): String {
        if (toolCallCount >= RECURRING_TOOL_CALL_LIMIT) {
            throw IllegalStateException(
                "Exceeded recurring tool call limit of $RECURRING_TOOL_CALL_LIMIT"
            )
        }

        val prompt = buildPromptForStream(messageToPrefill)
        history.add(messageToPrefill)
        val streamResult = streamDecode(prompt, onToken)
        val toolCalls = parseToolCalls(streamResult.fullText)

        val assistantMessage = Message.assistant(streamResult.fullText, toolCalls)
        history.add(assistantMessage)

        if (toolCalls.isEmpty()) {
            return streamResult.fullText
        }

        val responses = toolCalls.map { toolCall ->
            ToolResponse(
                name = toolCall.name,
                response = toolRegistry.execute(toolCall.name, toolCall.arguments)
            )
        }
        val toolMessage = Message.toolResponses(responses)
        return streamWithToolSupport(
            messageToPrefill = toolMessage,
            onToken = onToken,
            toolCallCount = toolCallCount + 1
        )
    }

    private fun buildPromptForStream(message: Message): String {
        val prompt = if (!prefilled) {
            val messages = history + message
            renderFullPrompt(
                systemPrompt = config.systemPrompt,
                toolsJson = toolRegistry.getOpenApiToolsJson(),
                messages = messages
            )
        } else {
            renderIncrementalPrompt(listOf(message))
        }
        prefilled = true
        return prompt
    }

    private suspend fun streamDecode(
        prompt: String,
        onToken: (String) -> Unit
    ): StreamResult {
        val session = sessionPtr ?: error("Session is not initialized.")
        return suspendCancellableCoroutine { continuation ->
            val fullBuffer = StringBuilder()
            val visibleBuffer = StringBuilder()
            val toolFenceFilter = ToolFenceFilter()
            val result = nativeSessionGenerateContentStream(
                session = session,
                inputs = arrayOf(NativeInputData(text = prompt)),
                callback = object : NativeStreamCallback {
                    override fun onChunk(chunk: String) {
                        fullBuffer.append(chunk)
                        val visibleDelta = toolFenceFilter.consume(chunk)
                        if (visibleDelta.isNotEmpty()) {
                            visibleBuffer.append(visibleDelta)
                            onToken(visibleBuffer.toString())
                        }
                    }

                    override fun onDone() {
                        continuation.resume(StreamResult(fullBuffer.toString()))
                    }

                    override fun onError(errorMessage: String) {
                        continuation.resumeWithException(
                            IllegalStateException(errorMessage)
                        )
                    }
                }
            )
            if (result != 0) {
                continuation.resumeWithException(
                    IllegalStateException("Failed to start stream (error code: $result)")
                )
            }
        }
    }

    private fun ensureAlive() {
        check(value = !isClosed) { "Conversation is closed." }
    }

    private data class StreamResult(val fullText: String)

    companion object {
        private const val RECURRING_TOOL_CALL_LIMIT = 25

        private fun parseToolCalls(text: String): List<ToolCall> {
            if (text.contains("\"tool_calls\"")) {
                val structuredCalls = parseStructuredToolCalls(text)
                if (structuredCalls.isNotEmpty()) {
                    return structuredCalls
                }
            }
            if (text.contains("```tool_code")) {
                return parseToolCodeBlocks(text)
            }
            return emptyList()
        }

        private fun parseStructuredToolCalls(text: String): List<ToolCall> {
            val toolCalls = mutableListOf<ToolCall>()
            val toolCallsPattern = """"tool_calls"\s*:\s*\[([^\]]*)\]""".toRegex()
            val toolCallsMatch = toolCallsPattern.find(text) ?: return emptyList()

            val toolCallsContent = toolCallsMatch.groupValues[1]
            val functionPattern =
                """"function"\s*:\s*\{[^}]*"name"\s*:\s*"([^"]+)"[^}]*\}""".toRegex()
            functionPattern.findAll(toolCallsContent).forEach { match ->
                val functionName = match.groupValues[1]
                val argsPattern = """"arguments"\s*:\s*\{([^}]*)\}""".toRegex()
                val argsMatch = argsPattern.find(match.value)
                val arguments = if (argsMatch != null) {
                    parseArgumentsFromJson(argsMatch.groupValues[1])
                } else {
                    emptyMap()
                }
                toolCalls.add(ToolCall(name = functionName, arguments = arguments))
            }
            return toolCalls
        }

        private fun parseToolCodeBlocks(text: String): List<ToolCall> {
            val toolCalls = mutableListOf<ToolCall>()
            val toolCodePattern = """```tool_code\s*\n([\s\S]*?)```""".toRegex()

            toolCodePattern.findAll(text).forEach { match ->
                val codeBlock = match.groupValues[1].trim()
                val functionPattern =
                    """(?:print\s*\(\s*)?(?:default_api\.)?(\w+)\s*\((.*?)\)\s*\)?""".toRegex()
                val funcMatch = functionPattern.find(codeBlock)
                if (funcMatch != null) {
                    val functionName = funcMatch.groupValues[1]
                    val argsString = funcMatch.groupValues[2]
                    val arguments = parsePythonStyleArguments(argsString)
                    toolCalls.add(ToolCall(name = functionName, arguments = arguments))
                }
            }
            return toolCalls
        }
    }
}