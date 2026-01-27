package com.aayush.simpleai.db

import androidx.room.TypeConverter
import com.aayush.simpleai.llm.Message
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromList(value: List<Message>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toList(value: String): List<Message> {
        return Json.decodeFromString(value)
    }
}