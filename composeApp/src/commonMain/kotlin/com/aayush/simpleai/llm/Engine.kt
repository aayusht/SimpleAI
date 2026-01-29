package com.aayush.simpleai.llm

import com.aayush.simpleai.util.EnginePtr
import com.aayush.simpleai.util.EngineSettingsPtr
import com.aayush.simpleai.util.PlatformLock
import com.aayush.simpleai.util.SamplerType
import com.aayush.simpleai.util.nativeEngineCreate
import com.aayush.simpleai.util.nativeEngineDelete
import com.aayush.simpleai.util.nativeEngineSettingsCreate
import com.aayush.simpleai.util.nativeEngineSettingsDelete
import com.aayush.simpleai.util.nativeEngineSettingsEnableBenchmark
import com.aayush.simpleai.util.nativeEngineSettingsSetCacheDir
import com.aayush.simpleai.util.nativeEngineSettingsSetMaxNumTokens

enum class Backend(val value: String) {
    NPU("npu"),
    GPU("gpu"),
    CPU("cpu")
}

data class EngineConfig(
    val modelPath: String,
    val backend: String = Backend.GPU.value,
    val visionBackend: String = Backend.GPU.value,
    val audioBackend: String = Backend.CPU.value,
    val maxNumTokens: Int = 32768,
    val cacheDir: String? = null,
    val enableBenchmark: Boolean = false
)

data class SamplerConfig(
    val type: SamplerType = SamplerType.TOP_P,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val temperature: Double = 0.8,
    val seed: Int = 0
)

data class ConversationConfig(
    val samplerConfig: SamplerConfig = SamplerConfig(),
    val systemPrompt: String? = null,
    val tools: List<ToolDefinition> = emptyList(),
    val prefillMessages: List<Message> = emptyList(),
    val maxOutputTokens: Int? = null,
    val implementationType: ImplementationType = ImplementationType.SESSION_BASED,
) {
    enum class ImplementationType {
        SESSION_BASED, CONVERSATION_BASED
    }
}

class Engine(private val config: EngineConfig) : AutoCloseable {
    private val lock = PlatformLock()
    private var enginePtr: EnginePtr? = null

    fun isInitialized(): Boolean = enginePtr != null

    fun initialize() {
        lock.withLock {
            check(value = !isInitialized()) { "Engine is already initialized." }

            val settings: EngineSettingsPtr =
                nativeEngineSettingsCreate(
                    modelPath = config.modelPath,
                    backend = config.backend,
                    visionBackend = config.visionBackend,
                    audioBackend = config.audioBackend
                )
            try {
                nativeEngineSettingsSetMaxNumTokens(settings, config.maxNumTokens)
                config.cacheDir?.let { nativeEngineSettingsSetCacheDir(settings, it) }
                if (config.enableBenchmark) {
                    nativeEngineSettingsEnableBenchmark(settings)
                }
                enginePtr = nativeEngineCreate(settings)
            } finally {
                nativeEngineSettingsDelete(settings)
            }
        }
    }

    fun createConversation(config: ConversationConfig = ConversationConfig()): Conversation {
        lock.withLock {
            val engine = enginePtr ?: error("Engine is not initialized.")
            return when (config.implementationType) {
                ConversationConfig.ImplementationType.SESSION_BASED -> SessionBasedConversation(
                    engine = engine,
                    config = config
                )
                ConversationConfig.ImplementationType.CONVERSATION_BASED -> ConversationBasedConversation(
                    engine = engine,
                    config = config
                )
            }
        }
    }

    override fun close() {
        lock.withLock {
            val engine = enginePtr ?: return
            nativeEngineDelete(engine)
            enginePtr = null
        }
    }
}