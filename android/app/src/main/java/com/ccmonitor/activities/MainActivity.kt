package com.ccmonitor.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ccmonitor.ui.theme.ClaudeCodeMonitorTheme
import com.ccmonitor.ConnectionHelper
import com.ccmonitor.AuthRepository
import com.ccmonitor.ConnectionState
import com.ccmonitor.repository.SettingsRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ClaudeCodeMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val connectionHelper = remember { ConnectionHelper.getInstance(context) }
    val authRepository = remember { AuthRepository.getInstance(context) }
    val settingsRepository = remember { SettingsRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Observe connection state
    val connectionState by connectionHelper.connectionState.collectAsState()
    val currentSession by connectionHelper.currentSession.collectAsState()
    val errors by connectionHelper.errors.collectAsState(initial = "")

    // Check for existing credentials on startup
    var hasCredentials by remember { mutableStateOf(false) }
    var isCheckingCredentials by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        hasCredentials = authRepository.hasValidCredentials()

        if (hasCredentials) {
            // Try to validate and auto-connect
            val validationResult = authRepository.validateStoredCredentials()
            validationResult.fold(
                onSuccess = { isValid ->
                    if (isValid) {
                        val apiKey = authRepository.getApiKey()
                        if (apiKey != null) {
                            connectionHelper.connect(apiKey)
                        }
                    } else {
                        authRepository.clearStoredCredentials()
                        hasCredentials = false
                    }
                },
                onFailure = {
                    authRepository.clearStoredCredentials()
                    hasCredentials = false
                }
            )
        }

        isCheckingCredentials = false
    }

    // State for info dialog and manual setup
    var showInfoDialog by remember { mutableStateOf(false) }
    var showManualSetupDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Claude Code Monitor") },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Server Info")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCheckingCredentials) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking credentials...")
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Connection status card
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Connection Status",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val statusColor = when (connectionState) {
                                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.secondary
                                ConnectionState.FAILED -> MaterialTheme.colorScheme.error
                                ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                            }

                            Text(
                                text = connectionState.name.replace("_", " "),
                                style = MaterialTheme.typography.bodyLarge,
                                color = statusColor
                            )

                            if (currentSession != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Session: ${currentSession!!.take(8)}...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Error display
                    if (errors.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = errors,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (!hasCredentials) {
                        // Authentication needed
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Welcome to Claude Code Monitor",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "To get started, scan a QR code from your desktop to pair this device.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = {
                                    val intent = Intent(context, QRScanActivity::class.java)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan QR Code")
                            }

                            OutlinedButton(
                                onClick = { showManualSetupDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Manual Setup")
                            }
                        }
                    } else {
                        // Has credentials - show main actions
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (connectionState == ConnectionState.CONNECTED) {
                                Button(
                                    onClick = {
                                        val intent = Intent(context, SessionListActivity::class.java)
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Browse Sessions")
                                }

                                if (currentSession != null) {
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(context, MessageDisplayActivity::class.java).apply {
                                                putExtra("sessionId", currentSession)
                                            }
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Current Session")
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            connectionHelper.reconnect()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = connectionState != ConnectionState.CONNECTING && connectionState != ConnectionState.RECONNECTING
                                ) {
                                    if (connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.RECONNECTING) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text("Connect")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(context, QRScanActivity::class.java)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.QrCode, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan New QR Code")
                            }

                            // Advanced options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            authRepository.clearStoredCredentials()
                                            connectionHelper.disconnect()
                                            hasCredentials = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Sign Out")
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val result = authRepository.refreshApiKey()
                                            result.fold(
                                                onSuccess = {
                                                    // Reconnect with new key
                                                    connectionHelper.reconnect()
                                                },
                                                onFailure = {
                                                    // Handle refresh failure
                                                }
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Refresh")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Server Info Dialog
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Server Information") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Status:", style = MaterialTheme.typography.labelMedium)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val statusColor = when (connectionState) {
                                    ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                                    ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.secondary
                                    ConnectionState.FAILED -> MaterialTheme.colorScheme.error
                                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline
                                }
                                Text(
                                    text = "●",
                                    color = statusColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = connectionState.name.replace("_", " "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = statusColor
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Server:", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = settingsRepository.getServerUrl().ifEmpty { "Not configured" },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (hasCredentials) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Device:", style = MaterialTheme.typography.labelMedium)
                                Text("Authenticated", style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        if (currentSession != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Session:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = "${currentSession!!.take(8)}...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Manual Setup Dialog
        if (showManualSetupDialog) {
            var configToken by remember { mutableStateOf("") }
            var isProcessing by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showManualSetupDialog = false },
                title = { Text("Manual Setup") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Paste the configuration token from your server web page:",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedTextField(
                            value = configToken,
                            onValueChange = { configToken = it },
                            label = { Text("Configuration Token") },
                            placeholder = { Text("https://your-server.com/auth?token=...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            keyboardOptions = KeyboardOptions.Default
                        )

                        if (isProcessing) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Text("Configuring...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (configToken.isNotBlank() && !isProcessing) {
                                scope.launch {
                                    isProcessing = true
                                    try {
                                        // Extract QR data from the pasted token
                                        val qrData = extractQRDataFromToken(configToken.trim())
                                        if (qrData != null) {
                                            // Same logic as QR scan
                                            val deviceId = android.provider.Settings.Secure.getString(
                                                context.contentResolver,
                                                android.provider.Settings.Secure.ANDROID_ID
                                            )

                                            settingsRepository.saveServerUrl(qrData.wsUrl)

                                            val authRepository = AuthRepository.getInstance(context, qrData.serverUrl)
                                            val connectionHelper = ConnectionHelper.getInstance(context, qrData.wsUrl)

                                            val result = authRepository.authenticateWithQRToken(qrData.guestToken, deviceId)
                                            result.fold(
                                                onSuccess = { authData ->
                                                    connectionHelper.connect(authData.apiKey)
                                                    hasCredentials = true
                                                    showManualSetupDialog = false
                                                },
                                                onFailure = { error ->
                                                    // Keep dialog open, show error
                                                }
                                            )
                                        }
                                    } catch (e: Exception) {
                                        // Keep dialog open, show error
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        enabled = configToken.isNotBlank() && !isProcessing
                    ) {
                        Text("Configure")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showManualSetupDialog = false },
                        enabled = !isProcessing
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// Helper function to extract QR data from pasted token
private fun extractQRDataFromToken(token: String): QRScanActivity.QRData? {
    return try {
        // Handle both URL format and direct query string format
        val queryString = if (token.startsWith("http")) {
            // URL format: https://server.com/auth?token=xyz
            val url = java.net.URL(token)
            url.query ?: return null
        } else {
            // Query string format: token=xyz&serverUrl=...&wsUrl=...
            token
        }

        val params = queryString.split("&").mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
            } else {
                null
            }
        }.toMap()

        val guestToken = params["token"] ?: return null

        val serverUrl: String
        val wsUrl: String

        if (params.containsKey("serverUrl") && params.containsKey("wsUrl")) {
            // Direct format with explicit URLs
            serverUrl = params["serverUrl"]!!
            wsUrl = params["wsUrl"]!!
        } else {
            // URL format - extract from the original URL
            val url = java.net.URL(token)
            serverUrl = "${url.protocol}://${url.host}${if (url.port != -1 && url.port != 80 && url.port != 443) ":${url.port}" else ""}"
            wsUrl = serverUrl.replace("https://", "wss://").replace("http://", "ws://")
        }

        QRScanActivity.QRData(
            serverUrl = serverUrl,
            wsUrl = wsUrl,
            guestToken = guestToken
        )
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to parse configuration token", e)
        null
    }
}