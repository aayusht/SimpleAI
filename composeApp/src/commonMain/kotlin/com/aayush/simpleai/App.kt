package com.aayush.simpleai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpleai.composeapp.generated.resources.Res
import simpleai.composeapp.generated.resources.*
import com.aayush.simpleai.llm.Role
import com.aayush.simpleai.util.DownloadState
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun App() {
    MaterialTheme {
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
                    Spacer(modifier = Modifier.height(height = 32.dp))
                    DownloadProgress(viewState.downloadState)
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

@Composable
fun ChatScreen(
    messages: List<MainViewState.Message>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf(value = "") }
    val listState = rememberLazyListState()
    
    LaunchedEffect(key1 = messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(index = messages.size - 1)
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(weight = 1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(height = 8.dp)) }
            items(items = messages, key = { it.key }) { MessageBubble(it) }
            item { Spacer(modifier = Modifier.height(height = 8.dp)) }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            val sendMessageAndClearInput: () -> Unit = {
                if (inputText.isNotBlank() && !isGenerating) {
                    onSendMessage(inputText)
                    inputText = ""
                }
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(weight = 1f),
                placeholder = { Text(text = "Message") },
                enabled = !isGenerating,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessageAndClearInput() }),
                shape = RoundedCornerShape(size = 24.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(width = 8.dp))
            IconButton(
                onClick = sendMessageAndClearInput,
                enabled = inputText.isNotBlank() && !isGenerating
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription =
                        stringResource(resource = Res.string.send_button_description),
                    tint = if (inputText.isNotBlank() && !isGenerating) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: MainViewState.Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = message.arrangement
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(message.clipShape)
                .background(
                    color = if (message.usePrimaryBackground) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(all = 12.dp)
        ) {
            if (message.isLoading) {
                BouncingDots()
            } else {
                Markdown(
                    content = message.markdownContent,
                    colors = markdownColor(
                        text = if (message.usePrimaryBackground) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ),
                )
            }
        }
    }
}

@Composable
fun BouncingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(times = 3) { index ->
            val offset = sin(x = (time * 2 * PI + index * 0.5).toFloat()) * 4
            Box(
                modifier = Modifier
                    .offset(y = (-offset).dp)
                    .size(size = 8.dp)
                    .clip(CircleShape)
                    .background(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            )
        }
    }
}

@Composable
fun DownloadProgress(downloadState: DownloadState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(resource = Res.string.downloading_model),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(height = 16.dp))

        when (downloadState) {
            is DownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height = 8.dp)
                )
                Spacer(modifier = Modifier.height(height = 8.dp))
                Text(
                    text = if (downloadState.totalMB > 0L) {
                        stringResource(
                            resource = Res.string.download_progress,
                            downloadState.receivedMB,
                            downloadState.totalMB,
                        )
                    } else {
                        stringResource(
                            resource = Res.string.download_progress_unknown,
                            downloadState.receivedMB,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            is DownloadState.Error -> {
                Text(
                    text = stringResource(
                        resource = Res.string.error_prefix,
                        downloadState.message,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height = 8.dp)
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun DownloadProgressPreview() {
    var receivedMB by remember { mutableStateOf(value = 10L) }
    LaunchedEffect(key1 = Unit) {
        while (receivedMB < 90) {
            delay(timeMillis = 1000L)
            receivedMB += 1
        }
    }
    MaterialTheme {
        DownloadProgress(
            downloadState = DownloadState.Downloading(
                receivedBytes = receivedMB * 1024 * 1024,
                totalBytes = 100L * 1024 * 1024,
                bytesPerSecond = 1L * 1024 * 1024,
                remainingMs = (100L - receivedMB) * 1000,
            )
        )
    }
}

@Composable
@Preview(showBackground = true)
fun ChatScreenPreview() {
    MaterialTheme {
        ChatScreen(
            messages = listOf(
                MainViewState.Message(
                    id = 0,
                    role = Role.USER,
                    content = "Hey what's up",
                    isLoading = false,
                ),
                MainViewState.Message(
                    id = 1,
                    role = Role.ASSISTANT,
                    content = "It's the [bomb.com](bomb.com) how u doin",
                    isLoading = false,
                ),
                MainViewState.Message(
                    id = 2,
                    role = Role.USER,
                    content = "**cool**",
                    isLoading = false,
                ),
                MainViewState.Message(
                    id = 3,
                    role = Role.ASSISTANT,
                    content = "",
                    isLoading = true,
                ),
            ),
            isGenerating = true,
            onSendMessage = { },
        )
    }
}
