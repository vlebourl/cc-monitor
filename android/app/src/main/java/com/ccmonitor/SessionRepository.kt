package com.ccmonitor

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SessionInfo(
    val sessionId: String,
    val filePath: String,
    val isActive: Boolean,
    val stats: SessionStats?
)

@Serializable
data class SessionStats(
    val messageCount: Int,
    val lastActivity: String,
    val fileSize: Long,
    val createdAt: String
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionInfo>,
    val totalSessions: Int,
    val activeSessions: Int,
    val timestamp: String
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null,
    val timestamp: String
)

class SessionRepository private constructor(
    private val context: Context,
    private val baseUrl: String = "http://localhost:3000"
) {
    private val tag = "SessionRepository"

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = false
            })
        }
    }

    private val authRepository = AuthRepository.getInstance(context)

    companion object {
        @Volatile
        private var INSTANCE: SessionRepository? = null

        fun getInstance(context: Context, baseUrl: String = "http://localhost:3000"): SessionRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionRepository(context.applicationContext, baseUrl).also { INSTANCE = it }
            }
        }
    }

    suspend fun getAllSessions(): Result<List<SessionInfo>> {
        val apiKey = authRepository.getApiKey()
        if (apiKey == null) {
            return Result.failure(Exception("No API key available"))
        }

        return try {
            Log.d(tag, "Fetching all sessions")

            val response: ApiResponse<SessionsResponse> = client.get("$baseUrl/api/sessions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
            }.body()

            if (response.success && response.data != null) {
                Log.i(tag, "Retrieved ${response.data.sessions.size} sessions")
                Result.success(response.data.sessions)
            } else {
                val error = response.error ?: response.message ?: "Failed to fetch sessions"
                Log.e(tag, "Failed to fetch sessions: $error")
                Result.failure(Exception(error))
            }

        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch sessions", e)
            Result.failure(e)
        }
    }

    suspend fun getActiveSessionsOnly(): Result<List<SessionInfo>> {
        return getAllSessions().map { sessions ->
            sessions.filter { it.isActive }
        }
    }

    suspend fun getSessionById(sessionId: String): Result<SessionInfo?> {
        return getAllSessions().map { sessions ->
            sessions.find { it.sessionId == sessionId }
        }
    }

    suspend fun getRecentSessions(limit: Int = 10): Result<List<SessionInfo>> {
        return getAllSessions().map { sessions ->
            sessions.sortedByDescending { session ->
                session.stats?.lastActivity ?: session.stats?.createdAt ?: ""
            }.take(limit)
        }
    }

    suspend fun searchSessions(query: String): Result<List<SessionInfo>> {
        return getAllSessions().map { sessions ->
            sessions.filter { session ->
                session.sessionId.contains(query, ignoreCase = true) ||
                session.filePath.contains(query, ignoreCase = true)
            }
        }
    }

    // Local session management for favorites/bookmarks
    fun markSessionAsFavorite(sessionId: String) {
        val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        val favorites = prefs.getStringSet("favorite_sessions", emptySet())?.toMutableSet() ?: mutableSetOf()
        favorites.add(sessionId)
        prefs.edit().putStringSet("favorite_sessions", favorites).apply()
        Log.d(tag, "Marked session $sessionId as favorite")
    }

    fun removeFavoriteSession(sessionId: String) {
        val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        val favorites = prefs.getStringSet("favorite_sessions", emptySet())?.toMutableSet() ?: mutableSetOf()
        favorites.remove(sessionId)
        prefs.edit().putStringSet("favorite_sessions", favorites).apply()
        Log.d(tag, "Removed session $sessionId from favorites")
    }

    fun getFavoriteSessions(): Set<String> {
        val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("favorite_sessions", emptySet()) ?: emptySet()
    }

    fun isSessionFavorite(sessionId: String): Boolean {
        return getFavoriteSessions().contains(sessionId)
    }

    // Recently viewed sessions
    fun addToRecentlyViewed(sessionId: String) {
        val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        val recent = prefs.getStringSet("recent_sessions", emptySet())?.toMutableSet() ?: mutableSetOf()

        // Remove if already exists to move to front
        recent.remove(sessionId)
        recent.add(sessionId)

        // Keep only last 20 sessions
        if (recent.size > 20) {
            val sortedRecent = recent.toList().takeLast(20).toSet()
            prefs.edit().putStringSet("recent_sessions", sortedRecent).apply()
        } else {
            prefs.edit().putStringSet("recent_sessions", recent).apply()
        }

        Log.d(tag, "Added session $sessionId to recently viewed")
    }

    fun getRecentlyViewedSessionIds(): List<String> {
        val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("recent_sessions", emptySet())?.toList()?.reversed() ?: emptyList()
    }

    suspend fun getRecentlyViewedSessions(): Result<List<SessionInfo>> {
        val recentIds = getRecentlyViewedSessionIds()
        if (recentIds.isEmpty()) {
            return Result.success(emptyList())
        }

        return getAllSessions().map { allSessions ->
            recentIds.mapNotNull { sessionId ->
                allSessions.find { it.sessionId == sessionId }
            }
        }
    }

    // Session grouping by project/path
    suspend fun getSessionsByProject(): Result<Map<String, List<SessionInfo>>> {
        return getAllSessions().map { sessions ->
            sessions.groupBy { session ->
                // Extract project name from file path
                // Expected path format: ~/.claude/projects/<project-name>/<session-id>.jsonl
                val pathParts = session.filePath.split("/")
                val projectsIndex = pathParts.indexOfFirst { it == "projects" }
                if (projectsIndex != -1 && projectsIndex + 1 < pathParts.size) {
                    pathParts[projectsIndex + 1]
                } else {
                    "Unknown Project"
                }
            }
        }
    }

    // Utility methods
    fun formatSessionId(sessionId: String): String {
        return if (sessionId.length > 8) {
            "${sessionId.take(8)}..."
        } else {
            sessionId
        }
    }

    fun getProjectName(filePath: String): String {
        val pathParts = filePath.split("/")
        val projectsIndex = pathParts.indexOfFirst { it == "projects" }
        return if (projectsIndex != -1 && projectsIndex + 1 < pathParts.size) {
            pathParts[projectsIndex + 1]
        } else {
            "Unknown Project"
        }
    }

    fun isSessionActive(session: SessionInfo): Boolean {
        return session.isActive
    }

    fun getSessionDisplayName(session: SessionInfo): String {
        val projectName = getProjectName(session.filePath)
        val shortId = formatSessionId(session.sessionId)
        return "$projectName ($shortId)"
    }

    fun clearCache() {
        // Clear any cached session data if implemented
        Log.d(tag, "Session cache cleared")
    }

    fun cleanup() {
        client.close()
        INSTANCE = null
    }
}