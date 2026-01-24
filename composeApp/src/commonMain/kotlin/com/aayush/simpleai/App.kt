package com.aayush.simpleai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel
import com.aayush.simpleai.ui.ChatScreen
import com.aayush.simpleai.ui.DownloadScreen
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
                .safeContentPadding()
                .imePadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (viewState.downloadState) {
                is DownloadState.Downloading,
                is DownloadState.Error,
                DownloadState.NotStarted -> {
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