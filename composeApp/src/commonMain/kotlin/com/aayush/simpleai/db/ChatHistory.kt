package com.aayush.simpleai.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aayush.simpleai.llm.Message

@Entity
data class ChatHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messages: List<Message>,
    val timestamp: Long = 0,
) {
    companion object {

        fun from(messages: List<Message>): ChatHistory {
            return ChatHistory(messages = messages, timestamp = messages.minOf { it.timestamp })
        }
    }
}
