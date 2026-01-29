package com.aayush.simpleai.llm

import com.aayush.simpleai.llm.Message.Content
import com.aayush.simpleai.util.EnginePtr
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.collections.plus
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Role(val prefix: String, val isUserVisible: Boolean) {
    SYSTEM(prefix = "System", isUserVisible = false),
    USER(prefix = "User", isUserVisible = true),
    ASSISTANT(prefix = "Assistant", isUserVisible = true),
    TOOL(prefix = "Tool", isUserVisible = false),
}

class Conversation internal constructor(
    private val engine: EnginePtr,
    private val config: ConversationConfig
) : AutoCloseable {
    private val lock = PlatformLock()
    private val toolRegistry = ToolRegistry(config.tools)
    private val _history = MutableStateFlow<List<Message>>(listOf())

    private val _generatingMessage = MutableStateFlow<Message?>(null)

    private val _isConversationReady = MutableStateFlow(false)

    private var sessionPtr: SessionPtr? = null
    private var prefilled = false
    private var isClosed = false

    val completedMessagesHistory: StateFlow<List<Message>>
        get() = _history.asStateFlow()

    val history: Flow<List<Message>>
        get() = combine(_history, _generatingMessage) { history, generatingMessage ->
            if (generatingMessage != null) {
                history + generatingMessage
            } else {
                history
            }
        }

    val isConversationReady: Flow<Boolean>
        get() = _isConversationReady.asStateFlow()

    init {
        createSession()
        if (config.prefillMessages.isNotEmpty()) {
            prefillHistory(config.prefillMessages)
        } else {
            _isConversationReady.value = true
        }
    }

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
            commitGeneratingMessage(fullText = response, toolCalls = emptyList())
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
        _history.value = emptyList()
        prefilled = false
        _isConversationReady.value = false
        createSession()
    }

    private fun prefillHistory(messages: List<Message>) {
        if (messages.isEmpty()) {
            _isConversationReady.value = true
            return
        }
        val prompt = renderFullPrompt(
            systemPrompt = config.systemPrompt,
            toolRegistry = toolRegistry,
            messages = messages
        )
        if (prompt.text.isNotBlank()) {
            val session = sessionPtr ?: error("Session is not initialized.")
            nativeSessionRunPrefill(session, prompt.nativeInputs)
            _history.update { it + messages }
            prefilled = true
        }
        _isConversationReady.value = true
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
        _history.update { it + messageToPrefill }
        updateGeneratingMessage(fullText = "", visibleText = "")

        val streamResult = streamDecode(prompt, onToken)
        val toolCalls = parseToolCalls(streamResult.fullText)

        commitGeneratingMessage(fullText = streamResult.fullText, toolCalls = toolCalls)

        if (toolCalls.isEmpty()) {
            return streamResult.fullText
        }

        val responses = toolCalls.map { toolCall ->
            ToolResponse(
                name = toolCall.name,
                response = toolRegistry.execute(toolCall.name, toolCall.arguments, engine)
            )
        }
        val toolMessage = Message.toolResponses(responses)
        return streamWithToolSupport(
            messageToPrefill = toolMessage,
            onToken = onToken,
            toolCallCount = toolCallCount + 1
        )
    }

    private fun buildPromptForStream(message: Message): Prompt {
        val prompt = if (!prefilled) {
            val messages = _history.value + message
            renderFullPrompt(
                systemPrompt = config.systemPrompt,
                toolRegistry = toolRegistry,
                messages = messages
            )
        } else {
            renderIncrementalPrompt(listOf(message))
        }
        prefilled = true
        _isConversationReady.value = true
        return prompt
    }

    private suspend fun streamDecode(
        prompt: Prompt,
        onToken: (String) -> Unit
    ): StreamResult {
        val session = sessionPtr ?: error("Session is not initialized.")
        return suspendCancellableCoroutine { continuation ->
            val fullBuffer = StringBuilder()
            val visibleBuffer = StringBuilder()
            val toolFenceFilter = ToolFenceFilter()
            val result = nativeSessionGenerateContentStream(
                session = session,
                inputs = prompt.nativeInputs,
                callback = object : NativeStreamCallback {
                    override fun onChunk(chunk: String) {
                        fullBuffer.append(chunk)
                        val visibleDelta = toolFenceFilter.consume(chunk)
                        if (visibleDelta.isNotEmpty()) {
                            visibleBuffer.append(visibleDelta)
                            onToken(visibleBuffer.toString())
                            updateGeneratingMessage(
                                fullText = fullBuffer.toString(),
                                visibleText = visibleBuffer.toString(),
                            )
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

    private fun updateGeneratingMessage(
        fullText: String,
        visibleText: String,
    ) {
        val generatingMessage = _generatingMessage.value ?: Message.assistant(isLoading = true)
        val newMessage = generatingMessage.copy(
            fullText = Content.Text(fullText),
            visibleText = visibleText,
            isLoading = visibleText.isBlank(),
        )
        _generatingMessage.value = newMessage
    }

    private fun commitGeneratingMessage(
        fullText: String,
        toolCalls: List<ToolCall>,
    ) {
        val oldMessage = _generatingMessage.value ?: return
        val newMessage = oldMessage.copy(
            fullText = Content.Text(fullText),
            isLoading = false,
            toolCalls = toolCalls,
        )
        _generatingMessage.value = null
        _history.update { it + newMessage }
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
                toolCalls.add(
                    ToolCall(
                        name = functionName,
                        argumentsString = argsMatch?.groupValues[1],
                        argumentStyle = ToolCall.ArgumentStyle.JSON,
                    )
                )
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
                    toolCalls.add(
                        ToolCall(
                            name = functionName,
                            argumentsString = argsString,
                            argumentStyle = ToolCall.ArgumentStyle.PYTHON,
                        )
                    )
                }
            }
            return toolCalls
        }
    }
}