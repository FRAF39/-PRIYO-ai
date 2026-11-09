package com.priyo.codex.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages", indices = [Index("createdAt")])
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt DESC") fun observeAll(): Flow<List<Message>>
    @Insert suspend fun insert(message: Message)
    @Query("DELETE FROM messages") suspend fun clear()
}

@Database(entities = [Message::class], version = 1, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    companion object {
        @Volatile private var INSTANCE: ChatDatabase? = null
        fun get(context: Context): ChatDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, ChatDatabase::class.java, "priyo_codex.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
