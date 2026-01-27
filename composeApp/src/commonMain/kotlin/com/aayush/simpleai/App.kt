package com.aayush.simpleai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource
import simpleai.composeapp.generated.resources.Res
import simpleai.composeapp.generated.resources.not_enough_memory
import simpleai.composeapp.generated.resources.not_enough_memory_and_storage
import simpleai.composeapp.generated.resources.not_enough_storage
import com.aayush.simpleai.ui.ChatScreen
import com.aayush.simpleai.ui.DownloadScreen
import com.aayush.simpleai.ui.WelcomeScreen
import com.aayush.simpleai.ui.theme.AppTheme
import com.aayush.simpleai.util.DownloadState

@Composable
fun App() {
    AppTheme {
        val viewModel = koinViewModel<MainViewModel>()
        val viewState by viewModel.viewState.collectAsState()

        Column(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.primaryContainer)
                .systemBarsPadding()
                .imePadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (viewState.downloadState) {
                DownloadState.LoadingFile -> {}
                is DownloadState.NotStarted -> {
                    val notEnoughMemory = viewState.notEnoughMemory
                    val notEnoughStorage = viewState.notEnoughStorage
                    val cannotDownloadMessage = when {
                        notEnoughMemory != null && notEnoughStorage != null -> {
                            stringResource(
                                resource = Res.string.not_enough_memory_and_storage,
                                notEnoughMemory.actualGBString,
                                notEnoughMemory.neededGBString,
                                notEnoughStorage.actualGBString,
                                notEnoughStorage.neededGBString,
                            )
                        }
                        notEnoughMemory != null -> {
                            stringResource(
                                resource = Res.string.not_enough_memory,
                                notEnoughMemory.actualGBString,
                                notEnoughMemory.neededGBString,
                            )
                        }
                        notEnoughStorage != null -> {
                            stringResource(
                                resource = Res.string.not_enough_storage,
                                notEnoughStorage.actualGBString,
                                notEnoughStorage.neededGBString,
                            )
                        }
                        else -> null
                    }
                    WelcomeScreen(
                        onProceed = { viewModel.downloadModel() },
                        remainingStorage = viewState.remainingStorageString,
                        cannotDownloadMessage = cannotDownloadMessage,
                    )
                }
                is DownloadState.Downloading,
                is DownloadState.Error,
                DownloadState.Starting -> {
                    DownloadScreen(viewState.downloadState)
                }
                DownloadState.Completed -> ChatScreen(
                    messages = viewState.messages,
                    isGenerating = viewState.isGenerating,
                    onSendMessage = { viewModel.sendMessage(prompt = it) }
                )
            }
        }
    }
}