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

expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

@Dao
interface ChatHistoryDao {
    @Insert
    suspend fun insert(chatHistory: ChatHistory): Long

    @Query("UPDATE ChatHistory SET messages = :messages, timestamp = :timestamp WHERE id = :id")
    suspend fun update(id: Long, messages: String, timestamp: Long)

    @Query("SELECT * FROM ChatHistory ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ChatHistory>>

    @Query("SELECT * FROM ChatHistory WHERE id = :id")
    suspend fun getById(id: Long): ChatHistory?

    @Query("DELETE FROM ChatHistory WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ChatHistory")
    suspend fun deleteAll()
}
