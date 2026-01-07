package com.aayush.simpleai.util

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


internal val defaultConfig: SamplerConfig = SamplerConfig(
    topK = 40,
    topP = 0.95,
    temperature = 0.8,
)

class SampleToolSet {
    @Tool(description = "Search for information")
    fun sampleSearch(
        @ToolParam(description = "Search query, eg \"What is the current weather?\".") query: String,
    ): String {
        // In a real application, you would call a search API here
        return "It's cold and rainy today, with a high of 20F and 1 inch of rain predicted today with a 90% chance"
    }
}

fun createEngine(
    context: Context,
    modelFile: File,
    backend: Backend = Backend.GPU,
): Engine {
    // cacheDir is set to null when model is in external storage (not /data/local/tmp)
    // This matches the gallery app behavior and lets the engine handle caching internally
    val modelPath = modelFile.absolutePath
    val engineConfig = EngineConfig(
        modelPath = modelPath,
        backend = Backend.GPU,
        maxNumTokens = 4096,
        cacheDir = context.getExternalFilesDir(null)?.absolutePath
            ?.takeIf { modelPath.startsWith("/data/local/tmp") }
    )

    return Engine(engineConfig).apply { initialize() }
}

suspend fun Conversation.sendMessageAsync(
    prompt: String,
    onToken: (String) -> Unit,
    onDone: (String) -> Unit = {},
    onError: (Throwable) -> Unit = {},
) {
    suspendCancellableCoroutine { continuation ->
        val response = StringBuilder()

        sendMessageAsync(
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