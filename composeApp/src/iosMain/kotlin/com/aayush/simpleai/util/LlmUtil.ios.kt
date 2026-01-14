@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aayush.simpleai.util

import cnames.structs.LiteRtLmConversation
import cnames.structs.LiteRtLmEngine
import kotlinx.cinterop.*
import kotlinx.coroutines.suspendCancellableCoroutine
import litert.lm.c.*
import platform.Foundation.NSFileManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createLlmEngine(config: LlmEngineConfig): LlmEngine {
    return IosLlmEngine(config)
}

private class IosLlmEngine(private val config: LlmEngineConfig) : LlmEngine {
    
    private var enginePtr: CPointer<LiteRtLmEngine>? = null
    
    override fun initialize() {
        // Verify model file exists
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(config.modelPath)) {
            throw IllegalStateException(message = "Model file not found at: ${config.modelPath}")
        }
        
        val settings = litert_lm_engine_settings_create(
            model_path = config.modelPath,
            backend_str = config.backend.value,
            vision_backend_str = null,
            audio_backend_str = null
        ) ?: throw IllegalStateException(message = "Failed to create engine settings for model at: ${config.modelPath}")
        
        try {
            // Set optional settings
            litert_lm_engine_settings_set_max_num_tokens(settings, max_num_tokens = config.maxNumTokens)
            
            config.cacheDir?.let { cacheDir ->
                litert_lm_engine_settings_set_cache_dir(settings, cacheDir)
            }
            
            // Create the engine
            enginePtr = litert_lm_engine_create(settings)
                ?: throw IllegalStateException("Failed to create LiteRT-LM engine")
        } finally {
            litert_lm_engine_settings_delete(settings)
        }
    }
    
    override fun createConversation(config: LlmConversationConfig): LlmConversation {
        val engine = enginePtr ?: throw IllegalStateException("Engine not initialized")
        
        // Create conversation config with sampler params
        val conversationConfigPtr = memScoped {
            val samplerParams = alloc<LiteRtLmSamplerParams>().apply {
                type = kTopP  // TopP sampler
                top_k = config.samplerConfig.topK
                top_p = config.samplerConfig.topP.toFloat()
                temperature = config.samplerConfig.temperature.toFloat()
                seed = 0
            }
            
            litert_lm_conversation_config_create(
                engine = engine,
                session_config = litert_lm_session_config_create(samplerParams.ptr),
                system_message_json = config.systemPrompt
            )
        }
        
        try {
            // Create the conversation
            val conversationPtr = litert_lm_conversation_create(engine, conversationConfigPtr)
                ?: throw IllegalStateException("Failed to create conversation")
            
            return IosLlmConversation(conversationPtr)
        } finally {
            conversationConfigPtr?.let { litert_lm_conversation_config_delete(it) }
        }
    }
    
    override fun close() {
        enginePtr?.let { ptr ->
            litert_lm_engine_delete(ptr)
        }
        enginePtr = null
    }
}

private class IosLlmConversation(
    private var conversationPtr: CPointer<LiteRtLmConversation>?
) : LlmConversation {
    
    companion object {
        private const val RECURRING_TOOL_CALL_LIMIT = 25
    }
    
    override suspend fun sendMessageAsync(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            val finalResponse = streamWithToolSupport(
                messageJSON = createUserMessageJSON(prompt),
                onToken = onToken,
                toolCallCount = 0
            )
            onDone(finalResponse)
        } catch (e: Throwable) {
            onError(e)
        }
    }
    
    /**
     * Internal streaming implementation with tool call support.
     * Follows the Swift reference pattern: intercept tool_calls, execute tools,
     * send responses, continue until content-only response.
     */
    private suspend fun streamWithToolSupport(
        messageJSON: String,
        onToken: (String) -> Unit,
        toolCallCount: Int
    ): String {
        val ptr = conversationPtr ?: throw IllegalStateException("Conversation is closed")
        
        val result = streamMessageRaw(ptr, messageJSON, onToken)
        
        // Check if response contains tool calls
        if (hasToolCalls(result.fullResponse)) {
            if (toolCallCount >= RECURRING_TOOL_CALL_LIMIT) {
                throw IllegalStateException("Exceeded recurring tool call limit of $RECURRING_TOOL_CALL_LIMIT")
            }
            
            // Execute tools and create response message
            val toolResponseJSON = handleToolCalls(result.fullResponse)
            
            // Continue the conversation with tool responses
            return streamWithToolSupport(
                messageJSON = toolResponseJSON,
                onToken = onToken,
                toolCallCount = toolCallCount + 1
            )
        }
        
        return result.extractedText
    }
    
    /**
     * Streams a message and collects the response.
     */
    private suspend fun streamMessageRaw(
        ptr: CPointer<LiteRtLmConversation>,
        messageJSON: String,
        onToken: (String) -> Unit
    ): StreamResult = suspendCancellableCoroutine { continuation ->
        
        val context = StreamContext(
            onToken = onToken,
            onComplete = { result ->
                continuation.resume(result)
            },
            onError = { error ->
                continuation.resumeWithException(error)
            }
        )
        
        // Store context reference to prevent GC
        val contextRef = StableRef.create(context)
        
        continuation.invokeOnCancellation {
            contextRef.dispose()
            litert_lm_conversation_cancel_process(ptr)
        }
        
        val result = litert_lm_conversation_send_message_stream(
            conversation = ptr,
            message_json = messageJSON,
            callback = staticCFunction { callbackData, chunk, isFinal, errorMsg ->
                val ctx = callbackData?.asStableRef<StreamContext>()?.get() ?: return@staticCFunction
                
                if (errorMsg != null) {
                    val errorString = errorMsg.toKString()
                    ctx.onError(RuntimeException("Stream error: $errorString"))
                    return@staticCFunction
                }
                
                if (chunk != null) {
                    val chunkString = chunk.toKString()
                    ctx.accumulatedJSON = chunkString
                    
                    // Parse JSON response to extract text
                    val parsedText = parseResponseText(chunkString)
                    if (parsedText.isNotEmpty()) {
                        ctx.accumulatedText += parsedText
                        
                        // Only yield content if it doesn't contain tool_code blocks
                        if (!ctx.accumulatedText.contains("```tool_code")) {
                            ctx.onToken(ctx.accumulatedText)
                        }
                    }
                }
                
                if (isFinal) {
                    ctx.onComplete(StreamResult(
                        extractedText = ctx.accumulatedText,
                        fullResponse = ctx.accumulatedJSON
                    ))
                }
            },
            callback_data = contextRef.asCPointer()
        )
        
        if (result != 0) {
            contextRef.dispose()
            continuation.resumeWithException(
                RuntimeException("Failed to start message stream (error code: $result)")
            )
        }
    }
    
    /**
     * Handles tool calls from the model response.
     */
    private fun handleToolCalls(responseJSON: String): String {
        val toolCalls = parseToolCalls(responseJSON)
        
        val responses = toolCalls.map { toolCall ->
            val result = CommonToolExecutor.execute(toolCall.name, toolCall.arguments)
            ToolResponseData(name = toolCall.name, response = result)
        }
        
        return createToolResponseMessage(responses)
    }
    
    override fun close() {
        conversationPtr?.let { ptr ->
            litert_lm_conversation_delete(ptr)
        }
        conversationPtr = null
    }
}

// Helper data classes for streaming
private data class StreamResult(
    val extractedText: String,
    val fullResponse: String
)

private class StreamContext(
    val onToken: (String) -> Unit,
    val onComplete: (StreamResult) -> Unit,
    val onError: (Throwable) -> Unit,
    var accumulatedText: String = "",
    var accumulatedJSON: String = ""
)

private data class ToolCall(
    val name: String,
    val arguments: Map<String, Any?>
)

private data class ToolResponseData(
    val name: String,
    val response: Any
)

// JSON utility functions

/**
 * Creates a user message JSON in the format expected by the native API.
 */
private fun createUserMessageJSON(text: String): String {
    // Escape special characters for JSON
    val escapedText = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    
    return """{"role":"user","content":[{"type":"text","text":"$escapedText"}]}"""
}

/**
 * Parses the response text from a JSON response string.
 */
private fun parseResponseText(jsonString: String): String {
    // Simple JSON parsing for content text extraction
    // Looking for: {"role":"...","content":[{"type":"text","text":"..."}]}
    
    val textPattern = """"text"\s*:\s*"([^"\\]*(?:\\.[^"\\]*)*)"""".toRegex()
    val matches = textPattern.findAll(jsonString)
    
    return matches.mapNotNull { match ->
        val captured = match.groupValues.getOrNull(1) ?: return@mapNotNull null
        // Unescape JSON string
        captured
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }.joinToString("")
}

/**
 * Checks if a JSON response contains tool calls.
 */
private fun hasToolCalls(jsonString: String): Boolean {
    // Check for structured tool_calls
    if (jsonString.contains("\"tool_calls\"")) {
        return true
    }
    
    // Check for tool_code blocks in content
    if (jsonString.contains("```tool_code")) {
        return true
    }
    
    return false
}

/**
 * Parses tool calls from a JSON response string.
 * Supports both structured tool_calls and tool_code blocks in content.
 */
private fun parseToolCalls(jsonString: String): List<ToolCall> {
    val toolCalls = mutableListOf<ToolCall>()
    
    // First check for structured tool_calls
    val toolCallsPattern = """"tool_calls"\s*:\s*\[([^\]]*)\]""".toRegex()
    val toolCallsMatch = toolCallsPattern.find(jsonString)
    
    if (toolCallsMatch != null) {
        // Parse structured tool calls
        val toolCallsContent = toolCallsMatch.groupValues[1]
        val functionPattern = """"function"\s*:\s*\{[^}]*"name"\s*:\s*"([^"]+)"[^}]*\}""".toRegex()
        
        functionPattern.findAll(toolCallsContent).forEach { match ->
            val functionName = match.groupValues[1]
            // For now, extract arguments from the JSON - simplified parsing
            val argsPattern = """"arguments"\s*:\s*\{([^}]*)\}""".toRegex()
            val argsMatch = argsPattern.find(match.value)
            val arguments = if (argsMatch != null) {
                parseArgumentsFromJSON(argsMatch.groupValues[1])
            } else {
                emptyMap()
            }
            toolCalls.add(ToolCall(name = functionName, arguments = arguments))
        }
        
        if (toolCalls.isNotEmpty()) {
            return toolCalls
        }
    }
    
    // Extract and unescape the text content from JSON first
    val textContent = parseResponseText(jsonString)
    
    // Fall back to parsing tool_code blocks from the unescaped content
    return parseToolCodeBlocks(textContent)
}

/**
 * Parses tool_code blocks from response text.
 * Format: ```tool_code\nprint(default_api.function_name(arg="value"))\n```
 */
private fun parseToolCodeBlocks(text: String): List<ToolCall> {
    val toolCalls = mutableListOf<ToolCall>()
    
    val toolCodePattern = """```tool_code\s*\n([\s\S]*?)```""".toRegex()
    
    toolCodePattern.findAll(text).forEach { match ->
        val codeBlock = match.groupValues[1].trim()
        
        // Parse function call: print(default_api.func_name(args)) or default_api.func_name(args) or func_name(args)
        val functionPattern = """(?:print\s*\(\s*)?(?:default_api\.)?(\w+)\s*\((.*?)\)\s*\)?""".toRegex()
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

/**
 * Parses Python-style keyword arguments: arg1="value1", arg2=123, arg3=true
 */
private fun parsePythonStyleArguments(argsString: String): Map<String, Any?> {
    val arguments = mutableMapOf<String, Any?>()
    
    // Pattern for keyword arguments: name="value" or name='value' or name=value
    val argPattern = """(\w+)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\d+(?:\.\d+)?)|(\w+))""".toRegex()
    
    argPattern.findAll(argsString).forEach { match ->
        val name = match.groupValues[1]
        
        when {
            // Double-quoted string
            match.groupValues[2].isNotEmpty() -> {
                arguments[name] = match.groupValues[2]
            }
            // Single-quoted string
            match.groupValues[3].isNotEmpty() -> {
                arguments[name] = match.groupValues[3]
            }
            // Numeric value
            match.groupValues[4].isNotEmpty() -> {
                val numStr = match.groupValues[4]
                arguments[name] = if (numStr.contains(".")) {
                    numStr.toDoubleOrNull() ?: numStr
                } else {
                    numStr.toIntOrNull() ?: numStr
                }
            }
            // Identifier (true/false/other)
            match.groupValues[5].isNotEmpty() -> {
                val value = match.groupValues[5]
                arguments[name] = when (value.lowercase()) {
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

/**
 * Parses arguments from JSON format.
 */
private fun parseArgumentsFromJSON(jsonContent: String): Map<String, Any?> {
    val arguments = mutableMapOf<String, Any?>()
    
    // Simple pattern for "key": "value" or "key": number
    val argPattern = """"(\w+)"\s*:\s*(?:"([^"]*)"|(\d+(?:\.\d+)?)|(\w+))""".toRegex()
    
    argPattern.findAll(jsonContent).forEach { match ->
        val name = match.groupValues[1]
        
        when {
            match.groupValues[2].isNotEmpty() -> {
                arguments[name] = match.groupValues[2]
            }
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

/**
 * Creates a tool response message JSON to send back to the model.
 */
private fun createToolResponseMessage(responses: List<ToolResponseData>): String {
    val contentBuilder = StringBuilder()
    contentBuilder.append("[")
    
    responses.forEachIndexed { index, response ->
        if (index > 0) contentBuilder.append(",")
        
        val escapedResponse = response.response.toString()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        
        contentBuilder.append("""{"type":"tool_response","name":"${response.name}","response":"$escapedResponse"}""")
    }
    
    contentBuilder.append("]")
    
    return """{"role":"tool","content":${contentBuilder}}"""
}
