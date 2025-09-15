package com.ccmonitor.repository

import android.content.Context
import com.ccmonitor.data.SessionMessage
import com.ccmonitor.database.ClaudeCodeDatabase
import com.ccmonitor.database.entities.CachedMessage
import com.ccmonitor.database.entities.SessionCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class MessageCacheRepository private constructor(context: Context) {
    private val database = ClaudeCodeDatabase.getDatabase(context)
    private val messageDao = database.cachedMessageDao()
    private val sessionDao = database.sessionCacheDao()

    companion object {
        @Volatile
        private var INSTANCE: MessageCacheRepository? = null

        fun getInstance(context: Context): MessageCacheRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = MessageCacheRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    // Message caching operations
    suspend fun cacheMessage(message: SessionMessage) {
        val cachedMessage = CachedMessage(
            sessionId = message.sessionId,
            messageType = message.data.messageType,
            content = message.data.content,
            timestamp = message.timestamp,
            parentUuid = message.data.parentUuid,
            isHistorical = message.data.historical ?: false,
            projectPath = extractProjectPath(message.sessionId)
        )

        messageDao.insertMessage(cachedMessage)

        // Update session cache
        updateSessionCache(message)
    }

    suspend fun getCachedMessages(sessionId: String): Flow<List<SessionMessage>> {
        return messageDao.getMessagesForSession(sessionId).map { cachedMessages ->
            cachedMessages.map { cached ->
                SessionMessage(
                    type = "message",
                    timestamp = cached.timestamp,
                    sessionId = cached.sessionId,
                    data = SessionMessage.MessageData(
                        messageType = cached.messageType,
                        content = cached.content,
                        parentUuid = cached.parentUuid,
                        historical = cached.isHistorical
                    )
                )
            }
        }
    }

    suspend fun getRecentCachedMessages(sessionId: String, limit: Int = 50): List<SessionMessage> {
        val cachedMessages = messageDao.getRecentMessagesForSession(sessionId, limit)
        return cachedMessages.reversed().map { cached ->
            SessionMessage(
                type = "message",
                timestamp = cached.timestamp,
                sessionId = cached.sessionId,
                data = SessionMessage.MessageData(
                    messageType = cached.messageType,
                    content = cached.content,
                    parentUuid = cached.parentUuid,
                    historical = cached.isHistorical
                )
            )
        }
    }

    suspend fun clearSessionCache(sessionId: String) {
        messageDao.clearSessionMessages(sessionId)
        sessionDao.deleteSession(sessionId)
    }

    suspend fun searchMessages(sessionId: String, query: String): List<SessionMessage> {
        val cachedMessages = messageDao.searchMessagesInSession(sessionId, query)
        return cachedMessages.map { cached ->
            SessionMessage(
                type = "message",
                timestamp = cached.timestamp,
                sessionId = cached.sessionId,
                data = SessionMessage.MessageData(
                    messageType = cached.messageType,
                    content = cached.content,
                    parentUuid = cached.parentUuid,
                    historical = cached.isHistorical
                )
            )
        }
    }

    // Session cache operations
    private suspend fun updateSessionCache(message: SessionMessage) {
        val existingSession = sessionDao.getSession(message.sessionId)
        val messageCount = messageDao.getMessageCount(message.sessionId)

        if (existingSession == null) {
            // Create new session cache entry
            val sessionCache = SessionCache(
                sessionId = message.sessionId,
                projectPath = extractProjectPath(message.sessionId),
                filePath = "", // This would be set from session discovery
                isActive = true,
                lastActivity = message.timestamp,
                messageCount = messageCount,
                lastMessageType = message.data.messageType,
                lastMessageContent = message.data.content.take(100) // Truncate for storage
            )
            sessionDao.insertSession(sessionCache)
        } else {
            // Update existing session
            sessionDao.updateSessionStats(
                sessionId = message.sessionId,
                messageCount = messageCount,
                lastMessageType = message.data.messageType,
                lastMessageContent = message.data.content.take(100),
                lastActivity = message.timestamp
            )
        }
    }

    suspend fun updateSessionActivity(sessionId: String, isActive: Boolean) {
        sessionDao.updateSessionActivity(sessionId, isActive)
    }

    fun getAllCachedSessions(): Flow<List<SessionCache>> {
        return sessionDao.getAllSessions()
    }

    fun getActiveCachedSessions(): Flow<List<SessionCache>> {
        return sessionDao.getActiveSessions()
    }

    suspend fun getCachedSession(sessionId: String): SessionCache? {
        return sessionDao.getSession(sessionId)
    }

    // Cleanup operations
    suspend fun cleanupOldData(maxAgeHours: Long = 24 * 7) { // Default: 1 week
        val cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(maxAgeHours)
        messageDao.deleteOldMessages(cutoffTime)
        sessionDao.deleteOldSessions(cutoffTime)
    }

    // Utility functions
    private fun extractProjectPath(sessionId: String): String {
        // This is a simplified version - in reality, we'd get this from session discovery
        return "Unknown Project"
    }

    suspend fun getCacheStats(): CacheStats {
        val totalMessages = messageDao.getMessageCount("")
        val totalSessions = sessionDao.getSessionCount()
        return CacheStats(totalMessages, totalSessions)
    }

    data class CacheStats(
        val totalMessages: Int,
        val totalSessions: Int
    )
}