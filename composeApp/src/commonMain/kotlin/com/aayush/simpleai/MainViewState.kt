package com.aayush.simpleai

import androidx.compose.runtime.Immutable
import com.aayush.simpleai.db.ChatHistory
import com.aayush.simpleai.llm.Message
import com.aayush.simpleai.llm.Role
import com.aayush.simpleai.util.DownloadState
import simpleai.composeapp.generated.resources.Res

data class MainViewState(
    val downloadState: DownloadState,
    val messages: List<MessageViewState>,
    val isGenerating: Boolean,
    val remainingStorage: Long?,
    val notEnoughStorage: NotEnoughBytes?,
    val notEnoughMemory: NotEnoughBytes?,
    val historyRows: List<HistoryRowState> = emptyList(),
    val isNewChat: Boolean = true,
) {

    @Immutable
    data class HistoryRowState(
        val id: Long,
        val headerText: String,
        val timestampText: String,
        val isSelected: Boolean,
    ) {
        companion object {
            fun from(chatHistory: ChatHistory, activeChatId: Long?): HistoryRowState {
                return HistoryRowState(
                    id = chatHistory.id,
                    headerText = chatHistory.getPreviewText(),
                    timestampText = formatTimestamp(chatHistory.timestamp),
                    isSelected = chatHistory.id == activeChatId,
                )
            }

            private fun formatTimestamp(timestamp: Long): String {
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                val diff = now - timestamp
                val seconds = diff / 1000
                val minutes = seconds / 60
                val hours = minutes / 60
                val days = hours / 24

                return when {
                    days > 0 -> "${days}d ago"
                    hours > 0 -> "${hours}h ago"
                    minutes > 0 -> "${minutes}m ago"
                    else -> "Just now"
                }
            }
        }
    }

    val remainingStorageString: String =
        formatOneDecimalPoint(float = (remainingStorage ?: 0).toFloat() / (1024 * 1024 * 1024))

    data class NotEnoughBytes(val actualBytes: Long, val neededBytes: Long) {

        val actualGBString: String
            get() = formatOneDecimalPoint(float = actualBytes.toFloat() / (1024 * 1024 * 1024))
        val neededGBString: String
            get() = formatOneDecimalPoint(float = neededBytes.toFloat() / (1024 * 1024 * 1024))

        companion object {

            private const val EPSILON = 0.01f

            fun from(actualBytes: Long, neededBytes: Long): NotEnoughBytes? {
                if (actualBytes >= neededBytes * (1 - EPSILON)) return null
                return NotEnoughBytes(actualBytes, neededBytes)
            }
        }
    }

    @Immutable
    data class MessageViewState(
        val key: String,
        val isLoading: Boolean,
        val markdownContent: String,
        val isEndAligned: Boolean,
        val usePrimaryBackground: Boolean,
    ) {

        constructor(
            id: Long,
            role: Role,
            content: String,
            isLoading: Boolean,
        ) : this(
            key = "$id${role.name}",
            isLoading = isLoading,
            markdownContent = content.trimEnd(),
            usePrimaryBackground = role == Role.USER,
            isEndAligned = role == Role.USER,
        )
    }

    sealed interface SystemPrompt {

        suspend fun getText(): String

        object General : SystemPrompt {
            override suspend fun getText() = getTextFromFile(filename = "general.md")
        }

        object Finance : SystemPrompt {
            override suspend fun getText() = getTextFromFile(filename = "finance.md")
        }

        object Medical : SystemPrompt {
            override suspend fun getText() = getTextFromFile(filename = "medical.md")
        }

        object Recipes : SystemPrompt {
            override suspend fun getText() = getTextFromFile(filename = "recipes.md")
        }

        object Therapy : SystemPrompt {
            override suspend fun getText() = getTextFromFile(filename = "therapy.md")
        }

        object Translation : SystemPrompt {
            override suspend fun getText() = getTextFromFile(filename = "translation.md")
        }

        object Travel : SystemPrompt {
            override suspend fun getText() = getTextFromFile(filename = "travel.md")
        }

        data class Custom(val text: String) : SystemPrompt {
            override suspend fun getText() = text
        }

        companion object {
            private suspend fun getTextFromFile(filename: String): String =
                Res.readBytes("files/$filename").decodeToString()
        }
    }

    companion object {
        private fun formatOneDecimalPoint(float: Float): String =
            "${float.toInt()}.${(float * 10).toInt() % 10}"

        private val Message.isToolCallsOnly: Boolean
            get() = visibleText.isBlank() && fullText.text.isNotBlank()


        fun getMessageViewStates(
            messages: List<Message>,
            dbMessages: List<Message>?,
            userMessageBeingGeneratedFor: Message?,
            isEngineReady: Boolean,
        ): List<MessageViewState> {
            val messagesToShow = if (isEngineReady) {
                messages
            } else {
                buildList {
                    // it'll be null for a new chat
                    if (dbMessages != null) addAll(dbMessages)
                    if (userMessageBeingGeneratedFor != null) {
                        add(userMessageBeingGeneratedFor)
                        add(Message.assistant(isLoading = true))
                    }
                }
            }
            return messagesToShow
                .filterIndexed { index, item ->
                    // TODO have some "called tools" message
                    val isNotLast = index != messagesToShow.lastIndex
                    item.role.isUserVisible && !(isNotLast && item.isToolCallsOnly)
                }
                .map { dataStateMessage ->
                    MessageViewState(
                        id = dataStateMessage.timestamp,
                        role = dataStateMessage.role,
                        content = dataStateMessage.visibleText.trim(),
                        // TODO have some "Searching" tools message or something
                        isLoading = dataStateMessage.isLoading || dataStateMessage.isToolCallsOnly,
                    )
                }
        }
    }
}