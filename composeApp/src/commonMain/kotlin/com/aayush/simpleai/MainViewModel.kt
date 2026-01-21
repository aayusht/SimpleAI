package com.aayush.simpleai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aayush.simpleai.llm.Backend
import com.aayush.simpleai.llm.Conversation
import com.aayush.simpleai.llm.ConversationConfig
import com.aayush.simpleai.llm.Engine
import com.aayush.simpleai.llm.EngineConfig
import com.aayush.simpleai.llm.Message
import com.aayush.simpleai.llm.SamplerConfig
import com.aayush.simpleai.llm.ToolDefinition
import com.aayush.simpleai.util.DownloadState
import com.aayush.simpleai.util.DownloadProvider
import com.aayush.simpleai.util.E2B_MODEL_FILE_NAME
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.jetbrains.compose.resources.getString
import simpleai.composeapp.generated.resources.*

data class MainDataState(
    val greeting: String = "",
    val downloadState: DownloadState = DownloadState.NotStarted
) {

    fun toViewState(): MainViewState = MainViewState(
        greeting = greeting,
        downloadState = downloadState
    )
}

data class MainViewState(
    val greeting: String,
    val downloadState: DownloadState,
)

class MainViewModel(
    private val downloadProvider: DownloadProvider,
    private val httpClient: HttpClient,
) : ViewModel() {
    
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    
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
        viewModelScope.launch {
            _dataState.update { it.copy(greeting = getString(Res.string.loading)) }
        }
    }

    private fun checkAndDownloadModel() {
        viewModelScope.launch(context = Dispatchers.IO) {
            try {
                val downloadFolder = downloadProvider.getDownloadFolder()
                val modelPath = "$downloadFolder/$E2B_MODEL_FILE_NAME".toPath()
                val fileSystem = FileSystem.SYSTEM

                if (fileSystem.exists(modelPath)) {
                    _dataState.value = _dataState.value.copy(
                        greeting = getString(Res.string.model_found),
                        downloadState = DownloadState.Completed
                    )
                    // Initialize engine with the existing model
                    initializeEngine(modelPath.toString())
                } else {
                    _dataState.value = _dataState.value.copy(
                        greeting = getString(Res.string.downloading_model),
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
                                greeting = downloadState.getMessage()
                            )
                        }
                    )
                    
                    // After download completes, initialize engine
                    if (_dataState.value.downloadState is DownloadState.Completed) {
                        initializeEngine(modelPath.toString())
                    }
                }
            } catch (e: Exception) {
                val errorMessage = e.messageOrUnknown()
                _dataState.value = _dataState.value.copy(
                    greeting = getString(Res.string.error_prefix, errorMessage),
                    downloadState = DownloadState.Error(errorMessage)
                )
            }
        }
    }
    
    private fun initializeEngine(modelPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backendsToTry = listOf(Backend.GPU, Backend.CPU)
            
            // Load system prompt from file
            val systemPrompt = Res.readBytes("files/system_prompt.md").decodeToString()
            
            for (backend in backendsToTry) {
                try {
                    _dataState.value = _dataState.value.copy(
                        greeting = getString(Res.string.initializing_engine, backend.name)
                    )
                    
                    engine = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = backend.value
                        )
                    ).apply { initialize() }
                    
                    // Create conversation with config including system prompt
                    val conversationConfig = ConversationConfig(
                        samplerConfig = SamplerConfig(
                            topK = 40,
                            topP = 0.95,
                            temperature = 0.8
                        ),
                        systemPrompt = systemPrompt,
                        tools = listOf(ToolDefinition.SampleWeatherSearchTool()),
                        prefillMessages = listOf(
                            Message.user(
                                "Please address me as big john for the rest of the conversation."
                            ),
                            Message.assistant(text = "You got it, big john.")
                        )
                    )
                    
                    conversation = engine?.createConversation(conversationConfig)

                    _dataState.value = _dataState.value.copy(
                        greeting = getString(Res.string.ready_engine, backend.name)
                    )
                    
                    // Send a test message
                    sendMessage(getString(Res.string.test_prompt))
                    
                    return@launch // Success, exit the loop
                } catch (e: Exception) {
                    try {
                        engine?.close()
                    } catch (closeException: Exception) {
                        // Ignore
                    }
                    
                    // If this was the last backend to try, throw the exception
                    if (backend == backendsToTry.last()) {
                        val errorMessage = e.messageOrUnknown()
                        _dataState.value = _dataState.value.copy(
                            greeting = getString(Res.string.error_prefix, errorMessage),
                            downloadState = DownloadState.Error(errorMessage)
                        )
                        return@launch
                    }
                }
            }
        }
    }
    
    private suspend fun sendMessage(prompt: String) {
        try {
            conversation?.sendMessageAsync(
                prompt = prompt,
                onToken = { partialResponse ->
                    _dataState.value = _dataState.value.copy(greeting = partialResponse)
                },
                onDone = { fullResponse ->
                    if (fullResponse.isEmpty()) {
                        viewModelScope.launch {
                            _dataState.value = _dataState.value.copy(
                                greeting = getString(Res.string.no_response)
                            )
                        }
                    } else {
                        _dataState.value = _dataState.value.copy(greeting = fullResponse)
                    }
                },
                onError = { throwable ->
                    viewModelScope.launch {
                        _dataState.value = _dataState.value.copy(
                            greeting = getString(Res.string.error_prefix, throwable.messageOrUnknown())
                        )
                    }
                }
            )
        } catch (e: Exception) {
            _dataState.value = _dataState.value.copy(
                greeting = getString(Res.string.error_prefix, e.messageOrUnknown())
            )
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

    private suspend fun Throwable.messageOrUnknown(): String =
        message ?: getString(Res.string.unknown_error)
}