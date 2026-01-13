package com.aayush.simpleai.util

import com.aayush.simpleai.generated.GeneratedToolSet
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ConversationConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createLlmEngine(config: LlmEngineConfig): LlmEngine {
    return AndroidLlmEngine(config)
}

private class AndroidLlmEngine(
    private val config: LlmEngineConfig
) : LlmEngine {
    private var engine: Engine? = null
    
    // The generated tool set from KSP (reads from tools.json)
    private val toolSet = GeneratedToolSet()
    
    override fun initialize() {
        val engineConfig = EngineConfig(
            modelPath = config.modelPath,
            backend = when (config.backend) {
                LlmBackend.GPU -> Backend.GPU
                LlmBackend.CPU -> Backend.CPU
            },
            maxNumTokens = config.maxNumTokens,
            cacheDir = config.cacheDir
        )
        
        engine = Engine(engineConfig).apply { initialize() }
    }
    
    override fun createConversation(config: LlmConversationConfig): LlmConversation {
        val engine = this.engine ?: throw IllegalStateException("Engine not initialized")
        
        val samplerConfig = SamplerConfig(
            topK = config.samplerConfig.topK,
            topP = config.samplerConfig.topP,
            temperature = config.samplerConfig.temperature
        )
        
        val systemMessage = config.systemPrompt?.let { 
            Message.of(listOf(Content.Text(it)))
        }
        
        val conversationConfig = ConversationConfig(
            samplerConfig = samplerConfig,
            tools = listOf(toolSet),
            systemMessage = systemMessage
        )
        
        val conversation = engine.createConversation(conversationConfig)
        return AndroidLlmConversation(conversation)
    }
    
    override fun close() {
        try {
            engine?.close()
        } catch (e: Exception) {
            // Ignore
        }
        engine = null
    }
}

private class AndroidLlmConversation(
    private val conversation: Conversation
) : LlmConversation {
    
    override suspend fun sendMessageAsync(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        suspendCancellableCoroutine { continuation ->
            val response = StringBuilder()
            
            conversation.sendMessageAsync(
                Message.of(contents = listOf(Content.Text(text = prompt))),
                callback = object : MessageCallback {
                    override fun onMessage(message: Message) {
                        response.append(message.toString())
                        onToken(response.toString().trim())
                    }
                    
                    override fun onDone() {
                        onDone(response.toString().trim())
                        continuation.resume(Unit)
                    }
                    
                    override fun onError(throwable: Throwable) {
                        if (throwable is CancellationException) {
                            onDone(response.toString().trim())
                            continuation.resume(Unit)
                        } else {
                            onError(throwable)
                            continuation.resumeWithException(throwable)
                        }
                    }
                }
            )
        }
    }
    
    override fun close() {
        try {
            conversation.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
