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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aayush.simpleai.MainViewState
import com.aayush.simpleai.llm.Message
import com.aayush.simpleai.llm.Role
import com.aayush.simpleai.db.ChatHistory
import com.aayush.simpleai.ui.theme.AppTheme
import com.aayush.simpleai.ui.theme.backgroundDark
import com.preat.peekaboo.image.picker.SelectionMode
import com.preat.peekaboo.image.picker.rememberImagePickerLauncher
import com.preat.peekaboo.image.picker.toImageBitmap
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.theolm.record.Record
import dev.theolm.record.config.OutputFormat
import dev.theolm.record.config.RecordConfig
import eu.iamkonstantin.kotlin.gadulka.GadulkaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.jetbrains.compose.resources.stringResource
import simpleai.composeapp.generated.resources.Res
import simpleai.composeapp.generated.resources.*
import kotlin.math.PI
import kotlin.math.sin


@Composable
fun ChatScreen(
    messages: List<MainViewState.MessageViewState>,
    isGenerating: Boolean,
    historyRows: List<MainViewState.HistoryRowState>,
    isNewChat: Boolean,
    chatOptions: ChatHistory.Options,
    onSendMessage: (Message) -> Unit,
    onSelectChat: (Long) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (Long) -> Unit,
    onSetChatOptions: (ChatHistory.Options) -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }
    val blurRadius by animateDpAsState(targetValue = if (showHistory) 24.dp else 0.dp)

    Box(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize().blur(blurRadius)) {
            ChatScreenContent(
                messages = messages,
                isGenerating = isGenerating,
                isNewChat = isNewChat,
                chatOptions = chatOptions,
                onSendMessage = onSendMessage,
                onSetChatOptions = onSetChatOptions,
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
            HistoryScreen(
                historyRows = historyRows,
                isNewChat = isNewChat,
                onDismiss = { showHistory = false },
                onSelectChat = { chatId ->
                    onSelectChat(chatId)
                    showHistory = false
                },
                onNewChat = {
                    onNewChat()
                    showHistory = false
                },
            )
        }
    }
}

@Composable
fun ChatScreenContent(
    messages: List<MainViewState.MessageViewState>,
    isGenerating: Boolean,
    isNewChat: Boolean,
    chatOptions: ChatHistory.Options,
    onSendMessage: (Message) -> Unit,
    onSetChatOptions: (ChatHistory.Options) -> Unit,
) {

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

        InputArea(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp),
            isGenerating = isGenerating,
            isNewChat = isNewChat,
            chatOptions = chatOptions,
            onSendMessage = onSendMessage,
            onSetChatOptions = onSetChatOptions,
        )
    }
}

@Composable
private fun InputArea(
    modifier: Modifier,
    isGenerating: Boolean,
    isNewChat: Boolean,
    chatOptions: ChatHistory.Options,
    onSendMessage: (Message) -> Unit,
    onSetChatOptions: (ChatHistory.Options) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf(value = "") }
    var pendingImages by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var pendingAudio by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var isRecording by remember { mutableStateOf(false) }
    var showMediaAlert by remember { mutableStateOf(false) }
    
    // Audio player for previews
    val audioPlayer = remember { GadulkaPlayer() }
    var playingAudioIndex by remember { mutableStateOf<Int?>(null) }
    
    // Image picker launcher
    val imagePickerLauncher = rememberImagePickerLauncher(
        selectionMode = SelectionMode.Multiple(maxSelection = 5),
        scope = scope,
        onResult = { byteArrays ->
            pendingImages = pendingImages + byteArrays
        }
    )

    Column(modifier = modifier) {

        if (isNewChat) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InputPill(
                    icon = Icons.Outlined.Language,
                    text = stringResource(Res.string.enable_search),
                    isHighlighted = chatOptions.searchEnabled,
                    onClick = {
                        onSetChatOptions(chatOptions.copy(searchEnabled = !chatOptions.searchEnabled))
                    }
                )
                InputPill(
                    icon = Icons.Outlined.Image,
                    text = stringResource(Res.string.enable_media),
                    isHighlighted = chatOptions.mediaEnabled,
                    onClick = {
                        val newMediaEnabled = !chatOptions.mediaEnabled
                        onSetChatOptions(chatOptions.copy(mediaEnabled = newMediaEnabled))
                        if (newMediaEnabled) {
                            showMediaAlert = true
                        }
                    }
                )
            }
        }

        if (chatOptions.mediaEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InputPill(
                    icon = Icons.Outlined.Image,
                    text = "+image",
                    onClick = { imagePickerLauncher.launch() }
                )
                InputPill(
                    icon = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                    text = if (isRecording) "stop" else "+audio",
                    isHighlighted = isRecording,
                    onClick = {
                        scope.launch {
                            if (isRecording) {
                                // Stop recording
                                try {
                                    val savedPath = Record.stopRecording()
                                    // Read the recorded file into bytes
                                    withContext(Dispatchers.IO) {
                                        val path = savedPath.toPath()
                                        if (FileSystem.SYSTEM.exists(path)) {
                                            val bytes = FileSystem.SYSTEM.read(path) { readByteArray() }
                                            pendingAudio = pendingAudio + bytes
                                            // Clean up temp file
                                            FileSystem.SYSTEM.delete(path)
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                isRecording = false
                            } else {
                                // Start recording
                                try {
                                    Record.setConfig(
                                        RecordConfig(
                                            outputFormat = OutputFormat.MPEG_4,
                                            sampleRate = 44100
                                        )
                                    )
                                    Record.startRecording()
                                    isRecording = true
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                )
            }
        }
        
        // Attachment preview row
        if (pendingImages.isNotEmpty() || pendingAudio.isNotEmpty()) {
            AttachmentPreviewRow(
                images = pendingImages,
                audioClips = pendingAudio,
                playingAudioIndex = playingAudioIndex,
                onRemoveImage = { index ->
                    pendingImages = pendingImages.toMutableList().apply { removeAt(index) }
                },
                onRemoveAudio = { index ->
                    if (playingAudioIndex == index) {
                        audioPlayer.stop()
                        playingAudioIndex = null
                    }
                    pendingAudio = pendingAudio.toMutableList().apply { removeAt(index) }
                },
                onPlayAudio = { index, bytes ->
                    scope.launch {
                        if (playingAudioIndex == index) {
                            audioPlayer.stop()
                            playingAudioIndex = null
                        } else {
                            // Write bytes to temp file and play
                            withContext(Dispatchers.IO) {
                                val tempPath = FileSystem.SYSTEM.canonicalize(".".toPath())
                                    .resolve("temp_audio_preview_$index.m4a")
                                FileSystem.SYSTEM.write(tempPath) { write(bytes) }
                                withContext(Dispatchers.Main) {
                                    audioPlayer.play(tempPath.toString())
                                    playingAudioIndex = index
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {

            val canSend = (inputText.isNotBlank() || pendingImages.isNotEmpty() || pendingAudio.isNotEmpty()) && !isGenerating
            val sendMessageAndClearInput: () -> Unit = {
                if (canSend) {
                    onSendMessage(Message.user(
                        text = inputText,
                        images = pendingImages,
                        audio = pendingAudio
                    ))
                    inputText = ""
                    pendingImages = emptyList()
                    pendingAudio = emptyList()
                }
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(weight = 1f),
                placeholder = { Text(text = "Message") },
                enabled = !isGenerating,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                shape = RoundedCornerShape(size = 24.dp),
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(width = 8.dp))
            IconButton(
                onClick = sendMessageAndClearInput,
                enabled = canSend
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription =
                        stringResource(resource = Res.string.send_button_description),
                    tint = if (canSend)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }

        if (showMediaAlert) {

            AlertDialog(
                onDismissRequest = { showMediaAlert = false },
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                title = { Text(text = stringResource(Res.string.media_alert_title)) },
                text = { Text(text = stringResource(Res.string.media_alert_message)) },
                confirmButton = {
                    TextButton(onClick = { showMediaAlert = false }) {
                        Text(text = stringResource(Res.string.proceed))
                    }
                }
            )
        }
    }
}

@Composable
private fun InputPill(
    icon: ImageVector,
    text: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = if (isHighlighted) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isHighlighted) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = contentColor
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = contentColor
        )
    }
}

/**
 * Row of attachment previews (images and audio) with remove buttons.
 */
@Composable
private fun AttachmentPreviewRow(
    images: List<ByteArray>,
    audioClips: List<ByteArray>,
    playingAudioIndex: Int?,
    onRemoveImage: (Int) -> Unit,
    onRemoveAudio: (Int) -> Unit,
    onPlayAudio: (Int, ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Image previews
        itemsIndexed(images) { index, imageBytes ->
            ImagePreviewItem(
                imageBytes = imageBytes,
                onRemove = { onRemoveImage(index) }
            )
        }
        
        // Audio previews
        itemsIndexed(audioClips) { index, audioBytes ->
            AudioPreviewItem(
                isPlaying = playingAudioIndex == index,
                onRemove = { onRemoveAudio(index) },
                onPlay = { onPlayAudio(index, audioBytes) }
            )
        }
    }
}

/**
 * Preview item for an image attachment with remove button.
 */
@Composable
private fun ImagePreviewItem(
    imageBytes: ByteArray,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier.size(64.dp)
    ) {
        val imageBitmap = remember(imageBytes) { imageBytes.toImageBitmap() }
        Image(
            bitmap = imageBitmap,
            contentDescription = "Image attachment",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        // Remove button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove image",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onError
            )
        }
    }
}

/**
 * Preview item for an audio attachment with play/remove buttons.
 */
@Composable
private fun AudioPreviewItem(
    isPlaying: Boolean,
    onRemove: () -> Unit,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier.size(64.dp)
    ) {
        // Audio icon background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause audio" else "Play audio",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        
        // Remove button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove audio",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onError
            )
        }
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
    
    val hasMedia = message.imageContent.isNotEmpty() || message.audioContent.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isEndAligned) Alignment.End else Alignment.Start
    ) {
        // Media content (images and audio) displayed above the text bubble
        if (hasMedia) {
            MessageMediaContent(
                images = message.imageContent,
                audioClips = message.audioContent,
                isEndAligned = message.isEndAligned,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        
        // Text bubble (only show if there's text content or loading)
        if (message.markdownContent.isNotBlank() || message.isLoading) {
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
                        val mdState = rememberMarkdownState(
                            message.markdownContent.trimEnd(),
                            retainState = true, // keeps old render while new parse runs
                        )
                        Markdown(
                            markdownState = mdState,
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
    }
}

/**
 * Media content (images and audio) for a message bubble.
 */
@Composable
private fun MessageMediaContent(
    images: List<ByteArray>,
    audioClips: List<ByteArray>,
    isEndAligned: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val audioPlayer = remember { GadulkaPlayer() }
    var playingAudioIndex by remember { mutableStateOf<Int?>(null) }
    
    // Clean up audio player when composable is disposed
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isEndAligned) Arrangement.End else Arrangement.Start
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Display images
            images.forEach { imageBytes ->
                MessageImageItem(imageBytes = imageBytes)
            }
            
            // Display audio clips
            audioClips.forEachIndexed { index, audioBytes ->
                MessageAudioItem(
                    audioBytes = audioBytes,
                    isPlaying = playingAudioIndex == index,
                    onPlay = {
                        scope.launch {
                            if (playingAudioIndex == index) {
                                audioPlayer.stop()
                                playingAudioIndex = null
                            } else {
                                withContext(Dispatchers.IO) {
                                    val tempPath = FileSystem.SYSTEM.canonicalize(".".toPath())
                                        .resolve("temp_msg_audio_$index.m4a")
                                    FileSystem.SYSTEM.write(tempPath) { write(audioBytes) }
                                    withContext(Dispatchers.Main) {
                                        audioPlayer.play(tempPath.toString())
                                        playingAudioIndex = index
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Image item in a message bubble.
 */
@Composable
private fun MessageImageItem(
    imageBytes: ByteArray
) {
    val imageBitmap = remember(imageBytes) { imageBytes.toImageBitmap() }
    Image(
        bitmap = imageBitmap,
        contentDescription = "Image attachment",
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop
    )
}

/**
 * Audio item in a message bubble with play button.
 */
@Composable
private fun MessageAudioItem(
    audioBytes: ByteArray,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 80.dp, height = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onPlay),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Icon(
                imageVector = Icons.Outlined.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
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
        ChatScreen(
            messages = listOf(),
            isGenerating = false,
            historyRows = emptyList(),
            isNewChat = true,
            chatOptions = ChatHistory.Options(),
            onSendMessage = {},
            onSelectChat = {},
            onNewChat = {},
            onDeleteChat = {},
            onSetChatOptions = {},
        )
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
            historyRows = emptyList(),
            isNewChat = true,
            chatOptions = ChatHistory.Options(),
            onSendMessage = { },
            onSelectChat = {},
            onNewChat = {},
            onDeleteChat = {},
            onSetChatOptions = {},
        )
    }
}
