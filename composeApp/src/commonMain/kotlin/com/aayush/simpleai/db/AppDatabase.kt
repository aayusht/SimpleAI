package com.aayush.simpleai.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

@Database(entities = [ChatHistory::class], version = 1)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@Dao
interface ChatHistoryDao {
    @Insert
    suspend fun insert(chatHistory: ChatHistory)

    @Query("SELECT * FROM ChatHistory ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ChatHistory>>

    @Query("DELETE FROM ChatHistory")
    suspend fun deleteAll()
}
