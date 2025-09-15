package com.ccmonitor.repository

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository private constructor(private val context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("cc_monitor_settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_SERVER_URL = "ws://localhost:8080"

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun getServerUrl(): String {
        return preferences.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun saveServerUrl(url: String) {
        val cleanUrl = url.trim()
        if (!isValidWebSocketUrl(cleanUrl)) {
            throw IllegalArgumentException("Invalid WebSocket URL format")
        }

        preferences.edit()
            .putString(KEY_SERVER_URL, cleanUrl)
            .apply()
    }

    fun resetToDefaults() {
        preferences.edit()
            .remove(KEY_SERVER_URL)
            .apply()
    }

    fun hasCustomServerUrl(): Boolean {
        return preferences.contains(KEY_SERVER_URL) &&
               getServerUrl() != DEFAULT_SERVER_URL
    }

    private fun isValidWebSocketUrl(url: String): Boolean {
        if (url.isEmpty()) return false

        return try {
            val uri = java.net.URI(url)

            // Must be ws or wss protocol
            if (uri.scheme != "ws" && uri.scheme != "wss") return false

            // Must have a host (IP address or domain name)
            val host = uri.host ?: return false

            // Validate host format (IP or domain)
            if (!isValidHost(host)) return false

            // Port validation (optional, defaults to 80/443)
            val port = uri.port
            if (port != -1 && (port < 1 || port > 65535)) return false

            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidHost(host: String): Boolean {
        // Check if it's an IP address
        if (isValidIpAddress(host)) return true

        // Check if it's a valid domain name
        if (isValidDomainName(host)) return true

        return false
    }

    private fun isValidIpAddress(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false

            parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidDomainName(domain: String): Boolean {
        if (domain.isEmpty() || domain.length > 253) return false

        // Basic domain name validation
        val domainRegex = Regex("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)*(([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?))$")
        return domainRegex.matches(domain)
    }

    // Get different URL formats
    fun getHttpUrl(): String {
        val wsUrl = getServerUrl()
        return wsUrl.replace("ws://", "http://").replace("wss://", "https://")
    }

    fun getHealthCheckUrl(): String {
        val httpUrl = getHttpUrl()
        val uri = java.net.URI(httpUrl)
        val healthPort = if (uri.port == 8080) 3000 else uri.port
        return "http://${uri.host}:${healthPort}/health"
    }

    fun getAutoDetectUrls(input: String): List<String> {
        return getConnectionCandidates(input)
    }

    // Server URL examples and validation
    fun getExampleUrls(): List<String> {
        return listOf(
            "192.168.1.100",            // Simple IP (auto-detect)
            "server.local",              // Simple DNS (auto-detect)
            "myserver.com",              // Simple domain (auto-detect)
            "ws://192.168.1.100:8080",  // Full WebSocket URL
            "wss://secure.server.com:8080" // Secure WebSocket
        )
    }

    fun validateAndFormatUrl(input: String): String {
        val cleanInput = input.trim()

        // If it already has protocol, validate and return
        if (cleanInput.startsWith("ws://") || cleanInput.startsWith("wss://")) {
            val uri = java.net.URI(cleanInput)
            return if (uri.port == -1) {
                "$cleanInput:8080"
            } else {
                cleanInput
            }
        }

        // Auto-detect format: just hostname/IP, add default protocol and port
        return "ws://$cleanInput:8080"
    }

    fun getConnectionCandidates(input: String): List<String> {
        val cleanInput = input.trim()

        // If already a full WebSocket URL, return as-is
        if (cleanInput.startsWith("ws://") || cleanInput.startsWith("wss://")) {
            return listOf(cleanInput)
        }

        // For bare hostname/IP, generate multiple candidates to try
        val candidates = mutableListOf<String>()

        // Add port if not specified
        val hostWithPort = if (cleanInput.contains(":")) cleanInput else "$cleanInput:8080"
        val hostOnly = cleanInput.split(":")[0]

        // Primary candidates (most common)
        candidates.add("ws://$hostWithPort")

        // Alternative ports to try
        candidates.add("ws://$hostOnly:8080")  // Standard WebSocket
        candidates.add("ws://$hostOnly:3000")  // Alternative HTTP->WS upgrade
        candidates.add("wss://$hostOnly:8080") // Secure WebSocket
        candidates.add("wss://$hostOnly:443")  // HTTPS port for secure WS

        // Remove duplicates while preserving order
        return candidates.distinct()
    }

    fun isSimpleHostFormat(input: String): Boolean {
        val cleanInput = input.trim()
        return !cleanInput.startsWith("ws://") &&
               !cleanInput.startsWith("wss://") &&
               !cleanInput.startsWith("http://") &&
               !cleanInput.startsWith("https://")
    }
}