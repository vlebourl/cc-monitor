package com.ccmonitor

import android.util.Log
import kotlinx.coroutines.*
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI

class AutoDiscoveryManager {
    private val tag = "AutoDiscoveryManager"

    data class ConnectionResult(
        val url: String,
        val isReachable: Boolean,
        val responseTimeMs: Long,
        val error: String? = null
    )

    suspend fun discoverBestConnection(
        host: String,
        timeoutMs: Int = 3000
    ): ConnectionResult? = withContext(Dispatchers.IO) {

        val candidates = generateConnectionCandidates(host)
        Log.d(tag, "Testing ${candidates.size} connection candidates for: $host")

        // Test all candidates in parallel
        val results = candidates.map { url ->
            async {
                testConnection(url, timeoutMs)
            }
        }.awaitAll()

        // Find the best working connection
        val workingConnections = results.filter { it.isReachable }

        return@withContext when {
            workingConnections.isEmpty() -> {
                Log.w(tag, "No working connections found for $host")
                results.minByOrNull { it.responseTimeMs } // Return fastest failed attempt for error info
            }
            workingConnections.size == 1 -> {
                Log.i(tag, "Found working connection: ${workingConnections[0].url}")
                workingConnections[0]
            }
            else -> {
                // Multiple working connections, prefer by priority and speed
                val best = workingConnections.minWith(compareBy<ConnectionResult> {
                    getProtocolPriority(it.url)
                }.thenBy {
                    it.responseTimeMs
                })
                Log.i(tag, "Multiple connections available, selected: ${best.url}")
                best
            }
        }
    }

    private fun generateConnectionCandidates(host: String): List<String> {
        val cleanHost = host.trim()

        // If already a full URL, return as-is
        if (cleanHost.startsWith("ws://") || cleanHost.startsWith("wss://")) {
            return listOf(cleanHost)
        }

        val hostOnly = cleanHost.split(":")[0]
        val specifiedPort = if (cleanHost.contains(":")) {
            cleanHost.split(":")[1].toIntOrNull()
        } else null

        val candidates = mutableListOf<String>()

        if (specifiedPort != null) {
            // User specified a port, respect it
            candidates.add("ws://$cleanHost")
            candidates.add("wss://$cleanHost")
        } else {
            // Auto-detect common configurations
            candidates.addAll(listOf(
                "ws://$hostOnly:8080",    // Standard WebSocket port
                "wss://$hostOnly:8080",   // Secure WebSocket
                "ws://$hostOnly:3000",    // HTTP server with WS upgrade
                "wss://$hostOnly:443",    // HTTPS/WSS standard port
                "ws://$hostOnly:80",      // HTTP/WS standard port
                "ws://$hostOnly:4000",    // Alternative development port
                "wss://$hostOnly:8443"    // Alternative secure port
            ))
        }

        return candidates.distinct()
    }

    private suspend fun testConnection(url: String, timeoutMs: Int): ConnectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            val uri = URI(url)
            val host = uri.host ?: throw IllegalArgumentException("Invalid host in URL: $url")
            val port = if (uri.port == -1) {
                when (uri.scheme) {
                    "ws" -> 80
                    "wss" -> 443
                    else -> 8080
                }
            } else {
                uri.port
            }

            Log.d(tag, "Testing connection to $host:$port")

            // Test TCP connectivity first (faster than WebSocket handshake)
            val socket = Socket()
            try {
                val socketTimeoutMs = minOf(timeoutMs, 2000) // Cap socket timeout
                socket.connect(java.net.InetSocketAddress(host, port), socketTimeoutMs)
                socket.close()

                val responseTime = System.currentTimeMillis() - startTime
                Log.d(tag, "✓ $url is reachable (${responseTime}ms)")

                ConnectionResult(
                    url = url,
                    isReachable = true,
                    responseTimeMs = responseTime
                )
            } catch (e: Exception) {
                socket.close()
                throw e
            }

        } catch (e: SocketTimeoutException) {
            val responseTime = System.currentTimeMillis() - startTime
            Log.d(tag, "✗ $url timeout after ${responseTime}ms")
            ConnectionResult(
                url = url,
                isReachable = false,
                responseTimeMs = responseTime,
                error = "Connection timeout"
            )
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            Log.d(tag, "✗ $url failed: ${e.message}")
            ConnectionResult(
                url = url,
                isReachable = false,
                responseTimeMs = responseTime,
                error = e.message ?: "Connection failed"
            )
        }
    }

    private fun getProtocolPriority(url: String): Int {
        return when {
            url.startsWith("ws://") && url.contains(":8080") -> 1  // Preferred: standard WS
            url.startsWith("wss://") && url.contains(":8080") -> 2 // Second: secure WS
            url.startsWith("ws://") -> 3                            // Third: other WS
            url.startsWith("wss://") -> 4                           // Fourth: other secure WS
            else -> 5                                               // Last: unknown
        }
    }

    fun formatSimpleInput(input: String): String {
        val clean = input.trim()

        // If it's already a full URL, return as-is
        if (clean.startsWith("ws://") || clean.startsWith("wss://")) {
            return clean
        }

        // For simple input, suggest the most likely format
        return if (clean.contains(":")) {
            "ws://$clean"
        } else {
            "ws://$clean:8080"
        }
    }

    suspend fun quickConnectivityTest(host: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = discoverBestConnection(host, 2000)
            result?.isReachable == true
        } catch (e: Exception) {
            Log.w(tag, "Quick connectivity test failed for $host: ${e.message}")
            false
        }
    }
}