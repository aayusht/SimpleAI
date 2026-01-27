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
import com.aayush.simpleai.util.BackgroundDownloadService
import com.aayush.simpleai.util.DOWNLOAD_URL
import com.aayush.simpleai.util.DownloadState
import com.aayush.simpleai.util.DownloadProvider
import com.aayush.simpleai.util.E4B_MODEL_FILE_NAME
import com.aayush.simpleai.util.EXPECTED_MODEL_SIZE_BYTES
import com.aayush.simpleai.util.DeviceStats
import com.aayush.simpleai.util.DeviceStatsProvider
import com.aayush.simpleai.util.REQUIRED_MEMORY_BYTES
import com.aayush.simpleai.util.REQUIRED_STORAGE_BYTES
import com.aayush.simpleai.util.createDeviceStatsProvider
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import simpleai.composeapp.generated.resources.*
import kotlin.time.Clock

internal typealias ChatMessage = Message

private data class MainDataState(
    val downloadState: DownloadState = DownloadState.LoadingFile,
    val isEngineReady: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val deviceStats: DeviceStats? = null,
)

class MainViewModel(
    private val downloadProvider: DownloadProvider,
    private val httpClient: HttpClient,
    private val backgroundDownloadService: BackgroundDownloadService,
) : ViewModel() {
    
    private val deviceStatsProvider: DeviceStatsProvider = createDeviceStatsProvider()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
        set(value) {
            field?.close()
            field = value
            if (value != null) {
                observeConversationMessages(value)
            }
        }
    
    private val _dataState = MutableStateFlow(MainDataState())
    
    // Combine internal state with background download state
    val viewState: StateFlow<MainViewState> = combine(
        _dataState,
        backgroundDownloadService.observeDownloadState()
    ) { dataState, downloadState ->
        // Use internal download state if set, otherwise use background service state <- AI slop
        val effectiveDownloadState = if (dataState.downloadState != DownloadState.LoadingFile) {
            dataState.downloadState
        } else {
            downloadState
        }
        
        MainViewState(
            downloadState = effectiveDownloadState,
            isEngineReady = dataState.isEngineReady,
            messages = MainViewState.getMessageViewStates(dataState.messages),
            isGenerating = dataState.isGenerating,
            remainingStorage = dataState.deviceStats?.availableStorage,
            notEnoughMemory = dataState.deviceStats?.maxMemory?.let { actualBytes ->
                MainViewState.NotEnoughBytes
                    .from(actualBytes = actualBytes, neededBytes = REQUIRED_MEMORY_BYTES)
            },
            notEnoughStorage = dataState.deviceStats?.availableStorage?.let { actualBytes ->
                MainViewState.NotEnoughBytes
                    .from(actualBytes = actualBytes, neededBytes = REQUIRED_STORAGE_BYTES)
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainViewState(
            downloadState = DownloadState.NotStarted,
            isEngineReady = false,
            messages = emptyList(),
            isGenerating = false,
            remainingStorage = null,
            notEnoughStorage = null,
            notEnoughMemory = null,
        )
    )

    init {
        checkModel()
        updateDeviceStats()
        observeBackgroundDownloadState()
    }

    private fun observeConversationMessages(conversation: Conversation) {
        viewModelScope.launch {
            conversation.history.collect { messages ->
                _dataState.update { it.copy(messages = messages) }
            }
        }
    }
    
    private fun observeBackgroundDownloadState() {
        viewModelScope.launch {
            backgroundDownloadService.observeDownloadState()
                .collect { downloadState ->
                    // Update internal state to reflect background service state
                    _dataState.update { it.copy(downloadState = downloadState) }
                }
        }

        viewModelScope.launch {
            _dataState.map { it.downloadState }
                .distinctUntilChanged()
                .firstOrNull { it is DownloadState.Completed }
                ?.let { initializeEngine(getModelFilePath().toString()) }
        }
    }

    private fun getModelFilePath(): Path {
        val downloadFolder = downloadProvider.getDownloadFolder()
        return "$downloadFolder/$E4B_MODEL_FILE_NAME".toPath()
    }

    private fun checkModel() {
        viewModelScope.launch(context = Dispatchers.IO) {
            try {
                val modelPath = getModelFilePath()
                when {
                    // Check if complete model exists
                    FileSystem.SYSTEM.exists(modelPath) -> {
                        _dataState.update { it.copy(downloadState = DownloadState.Completed) }
                    }
                    // Check for partial download from previous session
                    backgroundDownloadService.checkForPartialDownload(
                        outputPath = modelPath.toString(),
                        expectedTotalBytes = EXPECTED_MODEL_SIZE_BYTES
                    ) -> {
                        // State is updated by checkForPartialDownload
                    }
                    // No download in progress
                    else -> {
                        _dataState.update { it.copy(downloadState = DownloadState.NotStarted) }
                    }
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error"
                _dataState.update { it.copy(downloadState = DownloadState.Error(errorMessage)) }
            }
        }
    }

    private fun updateDeviceStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = deviceStatsProvider.getDeviceStats()
            _dataState.update { it.copy(deviceStats = stats) }
        }
    }

    fun downloadModel() {
        backgroundDownloadService.startDownload(
            url = DOWNLOAD_URL,
            outputPath = getModelFilePath().toString()
        )
    }
    
    fun cancelDownload() {
        backgroundDownloadService.cancelDownload()
    }
    
    private fun initializeEngine(modelPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val backendsToTry = listOf(Backend.GPU, Backend.CPU)
            val systemPrompt = Res.readBytes("files/system_prompt.md").decodeToString()
            
            for (backend in backendsToTry) {
                try {
                    engine = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = backend.value
                        )
                    ).apply { initialize() }
                    
                    val conversationConfig = ConversationConfig(
                        samplerConfig = SamplerConfig(
                            topK = 40,
                            topP = 0.95,
                            temperature = 1.0,
                        ),
                        systemPrompt = systemPrompt,
                        tools = listOf(ToolDefinition.WebSearchTool())
                    )
                    
                    conversation = engine?.createConversation(conversationConfig)
                    _dataState.update { it.copy(isEngineReady = true) }
                    return@launch
                } catch (e: Exception) {
                    try {
                        engine?.close()
                    } catch (_: Exception) {}
                    
                    if (backend == backendsToTry.last()) {
                        val errorMessage = e.message ?: "Unknown error"
                        _dataState.update { it.copy(downloadState = DownloadState.Error(errorMessage)) }
                        return@launch
                    }
                }
            }
        }
    }
    
    fun sendMessage(prompt: String) {
        if (prompt.isBlank()) return
        
        viewModelScope.launch(Dispatchers.IO) {

            _dataState.update { it.copy(isGenerating = true) }
            _dataState.first { it.isEngineReady }
            conversation?.sendMessageAsync(
                prompt = prompt,
                onToken = {},
                onDone = { _dataState.update { it.copy(isGenerating = false) } },
                onError = { _dataState.update { it.copy(isGenerating = false) } }
            )
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
    }
}
