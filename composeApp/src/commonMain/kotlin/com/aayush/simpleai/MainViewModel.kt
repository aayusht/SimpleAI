package com.aayush.simpleai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aayush.simpleai.db.ChatHistory
import com.aayush.simpleai.db.ChatHistoryDao
import com.aayush.simpleai.llm.Backend
import com.aayush.simpleai.llm.Conversation
import com.aayush.simpleai.llm.ConversationConfig
import com.aayush.simpleai.llm.Engine
import com.aayush.simpleai.llm.EngineConfig
import com.aayush.simpleai.llm.Message
import com.aayush.simpleai.llm.Role
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.collections.get

internal typealias ChatMessage = Message

private data class MainDataState(
    val downloadState: DownloadState = DownloadState.LoadingFile,
    val isConversationReady: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val userMessageBeingGeneratedFor: Message? = null,
    val deviceStats: DeviceStats? = null,
    val activeChatId: Long? = null,
    val chatHistoryMap: Map<Long, ChatHistory> = emptyMap(),
) {

    val chatHistoryList: List<ChatHistory>
        get() = chatHistoryMap.values.toList()
}

class MainViewModel(
    private val downloadProvider: DownloadProvider,
    private val httpClient: HttpClient,
    private val backgroundDownloadService: BackgroundDownloadService,
    private val chatHistoryDao: ChatHistoryDao,
) : ViewModel() {
    
    private val deviceStatsProvider: DeviceStatsProvider = createDeviceStatsProvider()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
        set(value) {
            field?.close()
            field = value
            if (value != null) {
                observeConversation(value)
                observeCompletedMessagesForDb(value)
            }
        }
    
    private val _dataState = MutableStateFlow(MainDataState())
    private var completedMessagesObserverJob: Job? = null
    
    // Combine internal state with background download state and chat history
    val viewState: StateFlow<MainViewState> = _dataState.map { dataState ->
        
        MainViewState(
            downloadState = dataState.downloadState,
            messages = MainViewState.getMessageViewStates(
                messages = dataState.messages,
                dbMessages = dataState.chatHistoryMap[dataState.activeChatId]?.messages,
                userMessageBeingGeneratedFor = dataState.userMessageBeingGeneratedFor,
                isEngineReady = dataState.isConversationReady,
            ),
            isGenerating = dataState.userMessageBeingGeneratedFor != null,
            remainingStorage = dataState.deviceStats?.availableStorage,
            notEnoughMemory = dataState.deviceStats?.maxMemory?.let { actualBytes ->
                MainViewState.NotEnoughBytes
                    .from(actualBytes = actualBytes, neededBytes = REQUIRED_MEMORY_BYTES)
            },
            notEnoughStorage = dataState.deviceStats?.availableStorage?.let { actualBytes ->
                MainViewState.NotEnoughBytes
                    .from(actualBytes = actualBytes, neededBytes = REQUIRED_STORAGE_BYTES)
            },
            historyRows = dataState.chatHistoryList.map { chatHistory ->
                MainViewState.HistoryRowState.from(chatHistory, dataState.activeChatId)
            },
            isNewChat = dataState.activeChatId == null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainViewState(
            downloadState = DownloadState.LoadingFile,
            messages = emptyList(),
            isGenerating = false,
            remainingStorage = null,
            notEnoughStorage = null,
            notEnoughMemory = null,
            historyRows = emptyList(),
            isNewChat = true,
        )
    )

    init {
        checkModel()
        updateDeviceStats()
        observeBackgroundDownloadState()
        observeChatHistoryList()
    }

    private fun observeConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversation.history.collect { messages ->
                _dataState.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            conversation.isConversationReady.collect { isReady ->
                _dataState.update { it.copy(isConversationReady = isReady) }
            }
        }
    }

    /**
     * Observes completed messages from the conversation and streams them to Room DB.
     * Only saves after the first assistant message is sent (to avoid saving empty chats).
     */
    private fun observeCompletedMessagesForDb(conversation: Conversation) {
        completedMessagesObserverJob?.cancel()
        completedMessagesObserverJob = viewModelScope.launch(Dispatchers.IO) {
            conversation.completedMessagesHistory.collect { messages ->
                // Only save if there's at least one assistant message
                val hasAssistantMessage = messages.any { it.role == Role.ASSISTANT }
                if (messages.isNotEmpty() && hasAssistantMessage) {
                    saveMessagesToDb(messages)
                }
            }
        }
    }

    private suspend fun saveMessagesToDb(messages: List<Message>) {
        val currentChatId = _dataState.value.activeChatId
        if (currentChatId != null) {
            // Update existing chat
            val timestamp = messages.firstOrNull()?.timestamp ?: 0L
            chatHistoryDao.update(
                id = currentChatId,
                messages = ChatHistory.encodeMessages(messages),
                timestamp = timestamp
            )
        } else {
            // Create new chat
            val chatHistory = ChatHistory.from(messages)
            val newId = chatHistoryDao.insert(chatHistory)
            _dataState.update { it.copy(activeChatId = newId) }
        }
    }

    private fun observeChatHistoryList() {
        viewModelScope.launch {
            chatHistoryDao.getAll()
                .distinctUntilChanged()
                .collect { historyList ->
                    _dataState.update { state ->
                        state.copy(chatHistoryMap = historyList.associateBy { it.id })
                    }
                }
        }
    }

    /**
     * Restores a chat from history by its ID.
     */
    fun restoreChat(chatId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val chatHistory = chatHistoryDao.getById(chatId) ?: return@launch
            _dataState.update { it.copy(activeChatId = chatId) }
            conversation?.restoreHistory(chatHistory.messages)
        }
    }

    /**
     * Starts a new chat, clearing the current conversation.
     */
    fun startNewChat() {
        viewModelScope.launch(Dispatchers.IO) {
            _dataState.update { it.copy(activeChatId = null, messages = emptyList()) }
            engine?.createConversation(getConversationConfig())
        }
    }

    /**
     * Deletes a chat from history.
     */
    fun deleteChat(chatId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            chatHistoryDao.deleteById(chatId)
            // If we deleted the active chat, start a new one
            if (_dataState.value.activeChatId == chatId) {
                startNewChat()
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
        backgroundDownloadService.requestNotificationPermission()
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
            
            for (backend in backendsToTry) {
                try {
                    engine = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = backend.value
                        )
                    ).apply {
                        initialize()
                        createConversation(getConversationConfig())
                    }
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

            _dataState.update { it.copy(userMessageBeingGeneratedFor = Message.user(prompt)) }
            _dataState.first { it.isConversationReady }
            conversation?.sendMessageAsync(
                prompt = prompt,
                onToken = {},
                onDone = { _dataState.update { it.copy(userMessageBeingGeneratedFor = null) } },
                onError = { _dataState.update { it.copy(userMessageBeingGeneratedFor = null) } }
            )
        }
    }

    suspend fun getConversationConfig() = ConversationConfig(
        samplerConfig = SamplerConfig(
            topK = 40,
            topP = 0.95,
            temperature = 1.0,
        ),
        systemPrompt = Res.readBytes("files/system_prompt.md").decodeToString(),
        tools = listOf(ToolDefinition.WebSearchTool())
    )
    
    override fun onCleared() {
        super.onCleared()
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
    }
}
