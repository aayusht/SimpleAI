@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
package com.aayush.simpleai.util

/**
 * Low-level LiteRT-LM API that maps directly to native methods.
 * 
 * This provides expect/actual declarations that wrap:
 * - Android: LiteRtLmJni native methods
 * - iOS: C interop functions from LiteRtLm framework
 */

expect fun supportsMediaInSession(): Boolean

// ============================================================================
// Pointer Types - Typed wrappers for native pointers
// ============================================================================

/** Pointer to engine settings (iOS: CPointer<LiteRtLmEngineSettings>, Android: internal state) */
expect class EngineSettingsPtr

/** Pointer to a LiteRT-LM engine */
expect class EnginePtr

/** Pointer to session config (iOS: CPointer<LiteRtLmSessionConfig>, Android: internal state) */
expect class SessionConfigPtr

/** Pointer to a LiteRT-LM session */
expect class SessionPtr

/** Pointer to conversation config (iOS: CPointer<LiteRtLmConversationConfig>, Android: internal state) */
expect class ConversationConfigPtr

/** Pointer to a LiteRT-LM conversation */
expect class ConversationPtr

/** Pointer to responses from session generate content */
expect class ResponsesPtr

/** Pointer to JSON response */
expect class JsonResponsePtr

/** Pointer to benchmark info */
expect class BenchmarkInfoPtr

// ============================================================================
// Platform Lock
// ============================================================================

/**
 * Simple non-suspending lock for critical sections across platforms.
 */
expect class PlatformLock() {
    inline fun <T> withLock(block: () -> T): T
}

// ============================================================================
// Engine Settings Functions
// ============================================================================

/**
 * Creates engine settings.
 *
 * @param modelPath The path to the model file.
 * @param backend The backend string ("cpu", "gpu", "npu").
 * @param visionBackend The vision backend string (null if not used).
 * @param audioBackend The audio backend string (null if not used).
 * @return Pointer to engine settings.
 */
expect fun nativeEngineSettingsCreate(
    modelPath: String,
    backend: String,
    visionBackend: String? = null,
    audioBackend: String? = null
): EngineSettingsPtr

/**
 * Sets the maximum number of tokens in engine settings.
 */
expect fun nativeEngineSettingsSetMaxNumTokens(
    settings: EngineSettingsPtr,
    maxNumTokens: Int
)

/**
 * Sets the cache directory in engine settings.
 */
expect fun nativeEngineSettingsSetCacheDir(
    settings: EngineSettingsPtr,
    cacheDir: String
)

/**
 * Enables benchmark mode in engine settings.
 */
expect fun nativeEngineSettingsEnableBenchmark(
    settings: EngineSettingsPtr
)

/**
 * Deletes engine settings.
 */
expect fun nativeEngineSettingsDelete(settings: EngineSettingsPtr)

// ============================================================================
// Engine Functions
// ============================================================================

/**
 * Creates a new LiteRT-LM engine from settings.
 *
 * @param settings The engine settings.
 * @return Pointer to the engine.
 */
expect fun nativeEngineCreate(settings: EngineSettingsPtr): EnginePtr

/**
 * Deletes a LiteRT-LM engine.
 */
expect fun nativeEngineDelete(engine: EnginePtr)

/**
 * Creates a session from an engine.
 *
 * @param engine The engine pointer.
 * @param config The session config (or null for defaults).
 * @return Pointer to the session.
 */
expect fun nativeEngineCreateSession(
    engine: EnginePtr,
    config: SessionConfigPtr?
): SessionPtr

/**
 * Runs prefill on a session. This adds content to the KV cache without generating a response.
 * Use this to "load" history into a fresh session.
 */
expect fun nativeSessionRunPrefill(
    session: SessionPtr,
    inputs: Array<NativeInputData>
)

/**
 * Runs decode on a session to generate a single response turn.
 * @return Pointer to responses.
 */
expect fun nativeSessionRunDecode(
    session: SessionPtr
): ResponsesPtr

// ============================================================================
// Session Config Functions
// ============================================================================

/**
 * Sampler type enum matching native kTopK, kTopP, kGreedy, kTypeUnspecified.
 */
enum class SamplerType(val value: Int) {
    TYPE_UNSPECIFIED(0),
    GREEDY(1),
    TOP_K(2),
    TOP_P(3)
}

/**
 * Creates a session config with sampler parameters.
 *
 * @param samplerType The sampler type.
 * @param topK The top-K parameter.
 * @param topP The top-P parameter.
 * @param temperature The temperature parameter.
 * @param seed The random seed.
 * @return Pointer to session config.
 */
expect fun nativeSessionConfigCreate(
    samplerType: SamplerType = SamplerType.TOP_P,
    topK: Int = 40,
    topP: Float = 0.95f,
    temperature: Float = 0.8f,
    seed: Int = 0
): SessionConfigPtr

/**
 * Sets max output tokens on session config.
 */
expect fun nativeSessionConfigSetMaxOutputTokens(
    config: SessionConfigPtr,
    maxOutputTokens: Int
)

/**
 * Deletes session config.
 */
expect fun nativeSessionConfigDelete(config: SessionConfigPtr)

// ============================================================================
// Session Functions
// ============================================================================

/**
 * Input data type for session generate content.
 */
data class NativeInputData(
    val text: String? = null,
    val imageBytes: ByteArray? = null,
    val audioBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NativeInputData) return false
        if (text != other.text) return false
        if (imageBytes != null) {
            if (other.imageBytes == null) return false
            if (!imageBytes.contentEquals(other.imageBytes)) return false
        } else if (other.imageBytes != null) return false
        if (audioBytes != null) {
            if (other.audioBytes == null) return false
            if (!audioBytes.contentEquals(other.audioBytes)) return false
        } else if (other.audioBytes != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = text?.hashCode() ?: 0
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (audioBytes?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Generates content from input data (non-streaming).
 *
 * @param session The session pointer.
 * @param inputs Array of input data.
 * @return Pointer to responses.
 */
expect fun nativeSessionGenerateContent(
    session: SessionPtr,
    inputs: Array<NativeInputData>
): ResponsesPtr

/**
 * Callback for streaming generation.
 */
interface NativeStreamCallback {
    fun onChunk(chunk: String)
    fun onDone()
    fun onError(errorMessage: String)
}

/**
 * Generates content from input data (streaming).
 *
 * @param session The session pointer.
 * @param inputs Array of input data.
 * @param callback The streaming callback.
 * @return 0 on success, error code on failure.
 */
expect fun nativeSessionGenerateContentStream(
    session: SessionPtr,
    inputs: Array<NativeInputData>,
    callback: NativeStreamCallback
): Int

/**
 * Gets benchmark info from a session.
 */
expect fun nativeSessionGetBenchmarkInfo(session: SessionPtr): BenchmarkInfoPtr?

/**
 * Deletes a session.
 */
expect fun nativeSessionDelete(session: SessionPtr)

// ============================================================================
// Responses Functions
// ============================================================================

/**
 * Gets the number of response candidates.
 */
expect fun nativeResponsesGetNumCandidates(responses: ResponsesPtr): Int

/**
 * Gets the response text at the given index.
 */
expect fun nativeResponsesGetResponseTextAt(responses: ResponsesPtr, index: Int): String?

/**
 * Deletes responses.
 */
expect fun nativeResponsesDelete(responses: ResponsesPtr)

// ============================================================================
// Conversation Config Functions
// ============================================================================

/**
 * Creates a conversation config.
 *
 * @param engine The engine pointer.
 * @param sessionConfig The session config (or null for defaults).
 * @param systemMessageJson The system message in JSON format (or null).
 * @return Pointer to conversation config.
 */
expect fun nativeConversationConfigCreate(
    engine: EnginePtr,
    sessionConfig: SessionConfigPtr?,
    systemMessageJson: String? = null
): ConversationConfigPtr

/**
 * Deletes conversation config.
 */
expect fun nativeConversationConfigDelete(config: ConversationConfigPtr)

// ============================================================================
// Conversation Functions
// ============================================================================

/**
 * Creates a new LiteRT-LM conversation.
 *
 * @param engine The engine pointer.
 * @param config The conversation config.
 * @return Pointer to the conversation.
 */
expect fun nativeConversationCreate(
    engine: EnginePtr,
    config: ConversationConfigPtr
): ConversationPtr

/**
 * Deletes a LiteRT-LM conversation.
 */
expect fun nativeConversationDelete(conversation: ConversationPtr)

/**
 * Callback interface for async message sending.
 */
interface NativeMessageCallback {
    /**
     * Called when a message chunk is received.
     * @param messageJson The message chunk in JSON format.
     */
    fun onMessage(messageJson: String)
    
    /**
     * Called when the message stream is complete.
     */
    fun onDone()
    
    /**
     * Called when an error occurs.
     * @param errorCode The error code (platform-specific).
     * @param errorMessage The error message.
     */
    fun onError(errorCode: Int, errorMessage: String)
}

/**
 * Sends a message asynchronously with streaming response.
 *
 * @param conversation The conversation pointer.
 * @param messageJson The message in JSON format.
 * @param callback The callback to receive streaming responses.
 * @return 0 on success, error code on failure.
 */
expect fun nativeConversationSendMessageStream(
    conversation: ConversationPtr,
    messageJson: String,
    callback: NativeMessageCallback
): Int

/**
 * Sends a message synchronously.
 *
 * @param conversation The conversation pointer.
 * @param messageJson The message in JSON format.
 * @return Pointer to JSON response.
 */
expect fun nativeConversationSendMessage(
    conversation: ConversationPtr,
    messageJson: String
): JsonResponsePtr

/**
 * Cancels the ongoing conversation process.
 */
expect fun nativeConversationCancelProcess(conversation: ConversationPtr)

/**
 * Gets benchmark info from a conversation.
 */
expect fun nativeConversationGetBenchmarkInfo(conversation: ConversationPtr): BenchmarkInfoPtr?

// ============================================================================
// JSON Response Functions
// ============================================================================

/**
 * Gets the string content from a JSON response.
 */
expect fun nativeJsonResponseGetString(response: JsonResponsePtr): String?

/**
 * Deletes a JSON response.
 */
expect fun nativeJsonResponseDelete(response: JsonResponsePtr)

// ============================================================================
// Benchmark Info Functions
// ============================================================================

/**
 * Gets the number of prefill turns.
 */
expect fun nativeBenchmarkInfoGetNumPrefillTurns(benchmarkInfo: BenchmarkInfoPtr): Int

/**
 * Gets prefill tokens per second at the given turn index.
 */
expect fun nativeBenchmarkInfoGetPrefillTokensPerSecAt(benchmarkInfo: BenchmarkInfoPtr, index: Int): Double

/**
 * Gets the number of decode turns.
 */
expect fun nativeBenchmarkInfoGetNumDecodeTurns(benchmarkInfo: BenchmarkInfoPtr): Int

/**
 * Gets decode tokens per second at the given turn index.
 */
expect fun nativeBenchmarkInfoGetDecodeTokensPerSecAt(benchmarkInfo: BenchmarkInfoPtr, index: Int): Double

/**
 * Gets time to first token in seconds.
 */
expect fun nativeBenchmarkInfoGetTimeToFirstToken(benchmarkInfo: BenchmarkInfoPtr): Double

/**
 * Gets the last prefill token count (Android-specific, returns 0 on iOS).
 */
expect fun nativeBenchmarkInfoGetLastPrefillTokenCount(benchmarkInfo: BenchmarkInfoPtr): Int

/**
 * Gets the last decode token count (Android-specific, returns 0 on iOS).
 */
expect fun nativeBenchmarkInfoGetLastDecodeTokenCount(benchmarkInfo: BenchmarkInfoPtr): Int

/**
 * Deletes benchmark info.
 */
expect fun nativeBenchmarkInfoDelete(benchmarkInfo: BenchmarkInfoPtr)

// ============================================================================
// Logging Functions
// ============================================================================

/**
 * Sets the minimum log level.
 * @param level The log level (0=verbose, 1=debug, 2=info, 3=warning, 4=error, 5=fatal).
 */
expect fun nativeSetMinLogLevel(level: Int)
