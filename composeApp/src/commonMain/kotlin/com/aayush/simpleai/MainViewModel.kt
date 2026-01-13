package com.aayush.simpleai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

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
    private val httpClient: HttpClient
) : ViewModel() {
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
                }
            } catch (e: Exception) {
                _dataState.value = _dataState.value.copy(
                    greeting = "Error: ${e.message}",
                    downloadState = DownloadState.Error(e.message ?: "Unknown error")
                )
            }
        }
    }
}