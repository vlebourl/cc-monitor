package com.ccmonitor.database.dao

import androidx.room.*
import com.ccmonitor.database.entities.SessionCache
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionCacheDao {
    @Query("SELECT * FROM session_cache ORDER BY lastActivity DESC")
    fun getAllSessions(): Flow<List<SessionCache>>

    @Query("SELECT * FROM session_cache WHERE isActive = 1 ORDER BY lastActivity DESC")
    fun getActiveSessions(): Flow<List<SessionCache>>

    @Query("SELECT * FROM session_cache WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): SessionCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionCache)

    @Update
    suspend fun updateSession(session: SessionCache)

    @Query("UPDATE session_cache SET isActive = :isActive, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateSessionActivity(sessionId: String, isActive: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE session_cache SET messageCount = :messageCount, lastMessageType = :lastMessageType, lastMessageContent = :lastMessageContent, lastActivity = :lastActivity, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateSessionStats(
        sessionId: String,
        messageCount: Int,
        lastMessageType: String,
        lastMessageContent: String,
        lastActivity: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM session_cache WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM session_cache WHERE updatedAt < :olderThan")
    suspend fun deleteOldSessions(olderThan: Long)

    @Query("SELECT COUNT(*) FROM session_cache")
    suspend fun getSessionCount(): Int

    @Query("SELECT * FROM session_cache WHERE projectPath LIKE '%' || :projectName || '%' ORDER BY lastActivity DESC")
    suspend fun getSessionsForProject(projectName: String): List<SessionCache>
}