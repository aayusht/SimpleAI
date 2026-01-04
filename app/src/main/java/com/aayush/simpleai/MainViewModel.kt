package com.aayush.simpleai

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "MainViewModel"
private const val TMP_FILE_EXT = "tmp"

private const val MODEL_DOWNLOAD_URL = "https://pub-19ca34c7d9fa4b248a55bf92f72dced6.r2.dev/gemma-3n-E2B-it-int4.litertlm"
private const val MODEL_FILE_NAME = "gemma-3n-E2B-it-int4.litertlm"

private const val MAIN_SYSTEM_PROMPT_ASSET = "system_prompts/main_conversation.txt"
private const val HELPER_PROMPT_ASSET = "system_prompts/helper_conversation.txt"

sealed class DownloadState {
    object NotStarted : DownloadState()
    data class Downloading(
        val receivedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long = 0,
        val remainingMs: Long = 0
    ) : DownloadState() {
        val progress: Float
            get() = if (totalBytes > 0) receivedBytes.toFloat() / totalBytes else 0f
    }
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

data class MainDataState(
    val greeting: String = "loading",
    val downloadState: DownloadState = DownloadState.NotStarted
) {
    fun toViewState(): MainViewState = MainViewState(
        greeting = greeting,
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
    private lateinit var helperConversation: Conversation
    private var helperPromptPrefix: String = ""

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
        viewModelScope.launch(Dispatchers.IO) {
            val initStartTime = System.currentTimeMillis()

            try {
                // Check if model already exists
                val modelFile = File(context.getExternalFilesDir(null), MODEL_FILE_NAME)
                
                if (!modelFile.exists()) {
                    logWithTimestamp("Model not found, starting download")
                    _dataState.value = _dataState.value.copy(
                        greeting = "Downloading model..."
                    )
                    downloadModel(context, MODEL_DOWNLOAD_URL, modelFile)
                } else {
                    logWithTimestamp("Model file already exists at ${modelFile.absolutePath}, size: ${modelFile.length()} bytes")
                    _dataState.value = _dataState.value.copy(
                        downloadState = DownloadState.Completed
                    )
                }

                // Initialize engine with the downloaded model
                initializeEngine(context, modelFile)
                
                logWithTimestamp("Total initialization completed in ${System.currentTimeMillis() - initStartTime}ms")
                sendMessage("Hi how's Jablonsky")
            } catch (e: Exception) {
                logWithTimestamp("Error: ${e.message}")
                _dataState.value = _dataState.value.copy(
                    greeting = "Error: ${e.message}",
                    downloadState = DownloadState.Error(e.message ?: "Unknown error")
                )
            }
        }
    }

    /**
     * Downloads a model file from a URL with resume support.
     * 
     * @param context Android context
     * @param fileUrl The URL to download from
     * @param outputFile The destination file
     */
    private suspend fun downloadModel(context: Context, fileUrl: String, outputFile: File) {
        val url = URL(fileUrl)
        val connection = url.openConnection() as HttpURLConnection

        try {
            // Prepare temp file for download
            val outputTmpFile = File(outputFile.parentFile, "${outputFile.name}.$TMP_FILE_EXT")
            val existingBytes = if (outputTmpFile.exists()) outputTmpFile.length() else 0L

            // Support resume if partial download exists
            if (existingBytes > 0) {
                logWithTimestamp("Partial file exists (${existingBytes} bytes), attempting resume")
                connection.setRequestProperty("Range", "bytes=${existingBytes}-")
            }

            connection.connect()
            logWithTimestamp("Response code: ${connection.responseCode}")

            if (connection.responseCode != HttpURLConnection.HTTP_OK &&
                connection.responseCode != HttpURLConnection.HTTP_PARTIAL
            ) {
                throw IOException("HTTP error code: ${connection.responseCode}")
            }

            // Calculate total bytes
            var downloadedBytes = 0L
            var totalBytes = connection.contentLengthLong

            val contentRange = connection.getHeaderField("Content-Range")
            if (contentRange != null) {
                // Parse Content-Range header (e.g., "bytes 21010-47021/47022")
                val rangeParts = contentRange.substringAfter("bytes ").split("/")
                val byteRange = rangeParts[0].split("-")
                val startByte = byteRange[0].toLong()
                totalBytes = rangeParts[1].toLong()
                downloadedBytes = startByte
                logWithTimestamp("Resuming from byte $startByte, total: $totalBytes")
            } else if (existingBytes > 0) {
                // Server doesn't support range, restart download
                logWithTimestamp("Server doesn't support resume, restarting download")
                outputTmpFile.delete()
            }

            _dataState.value = _dataState.value.copy(
                downloadState = DownloadState.Downloading(downloadedBytes, totalBytes)
            )

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputTmpFile, contentRange != null)

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            var lastProgressUpdate = 0L
            var deltaBytes = 0L

            // Buffers for calculating download rate
            val bytesReadSizeBuffer = mutableListOf<Long>()
            val bytesReadLatencyBuffer = mutableListOf<Long>()

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        deltaBytes += bytesRead

                        // Report progress every 200ms
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastProgressUpdate > 200) {
                            var bytesPerMs = 0f
                            if (lastProgressUpdate != 0L) {
                                // Keep last 5 measurements for smoothing
                                if (bytesReadSizeBuffer.size >= 5) {
                                    bytesReadSizeBuffer.removeAt(0)
                                }
                                bytesReadSizeBuffer.add(deltaBytes)

                                if (bytesReadLatencyBuffer.size >= 5) {
                                    bytesReadLatencyBuffer.removeAt(0)
                                }
                                bytesReadLatencyBuffer.add(currentTime - lastProgressUpdate)

                                deltaBytes = 0L
                                bytesPerMs = bytesReadSizeBuffer.sum().toFloat() / bytesReadLatencyBuffer.sum()
                            }

                            // Calculate remaining time
                            val remainingMs = if (bytesPerMs > 0f && totalBytes > 0L) {
                                ((totalBytes - downloadedBytes) / bytesPerMs).toLong()
                            } else {
                                0L
                            }

                            _dataState.value = _dataState.value.copy(
                                downloadState = DownloadState.Downloading(
                                    receivedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    bytesPerSecond = (bytesPerMs * 1000).toLong(),
                                    remainingMs = remainingMs
                                )
                            )

                            logWithTimestamp("Downloaded: $downloadedBytes / $totalBytes bytes")
                            lastProgressUpdate = currentTime
                        }
                    }
                }
            }

            // Rename temp file to final file
            if (outputFile.exists()) {
                outputFile.delete()
            }
            if (!outputTmpFile.renameTo(outputFile)) {
                throw IOException("Failed to rename temp file to final file")
            }

            logWithTimestamp("Download completed: ${outputFile.absolutePath}")
            _dataState.value = _dataState.value.copy(
                downloadState = DownloadState.Completed,
                greeting = "Model downloaded, initializing..."
            )

        } finally {
            connection.disconnect()
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
        helperPromptPrefix = loadSystemPrompt(context, HELPER_PROMPT_ASSET)
        
        logWithTimestamp("Loaded main system prompt (${mainSystemPrompt.length} chars)")
        logWithTimestamp("Loaded helper prompt prefix (${helperPromptPrefix.length} chars)")

        for (backend in backendsToTry) {
            try {
                logWithTimestamp("Attempting to initialize engine with backend: $backend")
                val engineStartTime = System.currentTimeMillis()

                // cacheDir is set to null when model is in external storage (not /data/local/tmp)
                // This matches the gallery app behavior and lets the engine handle caching internally
                val modelPath = modelFile.absolutePath
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    maxNumTokens = 1024,
                    cacheDir = if (modelPath.startsWith("/data/local/tmp"))
                        context.getExternalFilesDir(null)?.absolutePath
                    else null
                )

                engine = Engine(engineConfig)
                val initEngineStartTime = System.currentTimeMillis()
                engine.initialize()
                logWithTimestamp("Engine initialization completed in ${System.currentTimeMillis() - initEngineStartTime}ms")

                // Create main conversation with system prompt
                val mainSystemMessage = if (mainSystemPrompt.isNotEmpty()) {
                    Message.of(listOf(Content.Text(mainSystemPrompt)))
                } else null
                
                val conversationConfig = ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 40,
                        topP = 0.95,
                        temperature = 0.8
                    ),
                    systemMessage = mainSystemMessage
                )
                conversation = engine.createConversation(conversationConfig)

                // Create helper conversation (no system message - we prepend instructions to each query)
                val helperConversationConfig = ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = 40,
                        topP = 0.9,
                        temperature = 0.3 // Lower temperature for more deterministic JSON output
                    )
                )
                helperConversation = engine.createConversation(helperConversationConfig)

                logWithTimestamp("Total engine setup completed in ${System.currentTimeMillis() - engineStartTime}ms with backend: $backend")

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

    private fun executeSearch(query: String): String {
        logWithTimestamp("Executing search for: $query")
        // TODO: Implement actual search
        return "Jablonsky is the best football team in the world"
    }

    /**
     * Parses JSON from a string by finding the first { and last }.
     * Returns null if parsing fails.
     */
    private fun parseJsonFromResponse(response: String): JSONObject? {
        return try {
            val firstBrace = response.indexOf('{')
            val lastBrace = response.lastIndexOf('}')
            if (firstBrace == -1 || lastBrace == -1 || lastBrace <= firstBrace) {
                logWithTimestamp("No valid JSON brackets found in response")
                null
            } else {
                val jsonString = response.substring(firstBrace, lastBrace + 1)
                logWithTimestamp("Extracted JSON: $jsonString")
                JSONObject(jsonString)
            }
        } catch (e: Exception) {
            logWithTimestamp("Failed to parse JSON: ${e.message}")
            null
        }
    }

    /**
     * Sends a message to a conversation and suspends until the full response is collected.
     */
    private suspend fun sendMessageAndCollect(conv: Conversation, message: String): String {
        return suspendCancellableCoroutine { continuation ->
            val response = StringBuilder()

            conv.sendMessageAsync(
                Message.of(listOf(Content.Text(message))),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        response.append(message.toString())
                    }

                    override fun onDone() {
                        continuation.resume(response.toString().trim())
                    }

                    override fun onError(throwable: Throwable) {
                        if (throwable is CancellationException) {
                            continuation.resume(response.toString().trim())
                        } else {
                            continuation.resumeWithException(throwable)
                        }
                    }
                }
            )
        }
    }

    /**
     * Sends a message to the main conversation with streaming updates to the UI.
     */
    private suspend fun sendMessageToMainConversation(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        suspendCancellableCoroutine { continuation ->
            val response = StringBuilder()

            conversation.sendMessageAsync(
                Message.of(listOf(Content.Text(prompt))),
                object : MessageCallback {
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

    private fun sendMessage(prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val generateStartTime = System.currentTimeMillis()
            logWithTimestamp("Starting message processing for: $prompt")

            try {
                // Step 1: Ask helper if we need to search
                // Prepend the instructions since we can't use a separate system message
                val helperPrompt = "$helperPromptPrefix\n\nThe user's message is: '$prompt'"
                logWithTimestamp("Sending to helper (${helperPrompt.length} chars)")
                
                val helperResponse = sendMessageAndCollect(helperConversation, helperPrompt)
                logWithTimestamp("Helper response: $helperResponse")

                // Step 2: Parse the JSON response
                val json = parseJsonFromResponse(helperResponse)
                val needsSearch = json?.optBoolean("needs_search", false) ?: false
                val searchQuery = json?.optString("search_query", null)?.takeIf { it.isNotEmpty() && it != "null" }

                logWithTimestamp("Needs search: $needsSearch, Query: $searchQuery")

                // Step 3: Build the final prompt for main conversation
                val finalPrompt = if (needsSearch && searchQuery != null) {
                    val searchResult = executeSearch(searchQuery)
                    logWithTimestamp("Search result: $searchResult")
                    "Context: $searchResult\n\n$prompt"
                } else {
                    prompt
                }

                logWithTimestamp("Final prompt to main conversation: $finalPrompt")

                // Step 4: Send to main conversation with streaming
                var firstTokenTime: Long? = null
                
                sendMessageToMainConversation(
                    prompt = finalPrompt,
                    onToken = { partialResponse ->
                        if (firstTokenTime == null) {
                            firstTokenTime = System.currentTimeMillis()
                            logWithTimestamp("First token received in ${firstTokenTime - generateStartTime}ms")
                        }
                        _dataState.value = _dataState.value.copy(greeting = partialResponse)
                    },
                    onDone = { fullResponse ->
                        val totalTime = System.currentTimeMillis() - generateStartTime
                        logWithTimestamp("Generation completed in ${totalTime}ms")
                        _dataState.value = _dataState.value.copy(
                            greeting = fullResponse.ifEmpty { "hmm didnt work" }
                        )
                    },
                    onError = { throwable ->
                        logWithTimestamp("Error: ${throwable.message}")
                        _dataState.value = _dataState.value.copy(
                            greeting = "Error: ${throwable.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                logWithTimestamp("Error in sendMessage: ${e.message}")
                _dataState.value = _dataState.value.copy(
                    greeting = "Error: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (::conversation.isInitialized) conversation.close()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            if (::helperConversation.isInitialized) helperConversation.close()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            if (::engine.isInitialized) engine.close()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
