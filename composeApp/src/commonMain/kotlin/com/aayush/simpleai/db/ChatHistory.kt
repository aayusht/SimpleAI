package com.aayush.simpleai.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aayush.simpleai.llm.Message
import com.aayush.simpleai.llm.Role
import kotlinx.serialization.json.Json

@Entity
data class ChatHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messages: List<Message>,
    val timestamp: Long = 0,
) {
    /**
     * Returns the longest visible text from the first 5 user-visible messages.
     */
    fun getPreviewText(): String {
        return messages
            .filter { it.role == Role.USER && !it.isLoading }
            .take(5)
            .maxByOrNull { it.visibleText.length }
            ?.visibleText
            ?.trim()
            ?: ""
    }

    companion object {
        fun from(messages: List<Message>): ChatHistory {
            val timestamp = messages.firstOrNull()?.timestamp ?: 0L
            return ChatHistory(messages = messages, timestamp = timestamp)
        }

        /**
         * Encodes messages to JSON string for use with the DAO update method.
         */
        fun encodeMessages(messages: List<Message>): String {
            return Json.encodeToString(messages)
        }
    }
}
