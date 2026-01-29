package com.aayush.simpleai.db

import androidx.room.TypeConverter
import com.aayush.simpleai.llm.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun fromList(value: List<Message>): String {
        return Json.encodeToString(value)
    }

    fun toList(value: String): List<Message> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromMessageList(value: List<Message>): String = json.encodeToString(value)

    @TypeConverter
    fun toMessageList(value: String): List<Message> = json.decodeFromString(value)
}
