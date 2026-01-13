package com.aayush.simpleai.util

enum class LlmBackend(val value: String) {
    GPU("gpu"),
    CPU("cpu"),
}

/**
 * Tool executor interface - implement this with actual tool logic.
 * The generated ToolExecutor object will delegate to this.
 */
object CommonToolExecutor {
    /**
     * Execute a tool by name with the given parameters.
     * @param toolName The snake_case tool name from tools.json
     * @param params Map of parameter names to values
     * @return The tool result as a string
     */
    fun execute(toolName: String, params: Map<String, Any?>): String {
        return when (toolName) {
            "weather_search" -> executeSampleSearch(params["location"] as? String ?: "")
            else -> "Error: Unknown tool '$toolName'"
        }
    }
    
    private fun executeSampleSearch(query: String): String {
        // In a real application, you would call a search API here
        return "It's cold and rainy today, with a high of 20F and 1 inch of rain predicted today with a 90% chance"
    }
}

data class LlmEngineConfig(
    val modelPath: String,
    val backend: LlmBackend = LlmBackend.GPU,
    val maxNumTokens: Int = 4096,
    val cacheDir: String? = null
)

data class LlmSamplerConfig(
    val topK: Int = 40,
    val topP: Double = 0.95,
    val temperature: Double = 0.8
)

// tools defined in tools.json and the above executor
data class LlmConversationConfig(
    val samplerConfig: LlmSamplerConfig = LlmSamplerConfig(),
    val systemPrompt: String? = null
)

interface LlmMessageCallback {
    fun onMessage(message: String)
    fun onDone()
    fun onError(throwable: Throwable)
}

interface LlmConversation {
    suspend fun sendMessageAsync(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: (String) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    )
    
    fun close()
}

interface LlmEngine {
    fun initialize()
    fun createConversation(config: LlmConversationConfig): LlmConversation
    fun close()
}

interface LlmEngineProvider {
    fun createEngine(modelPath: String, backend: LlmBackend = LlmBackend.GPU): LlmEngine
}

expect fun createLlmEngine(config: LlmEngineConfig): LlmEngine