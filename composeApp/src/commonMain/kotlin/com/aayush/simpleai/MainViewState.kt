package com.aayush.simpleai

import androidx.compose.runtime.Immutable
import com.aayush.simpleai.llm.Role
import com.aayush.simpleai.util.DownloadState

data class MainViewState(
    val downloadState: DownloadState,
    val isEngineReady: Boolean,
    val messages: List<MessageViewState>,
    val isGenerating: Boolean,
    val remainingStorage: Long?,
    val notEnoughStorage: NotEnoughBytes?,
    val notEnoughMemory: NotEnoughBytes?,
) {

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

    companion object {
        private fun formatOneDecimalPoint(float: Float): String =
            "${float.toInt()}.${(float * 10).toInt() % 10}"

        fun getMessageViewStates(messages: List<ChatMessage>): List<MessageViewState> {
            return messages
                .filterIndexed { index, item ->
                    // TODO use this as like a searching state
                    val isLast = index == messages.lastIndex
                    val toolsOnly = item.visibleText.isBlank() && item.fullText.isNotBlank()
                    item.role.isUserVisible && !(isLast && toolsOnly)
                }
                .map { dataStateMessage ->
                    MainViewState.MessageViewState(
                        id = dataStateMessage.timestamp,
                        role = dataStateMessage.role,
                        content = dataStateMessage.visibleText.trim(),
                        isLoading = dataStateMessage.isLoading,
                    )
                }
        }
    }
}