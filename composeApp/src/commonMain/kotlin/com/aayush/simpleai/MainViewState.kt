package com.aayush.simpleai

import androidx.compose.runtime.Immutable
import com.aayush.simpleai.llm.Role
import com.aayush.simpleai.util.DownloadState

data class MainViewState(
    val downloadState: DownloadState,
    val isEngineReady: Boolean,
    val messages: List<Message>,
    val isGenerating: Boolean
) {
    @Immutable
    data class Message(
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
}