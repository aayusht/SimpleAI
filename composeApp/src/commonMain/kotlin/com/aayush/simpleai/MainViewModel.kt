package com.aayush.simpleai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aayush.simpleai.llm.Backend
import com.aayush.simpleai.llm.Conversation
import com.aayush.simpleai.llm.ConversationConfig
import com.aayush.simpleai.llm.Engine
import com.aayush.simpleai.llm.EngineConfig
import com.aayush.simpleai.llm.Role
import com.aayush.simpleai.llm.SamplerConfig
import com.aayush.simpleai.llm.ToolDefinition
import com.aayush.simpleai.util.DownloadState
import com.aayush.simpleai.util.DownloadProvider
import com.aayush.simpleai.util.E2B_MODEL_FILE_NAME
import com.aayush.simpleai.util.E4B_MODEL_FILE_NAME
import com.aayush.simpleai.util.MODEL_DOWNLOAD_URL
import com.aayush.simpleai.util.downloadFile
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.jetbrains.compose.resources.getString
import simpleai.composeapp.generated.resources.*
import kotlin.time.Clock

internal data class ChatMessage(
    val id: Long,
    val role: Role,
    val content: String,
    val isLoading: Boolean = false
)

private data class MainDataState(
    val downloadState: DownloadState = DownloadState.NotStarted,
    val isEngineReady: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val generatingMessage: ChatMessage? = null,
) {
    fun copyWithNewMessage(content: String, isFinal: Boolean = false): MainDataState {
        val generatingMessage = generatingMessage ?: return this
        val newMessage = generatingMessage.copy(content = content, isLoading = content.isBlank())
        if (isFinal) {
            return copy(generatingMessage = null, messages = messages + newMessage)
        }
        return copy(generatingMessage = newMessage)
    }
}

class MainViewModel(
    private val downloadProvider: DownloadProvider,
    private val httpClient: HttpClient,
) : ViewModel() {
    
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    
    private val _dataState = MutableStateFlow(MainDataState())
    val viewState: StateFlow<MainViewState> = _dataState
        .map { dataState ->
            MainViewState(
                downloadState = dataState.downloadState,
                isEngineReady = dataState.isEngineReady,
                messages = (dataState.messages + dataState.generatingMessage)
                    .filterNotNull()
                    .map { dataStateMessage ->
                        MainViewState.Message(
                            id = dataStateMessage.id,
                            role = dataStateMessage.role,
                            content = dataStateMessage.content,
                            isLoading = dataStateMessage.isLoading,
                        )
                    },
                isGenerating = dataState.generatingMessage != null,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainViewState(
                downloadState = DownloadState.NotStarted,
                isEngineReady = false,
                messages = emptyList(),
                isGenerating = false
            )
        )

    init {
        checkAndDownloadModel()
    }

    private fun checkAndDownloadModel() {
        viewModelScope.launch(context = Dispatchers.IO) {
            try {
                val downloadFolder = downloadProvider.getDownloadFolder()
                val currModel = E4B_MODEL_FILE_NAME
                val modelToDelete = E2B_MODEL_FILE_NAME
                val oldModelPath = "$downloadFolder/$modelToDelete".toPath()
                val newModelPath = "$downloadFolder/$currModel".toPath()
                val fileSystem = FileSystem.SYSTEM

                if (fileSystem.exists(oldModelPath)) {
                    _dataState.update { it.copy(downloadState = DownloadState.NotStarted) }
                    fileSystem.delete(oldModelPath)
                }
                if (fileSystem.exists(newModelPath)) {
                    _dataState.update { it.copy(downloadState = DownloadState.Completed) }
                    initializeEngine(newModelPath.toString())
                } else {
                    _dataState.update { it.copy(downloadState = DownloadState.NotStarted) }
                    
                    val downloadUrl = "$MODEL_DOWNLOAD_URL/$currModel"
                    downloadFile(
                        client = httpClient,
                        fileUrl = downloadUrl,
                        outputPath = newModelPath,
                        onProgressUpdate = { downloadState ->
                            _dataState.update { it.copy(downloadState = downloadState) }
                        }
                    )
                    
                    if (_dataState.value.downloadState is DownloadState.Completed) {
                        initializeEngine(newModelPath.toString())
                    }
                }
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Unknown error"
                _dataState.update { it.copy(downloadState = DownloadState.Error(errorMessage)) }
            }
        }
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
        if (prompt.isBlank() || _dataState.value.generatingMessage != null) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val id = Clock.System.now().toEpochMilliseconds()
            val userMessage = ChatMessage(id = id, role = Role.USER, content = prompt)
            val generatingMessage = ChatMessage(
                id = id,
                role = Role.ASSISTANT,
                content = "",
                isLoading = true,
            )
            
            _dataState.update { state ->
                state.copy(
                    messages = state.messages + userMessage,
                    generatingMessage = generatingMessage,
                )
            }

            _dataState.first { it.isEngineReady }
            try {
                conversation?.sendMessageAsync(
                    prompt = prompt,
                    onToken = { partialResponse ->
                        _dataState.update { it.copyWithNewMessage(content = partialResponse) }
                    },
                    onDone = { fullResponse ->
                        _dataState.update { state ->
                            state.copyWithNewMessage(
                                content = fullResponse.ifEmpty { "No response" },
                                isFinal = true,
                            )
                        }
                    },
                    onError = { e ->
                        _dataState.update { state ->
                            state.copyWithNewMessage(
                                content = "Error: ${e.message}",
                                isFinal = true,
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _dataState.update { state ->
                    state.copyWithNewMessage(content = "Error: ${e.message}", isFinal = true)
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
    }
}
