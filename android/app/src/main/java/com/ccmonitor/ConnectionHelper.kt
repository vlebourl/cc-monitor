package com.ccmonitor

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.ccmonitor.repository.MessageCacheRepository
import com.ccmonitor.repository.SettingsRepository
import com.ccmonitor.data.SessionMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ConnectionHelper private constructor(
    private val context: Context,
    private val customServerUrl: String? = null
) {
    private val tag = "ConnectionHelper"

    private var webSocketManager: WebSocketManager? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val messageCache = MessageCacheRepository.getInstance(context)
    private val settingsRepository = SettingsRepository.getInstance(context)

    private val serverUrl: String
        get() = customServerUrl ?: settingsRepository.getServerUrl()

    private val preferences: SharedPreferences =
        context.getSharedPreferences("cc_monitor_prefs", Context.MODE_PRIVATE)

    // Connection state flow
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Messages flow
    private val _messages = MutableSharedFlow<SessionMessage>(
        replay = 0,
        extraBufferCapacity = 1000
    )
    val messages: SharedFlow<SessionMessage> = _messages.asSharedFlow()

    // Errors flow
    private val _errors = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 100
    )
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    // Session occupied flow
    private val _sessionOccupied = MutableSharedFlow<SessionOccupiedInfo>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val sessionOccupied: SharedFlow<SessionOccupiedInfo> = _sessionOccupied.asSharedFlow()

    // Current session info
    private val _currentSession = MutableStateFlow<String?>(null)
    val currentSession: StateFlow<String?> = _currentSession.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: ConnectionHelper? = null

        fun getInstance(context: Context, serverUrl: String? = null): ConnectionHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionHelper(context.applicationContext, serverUrl).also { INSTANCE = it }
            }
        }
    }

    init {
        // Try to restore previous connection if API key exists
        restoreConnectionIfPossible()
    }

    fun connect(apiKey: String, sessionId: String? = null) {
        Log.d(tag, "Connecting with apiKey and sessionId: $sessionId")

        // Save API key for automatic reconnection
        saveApiKey(apiKey)

        // Clean up existing connection
        disconnectInternal()

        // Create new WebSocket manager
        webSocketManager = WebSocketManager(serverUrl).also { manager ->
            // Start collecting state changes
            scope.launch {
                manager.connectionState.collect { state ->
                    _connectionState.value = state
                    Log.d(tag, "Connection state changed to: $state")

                    if (state == ConnectionState.FAILED) {
                        _errors.emit("Connection failed after multiple attempts")
                    }
                }
            }

            // Start collecting messages
            scope.launch {
                manager.getMessages().collect { message ->
                    // Cache the message
                    try {
                        messageCache.cacheMessage(message)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to cache message: ${e.message}", e)
                    }

                    _messages.emit(message)
                    Log.d(tag, "Received session message: ${message.data.messageType}")
                }
            }

            // Start collecting errors
            scope.launch {
                manager.getErrors().collect { error ->
                    _errors.emit(error)
                    Log.e(tag, "WebSocket error: $error")
                }
            }

            // Start collecting session occupied events
            scope.launch {
                manager.getSessionOccupied().collect { occupiedInfo ->
                    _sessionOccupied.emit(occupiedInfo)
                    Log.w(tag, "Session occupied: ${occupiedInfo.sessionId}")
                }
            }

            // Connect
            manager.connect(apiKey, sessionId)
            _currentSession.value = sessionId
        }
    }

    fun disconnect() {
        Log.d(tag, "Disconnecting")
        disconnectInternal()
        clearSavedCredentials()
        _currentSession.value = null
    }

    fun subscribeToSession(sessionId: String, forceTakeover: Boolean = false) {
        Log.d(tag, "Subscribing to session: $sessionId${if (forceTakeover) " (force takeover)" else ""}")
        webSocketManager?.subscribeToSession(sessionId, forceTakeover)
        _currentSession.value = sessionId
        saveCurrentSession(sessionId)
    }

    fun unsubscribeFromCurrentSession() {
        Log.d(tag, "Unsubscribing from current session")
        webSocketManager?.unsubscribeFromSession()
        _currentSession.value = null
        clearCurrentSession()
    }

    fun reconnect() {
        Log.d(tag, "Reconnecting...")

        val savedApiKey = getSavedApiKey()
        val savedSessionId = getCurrentSession()

        if (savedApiKey != null) {
            connect(savedApiKey, savedSessionId)
        } else {
            Log.w(tag, "Cannot reconnect - no saved API key")
            _errors.tryEmit("Cannot reconnect - please authenticate again")
        }
    }

    fun isConnected(): Boolean {
        return _connectionState.value == ConnectionState.CONNECTED
    }

    fun isAuthenticated(): Boolean {
        return webSocketManager?.getConnectionStats()?.get("isAuthenticated") as? Boolean ?: false
    }

    fun getCurrentSessionId(): String? {
        return _currentSession.value
    }

    fun getConnectionStats(): Map<String, Any> {
        val managerStats = webSocketManager?.getConnectionStats() ?: emptyMap()
        return managerStats + mapOf(
            "hasSavedCredentials" to (getSavedApiKey() != null),
            "currentSession" to (getCurrentSessionId() ?: "none")
        )
    }

    // Message caching methods
    suspend fun getCachedMessages(sessionId: String): Flow<List<SessionMessage>> {
        return messageCache.getCachedMessages(sessionId)
    }

    suspend fun getRecentCachedMessages(sessionId: String, limit: Int = 50): List<SessionMessage> {
        return messageCache.getRecentCachedMessages(sessionId, limit)
    }

    suspend fun searchCachedMessages(sessionId: String, query: String): List<SessionMessage> {
        return messageCache.searchMessages(sessionId, query)
    }

    suspend fun clearSessionCache(sessionId: String) {
        messageCache.clearSessionCache(sessionId)
    }

    suspend fun performCacheCleanup(maxAgeHours: Long = 24 * 7) {
        messageCache.cleanupOldData(maxAgeHours)
    }

    suspend fun getCacheStats(): MessageCacheRepository.CacheStats {
        return messageCache.getCacheStats()
    }

    fun cleanup() {
        Log.d(tag, "Cleaning up ConnectionHelper")
        scope.cancel()
        disconnectInternal()
        INSTANCE = null
    }

    private fun disconnectInternal() {
        webSocketManager?.disconnect()
        webSocketManager?.cleanup()
        webSocketManager = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun restoreConnectionIfPossible() {
        val savedApiKey = getSavedApiKey()
        val savedSessionId = getCurrentSession()

        if (savedApiKey != null) {
            Log.d(tag, "Found saved credentials, attempting to restore connection")
            connect(savedApiKey, savedSessionId)
        }
    }

    // SharedPreferences helpers
    private fun saveApiKey(apiKey: String) {
        preferences.edit()
            .putString("api_key", apiKey)
            .apply()
    }

    private fun getSavedApiKey(): String? {
        return preferences.getString("api_key", null)
    }

    private fun saveCurrentSession(sessionId: String) {
        preferences.edit()
            .putString("current_session", sessionId)
            .apply()
    }

    private fun getCurrentSession(): String? {
        return preferences.getString("current_session", null)
    }

    private fun clearCurrentSession() {
        preferences.edit()
            .remove("current_session")
            .apply()
    }

    private fun clearSavedCredentials() {
        preferences.edit()
            .remove("api_key")
            .remove("current_session")
            .apply()
    }

    // Network monitoring helpers
    fun startNetworkMonitoring() {
        // TODO: Implement network state monitoring
        // This would monitor network connectivity and trigger reconnection
        // when network becomes available again
    }

    fun stopNetworkMonitoring() {
        // TODO: Stop network monitoring
    }

    // Connection health monitoring
    private fun startConnectionHealthCheck() {
        scope.launch {
            while (isActive) {
                delay(60000) // Check every minute

                if (_connectionState.value == ConnectionState.CONNECTED) {
                    val stats = getConnectionStats()
                    Log.d(tag, "Connection health check: $stats")

                    // If connection seems stale, trigger reconnection
                    if (!isAuthenticated() && getSavedApiKey() != null) {
                        Log.w(tag, "Connection appears stale, reconnecting...")
                        reconnect()
                    }
                }
            }
        }
    }

    // Auto-reconnection with backoff
    fun enableAutoReconnect(enable: Boolean = true) {
        if (enable) {
            scope.launch {
                _connectionState.collect { state ->
                    when (state) {
                        ConnectionState.FAILED, ConnectionState.DISCONNECTED -> {
                            if (getSavedApiKey() != null) {
                                delay(5000) // Wait 5 seconds before auto-reconnect

                                if (_connectionState.value != ConnectionState.CONNECTING &&
                                    _connectionState.value != ConnectionState.CONNECTED) {
                                    Log.i(tag, "Auto-reconnecting...")
                                    reconnect()
                                }
                            }
                        }
                        else -> { /* Do nothing */ }
                    }
                }
            }
        }
    }
}