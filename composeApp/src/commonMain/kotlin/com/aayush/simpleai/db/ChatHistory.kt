package com.aayush.simpleai.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ChatHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val isUser: Boolean,
    val timestamp: Long = 0
)
