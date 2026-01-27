package com.aayush.simpleai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aayush.simpleai.MainViewState
import com.aayush.simpleai.llm.Role
import com.aayush.simpleai.ui.theme.AppTheme
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.rememberMarkdownState
import org.jetbrains.compose.resources.stringResource
import simpleai.composeapp.generated.resources.Res
import simpleai.composeapp.generated.resources.send_button_description
import simpleai.composeapp.generated.resources.welcome_title
import kotlin.math.PI
import kotlin.math.sin


@Composable
fun ChatScreen(
    messages: List<MainViewState.MessageViewState>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
) {



    var showHistory by remember { mutableStateOf(false) }
    val blurRadius by animateDpAsState(targetValue = if (showHistory) 24.dp else 0.dp)

    Box(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize().blur(blurRadius)) {
            ChatScreenContent(
                messages = messages,
                isGenerating = isGenerating,
                onSendMessage = onSendMessage,
            )

            IconButton(
                onClick = { showHistory = true },
                colors = IconButtonDefaults.iconButtonColors().copy(
                    containerColor = IconButtonDefaults.iconButtonColors().containerColor,
                    contentColor = IconButtonDefaults.iconButtonColors().contentColor.copy(alpha = 0.5f),
                    disabledContainerColor = IconButtonDefaults.iconButtonColors().disabledContainerColor,
                    disabledContentColor = IconButtonDefaults.iconButtonColors().disabledContentColor.copy(
                        alpha = 0.5f
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "History",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        AnimatedVisibility(
            visible = showHistory,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            HistoryScreen(onDismiss = { showHistory = false })
        }
    }
}

@Composable
fun ChatScreenContent(
    messages: List<MainViewState.MessageViewState>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit
) {

    var inputText by remember { mutableStateOf(value = "") }
    val listState = rememberLazyListState()
    var wasAtBottom by remember { mutableStateOf(value = true) }

    // Update wasAtBottom whenever the scroll position changes using snapshotFlow to avoid performance issues
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisibleItem == null) {
                true
            } else {
                lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 2
            }
        }.collect { atBottom ->
            wasAtBottom = atBottom
        }
    }

    LaunchedEffect(key1 = messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(index = messages.size - 1)
        }
    }

    // Auto-scroll when generating and was at bottom
    LaunchedEffect(key1 = messages.lastOrNull()?.markdownContent, key2 = isGenerating) {
        if (isGenerating && wasAtBottom && messages.isNotEmpty()) {
            listState.scrollToItem(index = messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (messages.isNotEmpty()) {
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
        } else {
            Box(
                modifier = Modifier
                    .weight(weight = 1f)
                    .padding(start = 8.dp, end = 96.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    textAlign = TextAlign.Start,
                    text = stringResource(resource = Res.string.welcome_title),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
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
fun HistoryScreen(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Empty for now as requested
    }
}

@Composable
fun MessageBubble(message: MainViewState.MessageViewState) {
    val arrangement = if (message.isEndAligned) Arrangement.End else Arrangement.Start
    val clipShape = remember(message.isEndAligned) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (message.isEndAligned) 16.dp else 4.dp,
            bottomEnd = if (message.isEndAligned) 4.dp else 16.dp
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = arrangement
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(clipShape)
                .background(
                    color = if (message.usePrimaryBackground) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiaryContainer
                )
                .padding(all = 12.dp)
        ) {
            if (message.isLoading) {
                BouncingDots()
            } else {
//                Text(message.markdownContent.trimEnd())
                val mdState = rememberMarkdownState(
                    message.markdownContent.trimEnd(),
                    retainState = true, // keeps old render while new parse runs
                )
                Markdown(
                    markdownState = mdState,
//                    content = message.markdownContent,
                    modifier = Modifier.wrapContentSize(),
                    colors = markdownColor(
                        text = if (message.usePrimaryBackground) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
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
@Preview(showBackground = true)
fun EmptyChatScreenPreview() {
    AppTheme {
        ChatScreen(messages = listOf(), isGenerating = false, onSendMessage = {})
    }
}

@Composable
@Preview(showBackground = true)
fun EmptyChatScreenWithHistoryPreview() {
    AppTheme {
        Box {
            ChatScreen(messages = listOf(), isGenerating = false, onSendMessage = {})
            HistoryScreen(onDismiss = {})
        }
    }
}

@Composable
@Preview(showBackground = true)
fun ChatScreenPreview() {
    AppTheme {
        val targetContent = """
Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
        """.trimIndent()
        val content by fakeAiOutput(text = targetContent)
        ChatScreen(
            messages = listOf(
                MainViewState.MessageViewState(
                    id = 0,
                    role = Role.USER,
                    content = "Hey what's up",
                    isLoading = false,
                ),
                MainViewState.MessageViewState(
                    id = 1,
                    role = Role.ASSISTANT,
                    content = "It's the [bomb.com](bomb.com) how u doin",
                    isLoading = false,
                ),
                MainViewState.MessageViewState(
                    id = 2,
                    role = Role.USER,
                    content = "**cool**",
                    isLoading = false,
                ),
                MainViewState.MessageViewState(
                    id = 3,
                    role = Role.ASSISTANT,
                    content = content,
                    isLoading = content.isBlank(),
                ),
            ),
            isGenerating = true,
            onSendMessage = { },
        )
    }
}
