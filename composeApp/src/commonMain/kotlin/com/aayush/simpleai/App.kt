package com.aayush.simpleai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

import simpleai.composeapp.generated.resources.Res
import simpleai.composeapp.generated.resources.*
import com.aayush.simpleai.util.DownloadState

@Composable
@Preview
fun App() {
    MaterialTheme {
        val viewModel = koinViewModel<MainViewModel>()
        val viewState by viewModel.viewState.collectAsState()

        var showContent by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Download progress section
            DownloadProgress(viewState.greeting, viewState.downloadState)
        }
    }
}

@Composable
@Preview
fun DownloadProgress(greeting: String, downloadState: DownloadState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (downloadState) {
            is DownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                val receivedMB = downloadState.receivedBytes / 1024 / 1024
                val totalMB = if (downloadState.totalBytes > 0) downloadState.totalBytes / 1024 / 1024 else null
                Text(
                    text = if (totalMB != null) {
                        stringResource(Res.string.download_progress, receivedMB, totalMB)
                    } else {
                        stringResource(Res.string.download_progress_unknown, receivedMB)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            is DownloadState.Completed -> {
                Text(
                    text = stringResource(Res.string.ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is DownloadState.Error -> {
                Text(
                    text = stringResource(Res.string.error_prefix, downloadState.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
            }
        }
    }
}