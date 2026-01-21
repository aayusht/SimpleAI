@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.aayush.simpleai.util

import cnames.structs.LiteRtLmBenchmarkInfo
import cnames.structs.LiteRtLmConversation
import cnames.structs.LiteRtLmConversationConfig
import cnames.structs.LiteRtLmEngine
import cnames.structs.LiteRtLmEngineSettings
import cnames.structs.LiteRtLmJsonResponse
import cnames.structs.LiteRtLmResponses
import cnames.structs.LiteRtLmSession
import cnames.structs.LiteRtLmSessionConfig
import kotlinx.cinterop.*
import litert.lm.c.*
import platform.Foundation.NSLock

// ============================================================================
// Pointer Types - iOS implementations (CPointer wrappers)
// ============================================================================

actual class EngineSettingsPtr(val ptr: CPointer<LiteRtLmEngineSettings>)

actual class EnginePtr(val ptr: CPointer<LiteRtLmEngine>)

actual class SessionConfigPtr(val ptr: CPointer<LiteRtLmSessionConfig>)

actual class SessionPtr(val ptr: CPointer<LiteRtLmSession>)

actual class ConversationConfigPtr(val ptr: CPointer<LiteRtLmConversationConfig>)

actual class ConversationPtr(val ptr: CPointer<LiteRtLmConversation>)

actual class ResponsesPtr(val ptr: CPointer<LiteRtLmResponses>)

actual class JsonResponsePtr(val ptr: CPointer<LiteRtLmJsonResponse>)

actual class BenchmarkInfoPtr(val ptr: CPointer<LiteRtLmBenchmarkInfo>)

// ============================================================================
// Platform Lock
// ============================================================================

actual class PlatformLock {
    val lock = NSLock()

    actual inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
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
    val ptr = litert_lm_engine_settings_create(
        model_path = modelPath,
        backend_str = backend,
        vision_backend_str = visionBackend,
        audio_backend_str = audioBackend
    ) ?: throw IllegalStateException("Failed to create engine settings for model at: $modelPath")
    return EngineSettingsPtr(ptr)
}

actual fun nativeEngineSettingsSetMaxNumTokens(
    settings: EngineSettingsPtr,
    maxNumTokens: Int
) {
    litert_lm_engine_settings_set_max_num_tokens(settings.ptr, maxNumTokens)
}

actual fun nativeEngineSettingsSetCacheDir(
    settings: EngineSettingsPtr,
    cacheDir: String
) {
    litert_lm_engine_settings_set_cache_dir(settings.ptr, cacheDir)
}

actual fun nativeEngineSettingsEnableBenchmark(settings: EngineSettingsPtr) {
    litert_lm_engine_settings_enable_benchmark(settings.ptr)
}

actual fun nativeEngineSettingsDelete(settings: EngineSettingsPtr) {
    litert_lm_engine_settings_delete(settings.ptr)
}

// ============================================================================
// Engine Functions
// ============================================================================

actual fun nativeEngineCreate(settings: EngineSettingsPtr): EnginePtr {
    val ptr = litert_lm_engine_create(settings.ptr)
        ?: throw IllegalStateException("Failed to create LiteRT-LM engine")
    return EnginePtr(ptr)
}

actual fun nativeEngineDelete(engine: EnginePtr) {
    litert_lm_engine_delete(engine.ptr)
}

actual fun nativeEngineCreateSession(
    engine: EnginePtr,
    config: SessionConfigPtr?
): SessionPtr {
    val ptr = litert_lm_engine_create_session(engine.ptr, config?.ptr)
        ?: throw IllegalStateException("Failed to create session")
    return SessionPtr(ptr)
}

private fun <T> runWithAllocdInputs(
    session: SessionPtr,
    inputs: Array<NativeInputData>,
    block: (CArrayPointer<InputData>) -> T
): T {
    memScoped {
        val inputsArray = allocArray<InputData>(inputs.size)
        inputs.forEachIndexed { index, input ->
            inputsArray[index].apply {
                when {
                    input.text != null -> {
                        val bytes = input.text.encodeToByteArray()
                        type = InputDataType.kInputText
                        data = allocArrayOf(bytes)
                        size = bytes.size.toULong()
                    }
                    input.audioBytes != null -> {
                        type = InputDataType.kInputAudio
                        data = allocArrayOf(input.audioBytes)
                        size = input.audioBytes.size.toULong()
                    }
                    input.imageBytes != null -> {
                        type = InputDataType.kInputImage
                        data = allocArrayOf(input.imageBytes)
                        size = input.imageBytes.size.toULong()
                    }
                }
            }
        }

        return block(inputsArray)
    }
}

actual fun nativeSessionRunPrefill(
    session: SessionPtr,
    inputs: Array<NativeInputData>
) {
    runWithAllocdInputs(session, inputs) { inputsArray ->
        litert_lm_session_run_prefill(
            session.ptr,
            inputsArray,
            inputs.size.toULong()
        )
    }
}

actual fun nativeSessionRunDecode(
    session: SessionPtr
): ResponsesPtr {
    val ptr = litert_lm_session_run_decode(session.ptr)
        ?: throw IllegalStateException("Failed to run decode")
    return ResponsesPtr(ptr)
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
    val ptr = memScoped {
        val samplerParams = alloc<LiteRtLmSamplerParams>().apply {
            type = when (samplerType) {
                SamplerType.TYPE_UNSPECIFIED -> kTypeUnspecified
                SamplerType.GREEDY -> kGreedy
                SamplerType.TOP_K -> kTopK
                SamplerType.TOP_P -> kTopP
            }
            top_k = topK
            top_p = topP
            this.temperature = temperature
            this.seed = seed
        }
        litert_lm_session_config_create(samplerParams.ptr)
    } ?: throw IllegalStateException("Failed to create session config")
    return SessionConfigPtr(ptr)
}

actual fun nativeSessionConfigSetMaxOutputTokens(
    config: SessionConfigPtr,
    maxOutputTokens: Int
) {
    litert_lm_session_config_set_max_output_tokens(config.ptr, maxOutputTokens)
}

actual fun nativeSessionConfigDelete(config: SessionConfigPtr) {
    litert_lm_session_config_delete(config.ptr)
}

// ============================================================================
// Session Functions
// ============================================================================

actual fun nativeSessionGenerateContent(
    session: SessionPtr,
    inputs: Array<NativeInputData>
): ResponsesPtr {
    return runWithAllocdInputs(session, inputs) { inputsArray ->
        val ptr = litert_lm_session_generate_content(
            session.ptr,
            inputsArray,
            inputs.size.toULong()
        ) ?: throw IllegalStateException("Failed to generate content")
        ResponsesPtr(ptr)
    }
}

// Store active session callbacks to prevent GC
private val activeSessionCallbacks = mutableMapOf<Long, StableRef<SessionStreamContext>>()
private var sessionCallbackIdCounter = 0L

private class SessionStreamContext(
    val callback: NativeStreamCallback
)

actual fun nativeSessionGenerateContentStream(
    session: SessionPtr,
    inputs: Array<NativeInputData>,
    callback: NativeStreamCallback
): Int {
    return runWithAllocdInputs(session, inputs) { inputsArray ->
        
        val context = SessionStreamContext(callback)
        val contextRef = StableRef.create(context)
        val callbackId = ++sessionCallbackIdCounter
        activeSessionCallbacks[callbackId] = contextRef
        
        val result = litert_lm_session_generate_content_stream(
            session = session.ptr,
            inputs = inputsArray,
            num_inputs = inputs.size.toULong(),
            callback = staticCFunction { callbackData, chunk, isFinal, errorMsg ->
                val ctx = callbackData?.asStableRef<SessionStreamContext>()?.get() ?: return@staticCFunction
                
                if (errorMsg != null) {
                    ctx.callback.onError(errorMsg.toKString())
                    return@staticCFunction
                }
                
                if (chunk != null) {
                    ctx.callback.onChunk(chunk.toKString())
                }
                
                if (isFinal) {
                    ctx.callback.onDone()
                }
            },
            callback_data = contextRef.asCPointer()
        )
        
        if (result != 0) {
            contextRef.dispose()
            activeSessionCallbacks.remove(callbackId)
        }
        
        result
    }
}

actual fun nativeSessionGetBenchmarkInfo(session: SessionPtr): BenchmarkInfoPtr? {
    val ptr = litert_lm_session_get_benchmark_info(session.ptr) ?: return null
    return BenchmarkInfoPtr(ptr)
}

actual fun nativeSessionDelete(session: SessionPtr) {
    litert_lm_session_delete(session.ptr)
}

// ============================================================================
// Responses Functions
// ============================================================================

actual fun nativeResponsesGetNumCandidates(responses: ResponsesPtr): Int {
    return litert_lm_responses_get_num_candidates(responses.ptr)
}

actual fun nativeResponsesGetResponseTextAt(responses: ResponsesPtr, index: Int): String? {
    val ptr = litert_lm_responses_get_response_text_at(responses.ptr, index) ?: return null
    return ptr.toKString()
}

actual fun nativeResponsesDelete(responses: ResponsesPtr) {
    litert_lm_responses_delete(responses.ptr)
}

// ============================================================================
// Conversation Config Functions
// ============================================================================

actual fun nativeConversationConfigCreate(
    engine: EnginePtr,
    sessionConfig: SessionConfigPtr?,
    systemMessageJson: String?
): ConversationConfigPtr {
    val ptr = litert_lm_conversation_config_create(
        engine = engine.ptr,
        session_config = sessionConfig?.ptr,
        system_message_json = systemMessageJson
    ) ?: throw IllegalStateException("Failed to create conversation config")
    return ConversationConfigPtr(ptr)
}

actual fun nativeConversationConfigDelete(config: ConversationConfigPtr) {
    litert_lm_conversation_config_delete(config.ptr)
}

// ============================================================================
// Conversation Functions
// ============================================================================

actual fun nativeConversationCreate(
    engine: EnginePtr,
    config: ConversationConfigPtr
): ConversationPtr {
    val ptr = litert_lm_conversation_create(engine.ptr, config.ptr)
        ?: throw IllegalStateException("Failed to create conversation")
    return ConversationPtr(ptr)
}

actual fun nativeConversationDelete(conversation: ConversationPtr) {
    // Clean up any active callbacks
    litert_lm_conversation_delete(conversation.ptr)
}

// Store active conversation callbacks to prevent GC
private val activeConversationCallbacks = mutableMapOf<Long, StableRef<ConversationStreamContext>>()
private var conversationCallbackIdCounter = 0L

private class ConversationStreamContext(
    val callback: NativeMessageCallback
)

actual fun nativeConversationSendMessageStream(
    conversation: ConversationPtr,
    messageJson: String,
    callback: NativeMessageCallback
): Int {
    val context = ConversationStreamContext(callback)
    val contextRef = StableRef.create(context)
    val callbackId = ++conversationCallbackIdCounter
    activeConversationCallbacks[callbackId] = contextRef
    
    val result = litert_lm_conversation_send_message_stream(
        conversation = conversation.ptr,
        message_json = messageJson,
        callback = staticCFunction { callbackData, chunk, isFinal, errorMsg ->
            val ctx = callbackData?.asStableRef<ConversationStreamContext>()?.get() ?: return@staticCFunction
            
            if (errorMsg != null) {
                ctx.callback.onError(-1, errorMsg.toKString())
                return@staticCFunction
            }
            
            if (chunk != null) {
                ctx.callback.onMessage(chunk.toKString())
            }
            
            if (isFinal) {
                ctx.callback.onDone()
            }
        },
        callback_data = contextRef.asCPointer()
    )
    
    if (result != 0) {
        contextRef.dispose()
        activeConversationCallbacks.remove(callbackId)
    }
    
    return result
}

actual fun nativeConversationSendMessage(
    conversation: ConversationPtr,
    messageJson: String
): JsonResponsePtr {
    val ptr = litert_lm_conversation_send_message(conversation.ptr, messageJson)
        ?: throw IllegalStateException("Failed to send message")
    return JsonResponsePtr(ptr)
}

actual fun nativeConversationCancelProcess(conversation: ConversationPtr) {
    litert_lm_conversation_cancel_process(conversation.ptr)
}

actual fun nativeConversationGetBenchmarkInfo(conversation: ConversationPtr): BenchmarkInfoPtr? {
    val ptr = litert_lm_conversation_get_benchmark_info(conversation.ptr) ?: return null
    return BenchmarkInfoPtr(ptr)
}

// ============================================================================
// JSON Response Functions
// ============================================================================

actual fun nativeJsonResponseGetString(response: JsonResponsePtr): String? {
    val ptr = litert_lm_json_response_get_string(response.ptr) ?: return null
    return ptr.toKString()
}

actual fun nativeJsonResponseDelete(response: JsonResponsePtr) {
    litert_lm_json_response_delete(response.ptr)
}

// ============================================================================
// Benchmark Info Functions
// ============================================================================

actual fun nativeBenchmarkInfoGetNumPrefillTurns(benchmarkInfo: BenchmarkInfoPtr): Int {
    return litert_lm_benchmark_info_get_num_prefill_turns(benchmarkInfo.ptr)
}

actual fun nativeBenchmarkInfoGetPrefillTokensPerSecAt(benchmarkInfo: BenchmarkInfoPtr, index: Int): Double {
    return litert_lm_benchmark_info_get_prefill_tokens_per_sec_at(benchmarkInfo.ptr, index)
}

actual fun nativeBenchmarkInfoGetNumDecodeTurns(benchmarkInfo: BenchmarkInfoPtr): Int {
    return litert_lm_benchmark_info_get_num_decode_turns(benchmarkInfo.ptr)
}

actual fun nativeBenchmarkInfoGetDecodeTokensPerSecAt(benchmarkInfo: BenchmarkInfoPtr, index: Int): Double {
    return litert_lm_benchmark_info_get_decode_tokens_per_sec_at(benchmarkInfo.ptr, index)
}

actual fun nativeBenchmarkInfoGetTimeToFirstToken(benchmarkInfo: BenchmarkInfoPtr): Double {
    return litert_lm_benchmark_info_get_time_to_first_token(benchmarkInfo.ptr)
}

actual fun nativeBenchmarkInfoGetLastPrefillTokenCount(benchmarkInfo: BenchmarkInfoPtr): Int {
    // Not directly available on iOS - return 0
    // iOS exposes per-turn metrics instead
    return 0
}

actual fun nativeBenchmarkInfoGetLastDecodeTokenCount(benchmarkInfo: BenchmarkInfoPtr): Int {
    // Not directly available on iOS - return 0
    // iOS exposes per-turn metrics instead
    return 0
}

actual fun nativeBenchmarkInfoDelete(benchmarkInfo: BenchmarkInfoPtr) {
    litert_lm_benchmark_info_delete(benchmarkInfo.ptr)
}

// ============================================================================
// Logging Functions
// ============================================================================

actual fun nativeSetMinLogLevel(level: Int) {
    litert_lm_set_min_log_level(level)
}
