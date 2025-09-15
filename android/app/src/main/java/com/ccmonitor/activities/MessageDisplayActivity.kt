package com.ccmonitor.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ccmonitor.ui.theme.ClaudeCodeMonitorTheme
import com.ccmonitor.ConnectionHelper
import com.ccmonitor.SessionMessage
import com.ccmonitor.ConnectionState
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MessageDisplayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionId = intent.getStringExtra("sessionId") ?: ""
        val projectName = intent.getStringExtra("projectName") ?: "Unknown Project"

        setContent {
            ClaudeCodeMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MessageDisplayScreen(
                        sessionId = sessionId,
                        projectName = projectName,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDisplayScreen(
    sessionId: String,
    projectName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val connectionHelper = remember { ConnectionHelper.getInstance(context) }
    val scope = rememberCoroutineScope()

    // State
    var messages by remember { mutableStateOf<List<SessionMessage>>(emptyList()) }
    var sessionState by remember { mutableStateOf<String?>(null) }
    val connectionState by connectionHelper.connectionState.collectAsState()
    val currentSession by connectionHelper.currentSession.collectAsState()
    val errors by connectionHelper.errors.collectAsState(initial = "")

    val listState = rememberLazyListState()

    // Load cached messages when view loads
    LaunchedEffect(sessionId) {
        // First, load recent cached messages
        try {
            val cachedMessages = connectionHelper.getRecentCachedMessages(sessionId, 100)
            messages = cachedMessages

            // Scroll to bottom after loading cached messages
            if (cachedMessages.isNotEmpty()) {
                scope.launch {
                    listState.scrollToItem(cachedMessages.size - 1)
                }
            }
        } catch (e: Exception) {
            // Handle error loading cached messages
        }
    }

    // Collect real-time messages
    LaunchedEffect(sessionId) {
        connectionHelper.messages.collect { message ->
            if (message.sessionId == sessionId) {
                // Check if message is already in the list (avoid duplicates)
                val isAlreadyPresent = messages.any { it.timestamp == message.timestamp && it.data.content == message.data.content }

                if (!isAlreadyPresent) {
                    messages = messages + message
                    // Auto-scroll to bottom when new message arrives
                    scope.launch {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            }
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = projectName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${sessionId.take(8)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Connection status indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            when (connectionState) {
                                ConnectionState.CONNECTED -> Icons.Default.CloudDone
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Icons.Default.CloudSync
                                ConnectionState.FAILED -> Icons.Default.CloudOff
                                ConnectionState.DISCONNECTED -> Icons.Default.CloudOff
                            },
                            contentDescription = "Connection status",
                            tint = when (connectionState) {
                                ConnectionState.CONNECTED -> Color.Green
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Color.Orange
                                else -> Color.Red
                            },
                            modifier = Modifier.size(20.dp)
                        )

                        // Session state indicator
                        sessionState?.let { state ->
                            Icon(
                                when (state) {
                                    "working" -> Icons.Default.AutoMode
                                    "waiting" -> Icons.Default.Schedule
                                    else -> Icons.Default.Pause
                                },
                                contentDescription = "Session state: $state",
                                tint = when (state) {
                                    "working" -> MaterialTheme.colorScheme.primary
                                    "waiting" -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Error banner
            if (errors.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errors,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Messages list
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = "No messages",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No messages yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (connectionState) {
                                ConnectionState.CONNECTED -> "Waiting for Claude Code session activity..."
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> "Connecting to session..."
                                else -> "Check your connection and try again"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(message = message)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: SessionMessage) {
    // Parse message type to determine if it's from user or Claude
    val isFromUser = message.messageType == "user"
    val isFromClaude = message.messageType == "assistant" || message.messageType == "claude"
    val isSystemMessage = !isFromUser && !isFromClaude

    // Handle different message types
    if (isSystemMessage) {
        // System messages (state changes, notifications, etc.)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    when (message.messageType) {
                        "state" -> Icons.Default.Info
                        "timeout" -> Icons.Default.Schedule
                        else -> Icons.Default.Notifications
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = when (message.messageType) {
                        "state" -> "Session state: ${message.content}"
                        "timeout" -> "Session timeout"
                        else -> message.content
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        return
    }

    // User/Assistant message bubbles
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromClaude) Arrangement.Start else Arrangement.End
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(horizontal = if (isFromClaude) 0.dp else 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isFromClaude)
                    MaterialTheme.colorScheme.surfaceContainerHigh
                else
                    MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Message header with sender and timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (isFromClaude) Icons.Default.SmartToy else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isFromClaude)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (isFromClaude) "Claude" else "You",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFromClaude)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFromClaude)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Message content
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFromClaude)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    fontFamily = if (message.content.startsWith("```") || message.content.contains("```"))
                        FontFamily.Monospace else FontFamily.Default
                )

                // Show metadata if available (for historical messages)
                message.metadata["historical"]?.let {
                    if (it == "true") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "Historical",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        // Handle ISO timestamp format
        val dateTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val now = LocalDateTime.now()

        val minutes = java.time.Duration.between(dateTime, now).toMinutes()

        when {
            minutes < 1 -> "now"
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h"
            else -> "${minutes / 1440}d"
        }
    } catch (e: Exception) {
        // Fallback - just show time part
        try {
            timestamp.substring(11, 19) // HH:mm:ss
        } catch (e: Exception) {
            "now"
        }
    }
}