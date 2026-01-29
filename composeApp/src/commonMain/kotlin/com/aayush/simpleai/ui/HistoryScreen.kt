package com.aayush.simpleai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aayush.simpleai.MainViewState
import com.aayush.simpleai.db.ChatHistory
import com.aayush.simpleai.ui.theme.AppTheme
import com.aayush.simpleai.ui.theme.backgroundDark

@Composable
fun HistoryScreen(
    historyRows: List<MainViewState.HistoryRowState>,
    isNewChat: Boolean,
    onDismiss: () -> Unit,
    onSelectChat: (Long) -> Unit,
    onNewChat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Header with X button
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                modifier = Modifier.align(Alignment.Center).padding(top = 8.dp),
                text = "Chat History",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                modifier = Modifier.align(Alignment.TopStart),
                onClick = onDismiss
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            if (!isNewChat) {
                IconButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = onNewChat
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Chat",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(height = 8.dp))

        // History list
        if (historyRows.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(weight = 1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chat history yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(weight = 1f)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                0.9f to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    },
            ) {
                itemsIndexed(
                    items = historyRows,
                    key = { _, row -> row.id }
                ) { index, row ->
                    Column {
                        HistoryRowItem(
                            row = row,
                            onSelect = { onSelectChat(row.id) },
                        )
                        if (index < historyRows.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRowItem(
    row: MainViewState.HistoryRowState,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(all = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(weight = 1f),
            text = "\"${row.headerText}\"",
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
                .copy(alpha = if (row.isSelected) 0.6f else 1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(width = 4.dp))
        Text(
            modifier = Modifier.align(Alignment.Bottom),
            text = row.timestampText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = if (row.isSelected) 0.6f else 1f),
        )
    }
}

@Composable
@Preview(showBackground = true)
fun HistoryScreenPreview() {
    AppTheme {
        ChatScreen(
            messages = listOf(),
            isGenerating = false,
            historyRows = listOf(
                MainViewState.HistoryRowState(
                    id = 1,
                    headerText = "Hello, how can I help you today?",
                    timestampText = "2h ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 2,
                    headerText = "What is the capital of France?",
                    timestampText = "1d ago",
                    isSelected = true,
                ),
                MainViewState.HistoryRowState(
                    id = 3,
                    headerText = "Hello, how can I help you today?",
                    timestampText = "2h ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 4,
                    headerText = "What is the capital of France?",
                    timestampText = "1d ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 5,
                    headerText = "Hello, how can I help you today?",
                    timestampText = "2h ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 6,
                    headerText = "What is the capital of France?",
                    timestampText = "1d ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 7,
                    headerText = "Hello, how can I help you today?",
                    timestampText = "2h ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 8,
                    headerText = "What is the capital of France?",
                    timestampText = "1d ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 9,
                    headerText = "Hello, how can I help you today?",
                    timestampText = "2h ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 10,
                    headerText = "What is the capital of France?",
                    timestampText = "1d ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 17,
                    headerText = "Hello, how can I help you today?",
                    timestampText = "2h ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 18,
                    headerText = "What is the capital of France?",
                    timestampText = "1d ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 29,
                    headerText = "Hello, how can I help you today?",
                    timestampText = "2h ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 210,
                    headerText = "What is the capital of France?",
                    timestampText = "1d ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 217,
                    headerText = "Hello, how can I help you today?",
                    timestampText = "2h ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 218,
                    headerText = "What is the capital of France?",
                    timestampText = "1d ago",
                    isSelected = false,
                ),
            ),
            isNewChat = true,
            onSendMessage = {},
            onSelectChat = {},
            onNewChat = {},
            onDeleteChat = {},
            chatOptions = ChatHistory.Options(),
            onSetChatOptions = {},
        )
    }
}

@Composable
@Preview(showBackground = true)
fun PlainHistoryScreenPreview() {
    AppTheme {
        HistoryScreen(
            historyRows = listOf(
                MainViewState.HistoryRowState(
                    id = 1,
                    headerText = "Hello, how can I help you today? Hello, how can I help you today?",
                    timestampText = "2h ago",
                    isSelected = false,
                ),
                MainViewState.HistoryRowState(
                    id = 2,
                    headerText = "What is the capital of France?",
                    timestampText = "1d ago",
                    isSelected = true,
                ),
                MainViewState.HistoryRowState(
                    id = 3,
                    headerText = "idk",
                    timestampText = "1d ago",
                    isSelected = false,
                ),
            ),
            isNewChat = false,
            onDismiss = {},
            onSelectChat = { },
            onNewChat = { },
        )
    }
}