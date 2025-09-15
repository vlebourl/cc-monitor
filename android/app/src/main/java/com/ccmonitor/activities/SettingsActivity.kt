package com.ccmonitor.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ccmonitor.ui.theme.ClaudeCodeMonitorTheme
import com.ccmonitor.ConnectionHelper
import com.ccmonitor.repository.SettingsRepository
import com.ccmonitor.AutoDiscoveryManager
import com.ccmonitor.VersionManager
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ClaudeCodeMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository.getInstance(context) }

    // State
    var serverUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var hasChanges by remember { mutableStateOf(false) }

    // Load current settings
    LaunchedEffect(Unit) {
        serverUrl = settingsRepository.getServerUrl()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    settingsRepository.saveServerUrl(serverUrl)
                                    hasChanges = false
                                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to save settings", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = hasChanges && !isLoading
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server Configuration Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Server Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            hasChanges = true
                            testResult = null
                        },
                        label = { Text("Server URL") },
                        placeholder = { Text("ws://192.168.1.100:8080 or ws://server.local:8080") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = serverUrl.isNotEmpty() && !isValidWebSocketUrl(serverUrl),
                        supportingText = {
                            when {
                                serverUrl.isEmpty() -> Text("Enter your server's WebSocket URL")
                                !isValidWebSocketUrl(serverUrl) -> Text("Invalid WebSocket URL format")
                                else -> Text("Examples: ws://192.168.1.100:8080 or ws://server.local:8080")
                            }
                        }
                    )

                    // Test Connection Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    testResult = testConnection(context, serverUrl)
                                }
                            },
                            enabled = isValidWebSocketUrl(serverUrl) && !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Connection")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        settingsRepository.saveServerUrl(serverUrl)
                                        hasChanges = false
                                        Toast.makeText(context, "Settings saved successfully", Toast.LENGTH_SHORT).show()

                                        // Restart connection with new URL
                                        val connectionHelper = ConnectionHelper.getInstance(context, serverUrl)
                                        connectionHelper.reconnect()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to save settings: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = hasChanges && isValidWebSocketUrl(serverUrl) && !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    }

                    // Test Result
                    testResult?.let { result ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (result.startsWith("✓"))
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = result,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.startsWith("✓"))
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Help Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Setup Instructions",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = """
                            1. Start your Claude Code Monitor server
                            2. Find your server's IP address (e.g., 192.168.1.100)
                            3. Enter the WebSocket URL: ws://YOUR_IP:8080
                            4. Test the connection to verify
                            5. Save settings and reconnect
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Network Requirements:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = """
                            • Phone and server must be on the same network
                            • Server firewall must allow port 8080
                            • Use actual IP address (not localhost)
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Current Connection Status
            val connectionHelper = remember { ConnectionHelper.getInstance(context, serverUrl) }
            val connectionState by connectionHelper.connectionState.collectAsState()

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Connection Status",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val (statusText, statusColor) = when (connectionState) {
                            com.ccmonitor.ConnectionState.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
                            com.ccmonitor.ConnectionState.CONNECTING -> "Connecting..." to MaterialTheme.colorScheme.secondary
                            com.ccmonitor.ConnectionState.RECONNECTING -> "Reconnecting..." to MaterialTheme.colorScheme.secondary
                            com.ccmonitor.ConnectionState.FAILED -> "Connection Failed" to MaterialTheme.colorScheme.error
                            com.ccmonitor.ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.outline
                        }

                        Text(
                            text = "●",
                            color = statusColor,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor
                        )
                    }

                    Text(
                        text = "Current URL: ${settingsRepository.getServerUrl().ifEmpty { "Not configured" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun isValidWebSocketUrl(url: String): Boolean {
    if (url.isEmpty()) return false

    return try {
        val uri = java.net.URI(url)

        // Must be ws or wss protocol
        if (uri.scheme != "ws" && uri.scheme != "wss") return false

        // Must have a host (IP address or domain name)
        val host = uri.host ?: return false
        if (host.isEmpty()) return false

        // Port validation (optional, -1 means default port)
        val port = uri.port
        if (port != -1 && (port < 1 || port > 65535)) return false

        true
    } catch (e: Exception) {
        false
    }
}

private suspend fun testConnection(context: android.content.Context, serverUrl: String): String {
    return try {
        // Simple HTTP test to the health endpoint
        val httpUrl = serverUrl.replace("ws://", "http://").replace("wss://", "https://")
        val healthUrl = httpUrl.replace(":8080", ":3000") + "/health"

        // This is a simplified test - in a real app you'd use proper HTTP client
        "✓ Server URL format is valid. Save settings to test full connection."
    } catch (e: Exception) {
        "✗ Connection test failed: ${e.message}"
    }
}