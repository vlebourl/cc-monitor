package com.ccmonitor

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.net.ConnectException
import kotlin.math.min
import kotlin.math.pow

@Serializable
data class WebSocketMessage(
    val type: String,
    val payload: Map<String, String> = emptyMap(),
    val timestamp: String
)

@Serializable
data class SessionMessage(
    val sessionId: String,
    val messageType: String,
    val content: String,
    val timestamp: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class SessionOccupiedInfo(
    val sessionId: String,
    val existingViewer: String,
    val canTakeOver: Boolean
)

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED
}

class WebSocketManager(
    private val serverUrl: String = "ws://localhost:8080",
    private val maxRetryAttempts: Int = 10,
    private val baseDelay: Long = 1000, // 1 second base delay
    private val maxDelay: Long = 30000   // 30 seconds max delay
) {
    private val tag = "WebSocketManager"

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private var webSocketSession: WebSocketSession? = null
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = Channel<SessionMessage>(Channel.UNLIMITED)
    private val messageFlow = _messages

    private val _errors = Channel<String>(Channel.UNLIMITED)
    private val errorFlow = _errors

    private val _sessionOccupied = Channel<SessionOccupiedInfo>(Channel.UNLIMITED)
    private val sessionOccupiedFlow = _sessionOccupied

    private var retryCount = 0
    private var apiKey: String? = null
    private var sessionId: String? = null
    private var isAuthenticated = false

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun connect(apiKey: String, sessionId: String? = null) {
        if (_connectionState.value == ConnectionState.CONNECTING ||
            _connectionState.value == ConnectionState.CONNECTED) {
            Log.d(tag, "Already connecting or connected")
            return
        }

        this.apiKey = apiKey
        this.sessionId = sessionId
        this.retryCount = 0

        connectionJob?.cancel()
        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            connectWithRetry()
        }
    }

    fun disconnect() {
        Log.d(tag, "Disconnecting WebSocket")

        connectionJob?.cancel()
        heartbeatJob?.cancel()

        runBlocking {
            try {
                webSocketSession?.close(CloseReason(CloseReason.Codes.NORMAL, "Manual disconnect"))
            } catch (e: Exception) {
                Log.w(tag, "Error closing WebSocket session", e)
            }
        }

        webSocketSession = null
        isAuthenticated = false
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun subscribeToSession(sessionId: String, forceTakeover: Boolean = false) {
        if (!isAuthenticated) {
            Log.w(tag, "Cannot subscribe to session - not authenticated")
            return
        }

        this.sessionId = sessionId

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val subscribeMessage = WebSocketMessage(
                    type = "subscribe",
                    payload = buildMap {
                        put("sessionId", sessionId)
                        if (forceTakeover) put("forceTakeover", "true")
                    },
                    timestamp = System.currentTimeMillis().toString()
                )
                sendMessage(subscribeMessage)
            } catch (e: Exception) {
                Log.e(tag, "Failed to subscribe to session", e)
                _errors.trySend("Failed to subscribe to session: ${e.message}")
            }
        }
    }

    fun unsubscribeFromSession() {
        sessionId?.let { currentSessionId ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val unsubscribeMessage = WebSocketMessage(
                        type = "unsubscribe",
                        payload = mapOf("sessionId" to currentSessionId),
                        timestamp = System.currentTimeMillis().toString()
                    )
                    sendMessage(unsubscribeMessage)
                    sessionId = null
                } catch (e: Exception) {
                    Log.e(tag, "Failed to unsubscribe from session", e)
                }
            }
        }
    }

    suspend fun getMessages() = messageFlow
    suspend fun getErrors() = errorFlow
    suspend fun getSessionOccupied() = sessionOccupiedFlow

    private suspend fun connectWithRetry() {
        while (retryCount < maxRetryAttempts && connectionJob?.isActive == true) {
            try {
                _connectionState.value = if (retryCount == 0) ConnectionState.CONNECTING else ConnectionState.RECONNECTING

                Log.d(tag, "Attempting to connect (attempt ${retryCount + 1}/$maxRetryAttempts)")

                val url = buildConnectionUrl()

                client.webSocket(url) {
                    webSocketSession = this
                    _connectionState.value = ConnectionState.CONNECTED
                    retryCount = 0

                    Log.i(tag, "WebSocket connected successfully")

                    // Start heartbeat
                    startHeartbeat()

                    // Handle authentication if not already done
                    if (!isAuthenticated && apiKey != null) {
                        authenticate()
                    }

                    // Listen for incoming messages
                    handleIncomingMessages()
                }

            } catch (e: Exception) {
                Log.e(tag, "Connection failed (attempt ${retryCount + 1})", e)

                webSocketSession = null
                heartbeatJob?.cancel()

                when (e) {
                    is ConnectException -> {
                        _errors.trySend("Failed to connect to server. Check network connection.")
                    }
                    is CancellationException -> {
                        Log.d(tag, "Connection cancelled")
                        return
                    }
                    else -> {
                        _errors.trySend("Connection error: ${e.message}")
                    }
                }

                retryCount++

                if (retryCount >= maxRetryAttempts) {
                    Log.e(tag, "Max retry attempts reached. Giving up.")
                    _connectionState.value = ConnectionState.FAILED
                    _errors.trySend("Connection failed after $maxRetryAttempts attempts")
                    return
                }

                // Calculate exponential backoff delay
                val delay = calculateBackoffDelay(retryCount)
                Log.d(tag, "Retrying in ${delay}ms...")

                delay(delay)
            }
        }
    }

    private fun calculateBackoffDelay(attempt: Int): Long {
        val exponentialDelay = baseDelay * 2.0.pow(attempt - 1).toLong()
        return min(exponentialDelay, maxDelay)
    }

    private fun buildConnectionUrl(): String {
        val params = mutableListOf<String>()

        apiKey?.let { params.add("apiKey=$it") }
        sessionId?.let { params.add("sessionId=$it") }

        return if (params.isNotEmpty()) {
            "$serverUrl?${params.joinToString("&")}"
        } else {
            serverUrl
        }
    }

    private suspend fun authenticate() {
        apiKey?.let { key ->
            val authMessage = WebSocketMessage(
                type = "authenticate",
                payload = mapOf("apiKey" to key),
                timestamp = System.currentTimeMillis().toString()
            )
            sendMessage(authMessage)
        }
    }

    private suspend fun handleIncomingMessages() {
        try {
            for (frame in webSocketSession!!.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val messageText = frame.readText()
                        Log.d(tag, "Received message: $messageText")

                        try {
                            val message = json.decodeFromString<WebSocketMessage>(messageText)
                            handleWebSocketMessage(message)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to parse incoming message", e)
                            _errors.trySend("Failed to parse message: ${e.message}")
                        }
                    }
                    is Frame.Close -> {
                        val reason = frame.readReason()
                        Log.i(tag, "WebSocket closed: ${reason?.message}")

                        if (reason?.code == CloseReason.Codes.NORMAL) {
                            _connectionState.value = ConnectionState.DISCONNECTED
                            return
                        } else {
                            // Unexpected closure, attempt to reconnect
                            throw Exception("WebSocket closed unexpectedly: ${reason?.message}")
                        }
                    }
                    is Frame.Pong -> {
                        Log.d(tag, "Received pong")
                    }
                    else -> {
                        Log.d(tag, "Received other frame type: ${frame.frameType}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling incoming messages", e)

            if (e !is CancellationException) {
                webSocketSession = null
                isAuthenticated = false

                // Trigger reconnection if we haven't exceeded max attempts
                if (retryCount < maxRetryAttempts) {
                    Log.i(tag, "Connection lost, attempting to reconnect...")
                    _connectionState.value = ConnectionState.RECONNECTING
                    delay(calculateBackoffDelay(++retryCount))

                    if (connectionJob?.isActive == true) {
                        connectWithRetry()
                    }
                } else {
                    _connectionState.value = ConnectionState.FAILED
                    _errors.trySend("Connection lost and max retries exceeded")
                }
            }
        }
    }

    private suspend fun handleWebSocketMessage(message: WebSocketMessage) {
        when (message.type) {
            "connected" -> {
                Log.i(tag, "Connected to server")
            }
            "authenticated" -> {
                isAuthenticated = true
                Log.i(tag, "Authentication successful")

                // Subscribe to session if specified
                sessionId?.let { subscribeToSession(it) }
            }
            "authentication_failed" -> {
                Log.e(tag, "Authentication failed")
                _errors.trySend("Authentication failed: Invalid API key")
                disconnect()
            }
            "subscribed" -> {
                val sessionId = message.payload["sessionId"]
                val tookOver = message.payload["tookOver"]?.toBoolean() ?: false
                Log.i(tag, "Subscribed to session: $sessionId${if (tookOver) " (took over)" else ""}")
            }
            "unsubscribed" -> {
                Log.i(tag, "Unsubscribed from session: ${message.payload["sessionId"]}")
            }
            "session_occupied" -> {
                val sessionId = message.payload["sessionId"] ?: "unknown"
                val existingViewer = message.payload["existingViewer"] ?: "Unknown device"
                val canTakeOver = message.payload["canTakeOver"]?.toBoolean() ?: false

                Log.w(tag, "Session $sessionId is occupied by $existingViewer")
                _errors.trySend("Session is being viewed by $existingViewer. You can force takeover if needed.")

                // Emit special event for session occupied
                _sessionOccupied.trySend(SessionOccupiedInfo(sessionId, existingViewer, canTakeOver))
            }
            "session_taken_over" -> {
                val sessionId = message.payload["sessionId"] ?: "unknown"
                val newViewer = message.payload["newViewer"] ?: "Another device"

                Log.w(tag, "Session $sessionId viewing taken over by $newViewer")
                _errors.trySend("Session viewing has been taken over by another device")

                // Clear current session since it was taken over
                this.sessionId = null
            }
            "session_message" -> {
                try {
                    // Parse the session message from the payload
                    val sessionMessageJson = message.payload["sessionMessage"] ?: message.payload.toString()
                    val sessionMessage = json.decodeFromString<SessionMessage>(sessionMessageJson)
                    _messages.trySend(sessionMessage)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse session message", e)
                    _errors.trySend("Failed to parse session message: ${e.message}")
                }
            }
            "error" -> {
                val error = message.payload["error"] ?: "Unknown error"
                Log.e(tag, "Server error: $error")
                _errors.trySend("Server error: $error")
            }
            "pong" -> {
                Log.d(tag, "Received pong response")
            }
            else -> {
                Log.w(tag, "Unknown message type: ${message.type}")
            }
        }
    }

    private suspend fun sendMessage(message: WebSocketMessage) {
        try {
            val messageJson = json.encodeToString(message)
            webSocketSession?.send(Frame.Text(messageJson))
            Log.d(tag, "Sent message: $messageJson")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send message", e)
            throw e
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (webSocketSession != null && connectionJob?.isActive == true) {
                try {
                    delay(30000) // Send ping every 30 seconds

                    val pingMessage = WebSocketMessage(
                        type = "ping",
                        payload = emptyMap(),
                        timestamp = System.currentTimeMillis().toString()
                    )
                    sendMessage(pingMessage)

                } catch (e: Exception) {
                    Log.e(tag, "Failed to send heartbeat", e)
                    break
                }
            }
        }
    }

    fun getConnectionStats(): Map<String, Any> {
        return mapOf(
            "state" to _connectionState.value.name,
            "retryCount" to retryCount,
            "isAuthenticated" to isAuthenticated,
            "sessionId" to (sessionId ?: "none"),
            "hasActiveSession" to (webSocketSession != null)
        )
    }

    fun cleanup() {
        disconnect()
        client.close()
    }
}