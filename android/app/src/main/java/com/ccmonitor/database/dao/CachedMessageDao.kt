package com.ccmonitor.database.dao

import androidx.room.*
import com.ccmonitor.database.entities.CachedMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedMessageDao {
    @Query("SELECT * FROM cached_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<CachedMessage>>

    @Query("SELECT * FROM cached_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessagesForSessionPaged(sessionId: String, limit: Int, offset: Int): List<CachedMessage>

    @Query("SELECT * FROM cached_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForSession(sessionId: String, limit: Int = 50): List<CachedMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: CachedMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<CachedMessage>)

    @Query("DELETE FROM cached_messages WHERE sessionId = :sessionId")
    suspend fun clearSessionMessages(sessionId: String)

    @Query("DELETE FROM cached_messages WHERE cachedAt < :olderThan")
    suspend fun deleteOldMessages(olderThan: Long)

    @Query("SELECT COUNT(*) FROM cached_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int

    @Query("SELECT * FROM cached_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(sessionId: String): CachedMessage?

    // Search functionality
    @Query("SELECT * FROM cached_messages WHERE sessionId = :sessionId AND content LIKE '%' || :searchQuery || '%' ORDER BY timestamp ASC")
    suspend fun searchMessagesInSession(sessionId: String, searchQuery: String): List<CachedMessage>

    @Query("SELECT DISTINCT sessionId FROM cached_messages WHERE content LIKE '%' || :searchQuery || '%'")
    suspend fun getSessionsContainingText(searchQuery: String): List<String>
}