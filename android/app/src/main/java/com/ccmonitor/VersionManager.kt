package com.ccmonitor

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class VersionInfo(
    val version: String,
    val apiVersion: String,
    val protocolVersion: String,
    val features: List<String> = emptyList(),
    val minClientVersion: String? = null,
    val compatibilityLevel: String = "stable"
)

@Serializable
data class CompatibilityCheck(
    val compatible: Boolean,
    val reason: String,
    val serverVersion: VersionInfo,
    val clientVersion: VersionInfo,
    val warnings: List<String> = emptyList()
)

object VersionManager {
    private val tag = "VersionManager"

    // Current Android app version info
    val CLIENT_VERSION = VersionInfo(
        version = "1.0.0",
        apiVersion = "1.0",
        protocolVersion = "1.0",
        features = listOf(
            "websocket-connection",
            "qr-authentication",
            "message-display",
            "session-monitoring",
            "auto-discovery",
            "offline-cache"
        ),
        compatibilityLevel = "stable"
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    suspend fun checkServerCompatibility(serverBaseUrl: String): CompatibilityCheck = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(tag, "Checking server compatibility at: $serverBaseUrl")

            val serverInfo = fetchServerVersion(serverBaseUrl)
            val compatibility = performCompatibilityCheck(serverInfo, CLIENT_VERSION)

            Log.i(tag, "Compatibility check result: ${compatibility.compatible}")
            if (!compatibility.compatible) {
                Log.w(tag, "Incompatible server: ${compatibility.reason}")
            }

            compatibility

        } catch (e: Exception) {
            Log.e(tag, "Failed to check server compatibility", e)
            CompatibilityCheck(
                compatible = false,
                reason = "Unable to connect to server version endpoint: ${e.message}",
                serverVersion = VersionInfo("unknown", "unknown", "unknown"),
                clientVersion = CLIENT_VERSION
            )
        }
    }

    private suspend fun fetchServerVersion(serverBaseUrl: String): VersionInfo = withContext(Dispatchers.IO) {
        // Convert WebSocket URL to HTTP for version check
        val httpUrl = serverBaseUrl
            .replace("ws://", "http://")
            .replace("wss://", "https://")
            .replace(":8080", ":3000") // Use HTTP port instead of WebSocket port

        val versionUrl = "$httpUrl/version"

        Log.d(tag, "Fetching server version from: $versionUrl")

        val connection = URL(versionUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "ClaudeCodeMonitor-Android/${CLIENT_VERSION.version}")

        return@withContext try {
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                Log.d(tag, "Server version response: $response")
                json.decodeFromString<VersionInfo>(response)
            } else {
                Log.w(tag, "Server version endpoint returned: $responseCode")
                throw Exception("Server returned HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun performCompatibilityCheck(server: VersionInfo, client: VersionInfo): CompatibilityCheck {
        val warnings = mutableListOf<String>()

        // Check API version compatibility
        val serverApiVersion = parseVersion(server.apiVersion)
        val clientApiVersion = parseVersion(client.apiVersion)

        val compatible = when {
            // Major version mismatch = incompatible
            serverApiVersion.major != clientApiVersion.major -> {
                false
            }
            // Minor version differences might have warnings
            serverApiVersion.minor > clientApiVersion.minor -> {
                warnings.add("Server has newer API version ${server.apiVersion}, some features may not work")
                true
            }
            serverApiVersion.minor < clientApiVersion.minor -> {
                warnings.add("Client has newer API version ${client.apiVersion}, some features may not be available")
                true
            }
            else -> true
        }

        // Check protocol version
        if (server.protocolVersion != client.protocolVersion) {
            warnings.add("Protocol version mismatch: server=${server.protocolVersion}, client=${client.protocolVersion}")
        }

        // Check minimum client version requirement
        server.minClientVersion?.let { minVersion ->
            val minVersionParsed = parseVersion(minVersion)
            val clientVersionParsed = parseVersion(client.version)

            if (clientVersionParsed < minVersionParsed) {
                return CompatibilityCheck(
                    compatible = false,
                    reason = "Client version ${client.version} is below minimum required version $minVersion",
                    serverVersion = server,
                    clientVersion = client,
                    warnings = warnings
                )
            }
        }

        // Check feature compatibility
        val missingFeatures = server.features.filter { serverFeature ->
            !client.features.contains(serverFeature) && isRequiredFeature(serverFeature)
        }

        if (missingFeatures.isNotEmpty()) {
            warnings.add("Missing required features: ${missingFeatures.joinToString(", ")}")
        }

        val reason = if (!compatible) {
            "API version incompatibility: server=${server.apiVersion}, client=${client.apiVersion}"
        } else {
            "Compatible"
        }

        return CompatibilityCheck(
            compatible = compatible,
            reason = reason,
            serverVersion = server,
            clientVersion = client,
            warnings = warnings
        )
    }

    private data class ParsedVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ParsedVersion> {
        override fun compareTo(other: ParsedVersion): Int {
            return when {
                major != other.major -> major.compareTo(other.major)
                minor != other.minor -> minor.compareTo(other.minor)
                else -> patch.compareTo(other.patch)
            }
        }
    }

    private fun parseVersion(version: String): ParsedVersion {
        val parts = version.split(".")
        return ParsedVersion(
            major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
    }

    private fun isRequiredFeature(feature: String): Boolean {
        return when (feature) {
            "websocket-connection" -> true
            "qr-authentication" -> true
            "message-display" -> true
            else -> false
        }
    }

    fun getCompatibilityReport(check: CompatibilityCheck): String {
        val sb = StringBuilder()

        sb.appendLine("🔍 Compatibility Check Results:")
        sb.appendLine("Status: ${if (check.compatible) "✅ Compatible" else "❌ Incompatible"}")
        sb.appendLine()
        sb.appendLine("Server: ${check.serverVersion.version} (API: ${check.serverVersion.apiVersion})")
        sb.appendLine("Client: ${check.clientVersion.version} (API: ${check.clientVersion.apiVersion})")

        if (!check.compatible) {
            sb.appendLine()
            sb.appendLine("❌ Issue: ${check.reason}")
        }

        if (check.warnings.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("⚠️ Warnings:")
            check.warnings.forEach { warning ->
                sb.appendLine("  • $warning")
            }
        }

        return sb.toString()
    }
}