package com.aayush.simpleai

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aayush.simpleai.util.DownloadState
import com.aayush.simpleai.util.SampleToolSet
import com.aayush.simpleai.util.createEngine
import com.aayush.simpleai.util.defaultConfig
import com.aayush.simpleai.util.downloadFile
import com.aayush.simpleai.util.sendMessageAsync
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val TAG = "MainViewModel"
private const val MODEL_FILE_NAME = "gemma-3n-E2B-it-int4.litertlm"
private const val MODEL_DOWNLOAD_URL = "https://pub-19ca34c7d9fa4b248a55bf92f72dced6.r2.dev/$MODEL_FILE_NAME"

private const val MAIN_SYSTEM_PROMPT_ASSET = "system_prompts/main_conversation.txt"
private const val HELPER_PROMPT_ASSET = "system_prompts/helper_conversation.txt"

data class MainDataState(
    val greeting: String = "loading",
    val downloadState: DownloadState = DownloadState.NotStarted
) {

    val downloadProgressSuffix: String
        get() = when (downloadState) {
            is DownloadState.Downloading -> String.format(
                locale = Locale.getDefault(),
                format = " %.1f%%",
                downloadState.progress * 100
            )
            is DownloadState.Completed -> ""
            else -> ""
        }
    fun toViewState(): MainViewState = MainViewState(
        greeting = "$greeting$downloadProgressSuffix",
        downloadState = downloadState
    )
}

data class MainViewState(
    val greeting: String = "",
    val downloadState: DownloadState = DownloadState.NotStarted
)

class MainViewModel : ViewModel() {

    private lateinit var engine: Engine
    private lateinit var conversation: Conversation

    private val persistentToolSet: List<SampleToolSet> = listOf(SampleToolSet())

    private val _dataState = MutableStateFlow(MainDataState())
    val viewState: StateFlow<MainViewState> = _dataState
        .map { it.toViewState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _dataState.value.toViewState()
        )

    private fun logWithTimestamp(message: String) {
        val timestamp = System.currentTimeMillis()
        Log.d(TAG, "[$timestamp] $message")
    }

    fun init(context: Context) {
        Engine.setNativeMinLogSeverity(LogSeverity.VERBOSE)
        viewModelScope.launch(context = Dispatchers.IO) {
            val initStartTime = System.currentTimeMillis()

            try {
                // Check if model already exists
                val modelFile = File(
                    context.getExternalFilesDir(null),
                    MODEL_FILE_NAME,
                )
                
                if (!modelFile.exists()) {
                    logWithTimestamp("Model not found, starting download")
                    _dataState.value = _dataState.value.copy(greeting = "Downloading model...")
                    downloadFile(
                        context = context,
                        fileUrl = MODEL_DOWNLOAD_URL,
                        outputFile = modelFile,
                        onProgressUpdate = { progress ->
                            _dataState.value = _dataState.value.copy(downloadState = progress)
                        }
                    )
                } else {
                    logWithTimestamp("Model file already exists at ${modelFile.absolutePath}, size: ${modelFile.length()} bytes")
                    _dataState.value = _dataState.value.copy(
                        downloadState = DownloadState.Completed
                    )
                }

                // Initialize engine with the downloaded model
                initializeEngine(context, modelFile)
                
                logWithTimestamp("Total initialization completed in ${System.currentTimeMillis() - initStartTime}ms")
                sendMessage("Hi what's the weather today? use search tools")
            } catch (e: Exception) {
                throw e
//                logWithTimestamp("Error: ${e.message}")
//                _dataState.value = _dataState.value.copy(
//                    greeting = "GError: ${e.message}",
//                    downloadState = DownloadState.Error(e.message ?: "Unknown error")
//                )
            }
        }
    }

    /**
     * Loads a system prompt from the assets folder.
     */
    private fun loadSystemPrompt(context: Context, assetPath: String): String {
        return try {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            logWithTimestamp("Failed to load system prompt from $assetPath: ${e.message}")
            ""
        }
    }

    private fun initializeEngine(context: Context, modelFile: File) {
        val backendsToTry = listOf(Backend.GPU, Backend.CPU)

        // Load system prompts from assets
        val mainSystemPrompt = loadSystemPrompt(context, MAIN_SYSTEM_PROMPT_ASSET)

        logWithTimestamp("Loaded main system prompt (${mainSystemPrompt.length} chars)")

        for (backend in backendsToTry) {
            try {
                engine = createEngine(context, modelFile, backend)

                // Create main conversation with system prompt
                val mainSystemMessage = if (mainSystemPrompt.isNotEmpty()) {
                    Message.of(listOf(Content.Text(mainSystemPrompt)))
                } else null
                
                val conversationConfig = ConversationConfig(
                    samplerConfig = defaultConfig,
                    tools = persistentToolSet,
                    systemMessage = mainSystemMessage
                )
                conversation = engine.createConversation(conversationConfig)

                return // Success, exit the loop
            } catch (e: Exception) {
                logWithTimestamp("Failed to initialize with backend $backend: ${e.message}")
                try {
                    engine.close()
                } catch (closeException: Exception) {
                    // Ignore
                }

                // If this was the last backend to try, throw the exception
                if (backend == backendsToTry.last()) {
                    throw e
                }
            }
        }

        throw IllegalStateException("Failed to initialize engine with any backend")
    }

    private fun sendMessage(prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val generateStartTime = System.currentTimeMillis()
            logWithTimestamp("Starting message processing for: $prompt")

            try {
                conversation.sendMessageAsync(
                    prompt = prompt,
                    onToken = { partialResponse ->
                        _dataState.value = _dataState.value.copy(greeting = partialResponse)
                    },
                    onDone = { fullResponse ->
                        _dataState.value = _dataState.value.copy(
                            greeting = fullResponse.ifEmpty { "hmm didnt work" }
                        )
                    },
                    onError = { throwable ->
                        _dataState.value = _dataState.value.copy(
                            greeting = "VError: ${throwable.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _dataState.value = _dataState.value.copy(
                    greeting = "BError: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            conversation.close()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            engine.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
