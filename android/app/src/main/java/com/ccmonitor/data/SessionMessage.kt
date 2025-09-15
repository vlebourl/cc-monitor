package com.ccmonitor.data

import kotlinx.serialization.Serializable

@Serializable
data class SessionMessage(
    val type: String,
    val timestamp: String,
    val sessionId: String,
    val data: MessageData
) {
    @Serializable
    data class MessageData(
        val messageType: String,
        val content: String,
        val parentUuid: String = "",
        val historical: Boolean? = null
    )
}

@Serializable
data class SessionState(
    val active: Boolean,
    val sessionId: String,
    val participants: Int = 0,
    val projectPath: String = "",
    val lastActivity: String = ""
)

@Serializable
data class AuthMessage(
    val type: String,
    val sessionId: String? = null,
    val token: String? = null,
    val success: Boolean? = null,
    val error: String? = null
)

@Serializable
data class ConnectionStatus(
    val connected: Boolean,
    val sessionId: String? = null,
    val error: String? = null,
    val timestamp: String = ""
)