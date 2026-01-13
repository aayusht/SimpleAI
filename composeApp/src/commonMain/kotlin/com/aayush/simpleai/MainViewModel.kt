package com.aayush.simpleai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aayush.simpleai.util.DownloadState
import com.aayush.simpleai.util.DownloadProvider
import com.aayush.simpleai.util.E2B_MODEL_FILE_NAME
import com.aayush.simpleai.util.LlmBackend
import com.aayush.simpleai.util.LlmConversation
import com.aayush.simpleai.util.LlmConversationConfig
import com.aayush.simpleai.util.LlmEngine
import com.aayush.simpleai.util.LlmEngineProvider
import com.aayush.simpleai.util.LlmSamplerConfig
import com.aayush.simpleai.util.MODEL_DOWNLOAD_URL
import com.aayush.simpleai.util.downloadFile
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import simpleai.composeapp.generated.resources.Res

data class MainDataState(
    val greeting: String = "Loading...",
    val downloadState: DownloadState = DownloadState.NotStarted
) {

    val downloadProgressSuffix: String
        get() = when (downloadState) {
            is DownloadState.Downloading -> {
                val progressPercent = (downloadState.progress * 100).toInt()
                " $progressPercent%"
            }
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

class MainViewModel(
    private val downloadProvider: DownloadProvider,
    private val httpClient: HttpClient,
    private val llmEngineProvider: LlmEngineProvider
) : ViewModel() {
    
    private var engine: LlmEngine? = null
    private var conversation: LlmConversation? = null
    
    private val _dataState = MutableStateFlow(MainDataState())
    val viewState: StateFlow<MainViewState> = _dataState
        .map { it.toViewState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _dataState.value.toViewState()
        )

    init {
        checkAndDownloadModel()
    }

    private fun checkAndDownloadModel() {
        viewModelScope.launch(context = Dispatchers.IO) {
            try {
                val downloadFolder = downloadProvider.getDownloadFolder()
                val modelPath = "$downloadFolder/$E2B_MODEL_FILE_NAME".toPath()
                val fileSystem = FileSystem.SYSTEM

                if (fileSystem.exists(modelPath)) {
                    _dataState.value = _dataState.value.copy(
                        greeting = "Model file found",
                        downloadState = DownloadState.Completed
                    )
                    // Initialize engine with the existing model
                    initializeEngine(modelPath.toString())
                } else {
                    _dataState.value = _dataState.value.copy(
                        greeting = "Downloading model...",
                        downloadState = DownloadState.NotStarted
                    )
                    
                    val downloadUrl = "$MODEL_DOWNLOAD_URL/$E2B_MODEL_FILE_NAME"
                    downloadFile(
                        client = httpClient,
                        fileUrl = downloadUrl,
                        outputPath = modelPath,
                        onProgressUpdate = { downloadState ->
                            _dataState.value = _dataState.value.copy(
                                downloadState = downloadState,
                                greeting = when (downloadState) {
                                    is DownloadState.Downloading -> "Downloading model..."
                                    is DownloadState.Completed -> "Download completed!"
                                    is DownloadState.Error -> "Download error: ${downloadState.message}"
                                    else -> "Preparing download..."
                                }
                            )
                        }
                    )
                    
                    // After download completes, initialize engine
                    if (_dataState.value.downloadState is DownloadState.Completed) {
                        initializeEngine(modelPath.toString())
                    }
                }
            } catch (e: Exception) {
                _dataState.value = _dataState.value.copy(
                    greeting = "Error: ${e.message}",
                    downloadState = DownloadState.Error(e.message ?: "Unknown error")
                )
            }
        }
    }
    
    private fun initializeEngine(modelPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backendsToTry = listOf(LlmBackend.GPU, LlmBackend.CPU)
            
            // Load system prompt from file
            val systemPrompt = Res.readBytes("files/system_prompt.md").decodeToString()
            
            for (backend in backendsToTry) {
                try {
                    _dataState.value = _dataState.value.copy(
                        greeting = "Initializing engine with $backend..."
                    )
                    
                    engine = llmEngineProvider.createEngine(modelPath, backend)
                    engine?.initialize()
                    
                    // Create conversation with config including system prompt
                    val conversationConfig = LlmConversationConfig(
                        samplerConfig = LlmSamplerConfig(
                            topK = 40,
                            topP = 0.95,
                            temperature = 0.8
                        ),
                        systemPrompt = systemPrompt
                    )
                    
                    conversation = engine?.createConversation(conversationConfig)
                    
                    _dataState.value = _dataState.value.copy(
                        greeting = "Ready! Engine initialized with $backend"
                    )
                    
                    // Send a test message
                    sendMessage("Hi what's the weather today in San Francisco?")
                    
                    return@launch // Success, exit the loop
                } catch (e: Exception) {
                    try {
                        engine?.close()
                    } catch (closeException: Exception) {
                        // Ignore
                    }
                    
                    // If this was the last backend to try, throw the exception
                    if (backend == backendsToTry.last()) {
                        _dataState.value = _dataState.value.copy(
                            greeting = "Failed to initialize: ${e.message}",
                            downloadState = DownloadState.Error(e.message ?: "Unknown error")
                        )
                        return@launch
                    }
                }
            }
        }
    }
    
    private fun sendMessage(prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                conversation?.sendMessageAsync(
                    prompt = prompt,
                    onToken = { partialResponse ->
                        _dataState.value = _dataState.value.copy(greeting = partialResponse)
                    },
                    onDone = { fullResponse ->
                        _dataState.value = _dataState.value.copy(
                            greeting = fullResponse.ifEmpty { "No response" }
                        )
                    },
                    onError = { throwable ->
                        _dataState.value = _dataState.value.copy(
                            greeting = "Error: ${throwable.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _dataState.value = _dataState.value.copy(
                    greeting = "Error: ${e.message}"
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        try {
            conversation?.close()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            engine?.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}