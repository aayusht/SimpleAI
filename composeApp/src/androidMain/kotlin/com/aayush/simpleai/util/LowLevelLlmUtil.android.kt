@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.aayush.simpleai.util

import com.google.ai.edge.litertlm.BenchmarkInfo
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.LiteRtLmJni
import com.google.ai.edge.litertlm.SamplerConfig

// ============================================================================
// Pointer Types - Android implementations (Long values or state holders)
// ============================================================================

actual class EngineSettingsPtr(
    var modelPath: String,
    var backend: String,
    var visionBackend: String? = null,
    var audioBackend: String? = null,
    var maxNumTokens: Int = 0,
    var cacheDir: String? = null,
    var enableBenchmark: Boolean = false
)

actual class EnginePtr(val value: Long)

actual class SessionConfigPtr(
    var samplerType: SamplerType = SamplerType.TOP_P,
    var topK: Int = 40,
    var topP: Float = 0.95f,
    var temperature: Float = 0.8f,
    var seed: Int = 0,
    var maxOutputTokens: Int? = null
)

actual class SessionPtr(val value: Long)

actual class ConversationConfigPtr(
    val enginePtr: Long,
    var sessionConfig: SessionConfigPtr? = null,
    var systemMessageJson: String? = null,
    var toolsDescriptionJson: String = "",
    var enableConstrainedDecoding: Boolean = false
)

actual class ConversationPtr(val value: Long)

actual class ResponsesPtr(val text: String)

actual class JsonResponsePtr(val text: String)

actual class BenchmarkInfoPtr(val info: BenchmarkInfo)

// ============================================================================
// Platform Lock
// ============================================================================

actual class PlatformLock {
    val lock = Any()

    actual inline fun <T> withLock(block: () -> T): T {
        return synchronized(lock) { block() }
    }
}

// ============================================================================
// Engine Settings Functions
// ============================================================================

actual fun nativeEngineSettingsCreate(
    modelPath: String,
    backend: String,
    visionBackend: String?,
    audioBackend: String?
): EngineSettingsPtr {
    return EngineSettingsPtr(
        modelPath = modelPath,
        backend = backend,
        visionBackend = visionBackend,
        audioBackend = audioBackend
    )
}

actual fun nativeEngineSettingsSetMaxNumTokens(
    settings: EngineSettingsPtr,
    maxNumTokens: Int
) {
    settings.maxNumTokens = maxNumTokens
}

actual fun nativeEngineSettingsSetCacheDir(
    settings: EngineSettingsPtr,
    cacheDir: String
) {
    settings.cacheDir = cacheDir
}

actual fun nativeEngineSettingsEnableBenchmark(settings: EngineSettingsPtr) {
    settings.enableBenchmark = true
}

actual fun nativeEngineSettingsDelete(settings: EngineSettingsPtr) {
    // No-op on Android, settings is just a data holder
}

// ============================================================================
// Engine Functions
// ============================================================================

actual fun nativeEngineCreate(settings: EngineSettingsPtr): EnginePtr {
    val ptr = LiteRtLmJni.nativeCreateEngine(
        modelPath = settings.modelPath,
        backend = settings.backend,
        visionBackend = settings.visionBackend ?: "",
        audioBackend = settings.audioBackend ?: "",
        maxNumTokens = settings.maxNumTokens,
        cacheDir = settings.cacheDir ?: "",
        enableBenchmark = settings.enableBenchmark
    )
    return EnginePtr(ptr)
}

actual fun nativeEngineDelete(engine: EnginePtr) {
    LiteRtLmJni.nativeDeleteEngine(engine.value)
}

actual fun nativeEngineCreateSession(
    engine: EnginePtr,
    config: SessionConfigPtr?
): SessionPtr {
    val samplerConfig = config?.let {
        SamplerConfig(
            topK = it.topK,
            topP = it.topP.toDouble(),
            temperature = it.temperature.toDouble(),
            seed = it.seed
        )
    }
    val ptr = LiteRtLmJni.nativeCreateSession(engine.value, samplerConfig)
    return SessionPtr(ptr)
}

actual fun nativeSessionRunPrefill(
    session: SessionPtr,
    inputs: Array<NativeInputData>
) {
    val inputDataArray = inputs.map { input ->
        when {
            input.text != null -> InputData.Text(input.text)
            input.imageBytes != null -> InputData.Image(input.imageBytes)
            input.audioBytes != null -> InputData.Audio(input.audioBytes)
            else -> throw IllegalArgumentException("Input data must have text, image, or audio")
        }
    }.toTypedArray()
    
    LiteRtLmJni.nativeRunPrefill(session.value, inputDataArray)
}

actual fun nativeSessionRunDecode(
    session: SessionPtr
): ResponsesPtr {
    val result = LiteRtLmJni.nativeRunDecode(session.value)
    return ResponsesPtr(result)
}

// ============================================================================
// Session Config Functions
// ============================================================================

actual fun nativeSessionConfigCreate(
    samplerType: SamplerType,
    topK: Int,
    topP: Float,
    temperature: Float,
    seed: Int
): SessionConfigPtr {
    return SessionConfigPtr(
        samplerType = samplerType,
        topK = topK,
        topP = topP,
        temperature = temperature,
        seed = seed
    )
}

actual fun nativeSessionConfigSetMaxOutputTokens(
    config: SessionConfigPtr,
    maxOutputTokens: Int
) {
    config.maxOutputTokens = maxOutputTokens
}

actual fun nativeSessionConfigDelete(config: SessionConfigPtr) {
    // No-op on Android, config is just a data holder
}

// ============================================================================
// Session Functions
// ============================================================================

actual fun nativeSessionGenerateContent(
    session: SessionPtr,
    inputs: Array<NativeInputData>
): ResponsesPtr {
    val inputDataArray = inputs.map { input ->
        when {
            input.text != null -> InputData.Text(input.text)
            input.imageBytes != null -> InputData.Image(input.imageBytes)
            input.audioBytes != null -> InputData.Audio(input.audioBytes)
            else -> throw IllegalArgumentException("Input data must have text, image, or audio")
        }
    }.toTypedArray()
    
    val result = LiteRtLmJni.nativeGenerateContent(session.value, inputDataArray)
    return ResponsesPtr(result)
}

// Store active session callbacks
private val activeSessionCallbacks = mutableMapOf<Long, Any>()

actual fun nativeSessionGenerateContentStream(
    session: SessionPtr,
    inputs: Array<NativeInputData>,
    callback: NativeStreamCallback
): Int {
    val inputDataArray = inputs.map { input ->
        when {
            input.text != null -> InputData.Text(input.text)
            input.imageBytes != null -> InputData.Image(input.imageBytes)
            input.audioBytes != null -> InputData.Audio(input.audioBytes)
            else -> throw IllegalArgumentException("Input data must have text, image, or audio")
        }
    }.toTypedArray()
    
    return try {
        LiteRtLmJni.nativeGenerateContentStream(
            sessionPointer = session.value,
            inputData = inputDataArray,
            callback = object : LiteRtLmJni.JniInferenceCallback {
                override fun onNext(response: String) {
                    callback.onChunk(response)
                }
                
                override fun onDone() {
                    callback.onDone()
                }
                
                override fun onError(statusCode: Int, message: String) {
                    callback.onError(message)
                }
            }
        )
        0 // Success
    } catch (e: Exception) {
        -1 // Error
    }
}

actual fun nativeSessionGetBenchmarkInfo(session: SessionPtr): BenchmarkInfoPtr? {
    // Note: Android JNI doesn't have a direct session benchmark info getter
    // This would need to be added to the JNI if needed
    return null
}

actual fun nativeSessionDelete(session: SessionPtr) {
    LiteRtLmJni.nativeDeleteSession(session.value)
}

// ============================================================================
// Responses Functions
// ============================================================================

actual fun nativeResponsesGetNumCandidates(responses: ResponsesPtr): Int {
    // Android returns a single string, so always 1 candidate
    return 1
}

actual fun nativeResponsesGetResponseTextAt(responses: ResponsesPtr, index: Int): String? {
    return if (index == 0) responses.text else null
}

actual fun nativeResponsesDelete(responses: ResponsesPtr) {
    // No-op on Android, responses is just a string wrapper
}

// ============================================================================
// Conversation Config Functions
// ============================================================================

actual fun nativeConversationConfigCreate(
    engine: EnginePtr,
    sessionConfig: SessionConfigPtr?,
    systemMessageJson: String?
): ConversationConfigPtr {
    return ConversationConfigPtr(
        enginePtr = engine.value,
        sessionConfig = sessionConfig,
        systemMessageJson = systemMessageJson
    )
}

actual fun nativeConversationConfigDelete(config: ConversationConfigPtr) {
    // No-op on Android, config is just a data holder
}

// ============================================================================
// Conversation Functions
// ============================================================================

actual fun nativeConversationCreate(
    engine: EnginePtr,
    config: ConversationConfigPtr
): ConversationPtr {
    val samplerConfig = config.sessionConfig?.let {
        SamplerConfig(
            topK = it.topK,
            topP = it.topP.toDouble(),
            temperature = it.temperature.toDouble(),
            seed = it.seed
        )
    }
    
    val ptr = LiteRtLmJni.nativeCreateConversation(
        enginePointer = engine.value,
        samplerConfig = samplerConfig,
        systemMessageJsonString = config.systemMessageJson ?: "",
        toolsDescriptionJsonString = config.toolsDescriptionJson,
        enableConversationConstrainedDecoding = config.enableConstrainedDecoding
    )
    return ConversationPtr(ptr)
}

actual fun nativeConversationDelete(conversation: ConversationPtr) {
    LiteRtLmJni.nativeDeleteConversation(conversation.value)
}

// Store active conversation callbacks
private val activeConversationCallbacks = mutableMapOf<Long, Any>()

actual fun nativeConversationSendMessageStream(
    conversation: ConversationPtr,
    messageJson: String,
    callback: NativeMessageCallback
): Int {
    return try {
        LiteRtLmJni.nativeSendMessageAsync(
            conversationPointer = conversation.value,
            messageJsonString = messageJson,
            callback = object : LiteRtLmJni.JniMessageCallback {
                override fun onMessage(messageJsonString: String) {
                    callback.onMessage(messageJsonString)
                }
                
                override fun onDone() {
                    callback.onDone()
                }
                
                override fun onError(statusCode: Int, message: String) {
                    callback.onError(statusCode, message)
                }
            }
        )
        0 // Success
    } catch (e: Exception) {
        -1 // Error
    }
}

actual fun nativeConversationSendMessage(
    conversation: ConversationPtr,
    messageJson: String
): JsonResponsePtr {
    val result = LiteRtLmJni.nativeSendMessage(
        conversationPointer = conversation.value,
        messageJsonString = messageJson
    )
    return JsonResponsePtr(result)
}

actual fun nativeConversationCancelProcess(conversation: ConversationPtr) {
    LiteRtLmJni.nativeConversationCancelProcess(conversation.value)
}

actual fun nativeConversationGetBenchmarkInfo(conversation: ConversationPtr): BenchmarkInfoPtr? {
    return try {
        val info = LiteRtLmJni.nativeConversationGetBenchmarkInfo(conversation.value)
        BenchmarkInfoPtr(info)
    } catch (e: Exception) {
        null
    }
}

// ============================================================================
// JSON Response Functions
// ============================================================================

actual fun nativeJsonResponseGetString(response: JsonResponsePtr): String? {
    return response.text
}

actual fun nativeJsonResponseDelete(response: JsonResponsePtr) {
    // No-op on Android, response is just a string wrapper
}

// ============================================================================
// Benchmark Info Functions
// Note: Android's BenchmarkInfo only exposes lastPrefillTokenCount and lastDecodeTokenCount.
// The detailed per-turn metrics available on iOS are not exposed in the Android JNI.
// ============================================================================

actual fun nativeBenchmarkInfoGetNumPrefillTurns(benchmarkInfo: BenchmarkInfoPtr): Int {
    // Android only tracks the last prefill, so return 1 if there was a prefill
    return if (benchmarkInfo.info.lastPrefillTokenCount > 0) 1 else 0
}

actual fun nativeBenchmarkInfoGetPrefillTokensPerSecAt(benchmarkInfo: BenchmarkInfoPtr, index: Int): Double {
    // Not available on Android - would need JNI extension to expose
    return 0.0
}

actual fun nativeBenchmarkInfoGetNumDecodeTurns(benchmarkInfo: BenchmarkInfoPtr): Int {
    // Android only tracks the last decode, so return 1 if there was a decode
    return if (benchmarkInfo.info.lastDecodeTokenCount > 0) 1 else 0
}

actual fun nativeBenchmarkInfoGetDecodeTokensPerSecAt(benchmarkInfo: BenchmarkInfoPtr, index: Int): Double {
    // Not available on Android - would need JNI extension to expose
    return 0.0
}

actual fun nativeBenchmarkInfoGetTimeToFirstToken(benchmarkInfo: BenchmarkInfoPtr): Double {
    // Not available on Android - would need JNI extension to expose
    return 0.0
}

actual fun nativeBenchmarkInfoGetLastPrefillTokenCount(benchmarkInfo: BenchmarkInfoPtr): Int {
    return benchmarkInfo.info.lastPrefillTokenCount
}

actual fun nativeBenchmarkInfoGetLastDecodeTokenCount(benchmarkInfo: BenchmarkInfoPtr): Int {
    return benchmarkInfo.info.lastDecodeTokenCount
}

actual fun nativeBenchmarkInfoDelete(benchmarkInfo: BenchmarkInfoPtr) {
    // No-op on Android, BenchmarkInfo is a Kotlin data class
}

// ============================================================================
// Logging Functions
// ============================================================================

actual fun nativeSetMinLogLevel(level: Int) {
    LiteRtLmJni.nativeSetMinLogSeverity(level)
}
