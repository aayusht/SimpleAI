package com.aayush.simpleai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.aayush.simpleai.llm.Role
import com.aayush.simpleai.util.DownloadState

data class MainViewState(
    val downloadState: DownloadState,
    val isEngineReady: Boolean,
    val messages: List<Message>,
    val isGenerating: Boolean
) {
    data class Message(
        val key: String,
        val isLoading: Boolean,
        val markdownContent: String,
        val arrangement: Arrangement.Horizontal,
        val clipShape: RoundedCornerShape,
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
            arrangement = if (role == Role.USER) Arrangement.End else Arrangement.Start,
            usePrimaryBackground = role == Role.USER,
            clipShape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (role == Role.USER) 16.dp else 4.dp,
                bottomEnd = if (role == Role.USER) 4.dp else 16.dp
            )
        )
    }
}