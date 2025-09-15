package com.ccmonitor.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ccmonitor.ui.theme.ClaudeCodeMonitorTheme
import com.ccmonitor.SessionRepository
import com.ccmonitor.SessionInfo
import com.ccmonitor.SessionOccupiedInfo
import com.ccmonitor.ConnectionHelper
import com.ccmonitor.ui.SessionOccupiedDialog
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SessionListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ClaudeCodeMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SessionListScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen() {
    val context = LocalContext.current
    val sessionRepository = remember { SessionRepository.getInstance(context) }
    val connectionHelper = remember { ConnectionHelper.getInstance(context) }
    val scope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Session occupied dialog state
    var sessionOccupiedInfo by remember { mutableStateOf<SessionOccupiedInfo?>(null) }

    // Tab titles
    val tabs = listOf("All", "Active", "Recent", "Favorites")

    // Load sessions
    fun loadSessions(filter: String = "all") {
        scope.launch {
            isLoading = true
            errorMessage = null

            val result = when (filter) {
                "active" -> sessionRepository.getActiveSessionsOnly()
                "recent" -> sessionRepository.getRecentlyViewedSessions()
                "favorites" -> {
                    val allSessions = sessionRepository.getAllSessions()
                    allSessions.map { sessionList ->
                        val favorites = sessionRepository.getFavoriteSessions()
                        sessionList.filter { favorites.contains(it.sessionId) }
                    }
                }
                else -> sessionRepository.getAllSessions()
            }

            result.fold(
                onSuccess = { sessionList ->
                    sessions = if (searchQuery.isNotEmpty()) {
                        sessionList.filter { session ->
                            session.sessionId.contains(searchQuery, ignoreCase = true) ||
                            sessionRepository.getProjectName(session.filePath).contains(searchQuery, ignoreCase = true)
                        }
                    } else {
                        sessionList
                    }
                },
                onFailure = { error ->
                    errorMessage = error.message
                }
            )
            isLoading = false
        }
    }

    // Load sessions when tab or search changes
    LaunchedEffect(selectedTab, searchQuery) {
        val filter = when (selectedTab) {
            1 -> "active"
            2 -> "recent"
            3 -> "favorites"
            else -> "all"
        }
        loadSessions(filter)
    }

    // Listen for session occupied events
    LaunchedEffect(Unit) {
        connectionHelper.sessionOccupied.collect { occupiedInfo ->
            sessionOccupiedInfo = occupiedInfo
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { /* Search is reactive */ },
                    active = isSearchActive,
                    onActiveChange = { isSearchActive = it },
                    placeholder = { Text("Search sessions...") },
                    leadingIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                ) {
                    // Search suggestions could go here
                }
            } else {
                TopAppBar(
                    title = { Text("Sessions") },
                    navigationIcon = {
                        IconButton(onClick = {
                            (context as ComponentActivity).finish()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { loadSessions(tabs[selectedTab].lowercase()) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tabs
            if (!isSearchActive) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Loading sessions...")
                        }
                    }

                    errorMessage != null -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Failed to load sessions",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { loadSessions(tabs[selectedTab].lowercase()) }) {
                                Text("Retry")
                            }
                        }
                    }

                    sessions.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = "No sessions",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = when (selectedTab) {
                                    1 -> "No active sessions"
                                    2 -> "No recent sessions"
                                    3 -> "No favorite sessions"
                                    else -> "No sessions found"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = when (selectedTab) {
                                    1 -> "Start a Claude Code session to see it here"
                                    2 -> "Sessions you view will appear here"
                                    3 -> "Mark sessions as favorites to see them here"
                                    else -> "No sessions are currently available"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            items(sessions) { session ->
                                SessionCard(
                                    session = session,
                                    onSessionClick = { sessionInfo ->
                                        // Add to recently viewed
                                        sessionRepository.addToRecentlyViewed(sessionInfo.sessionId)

                                        // Connect to session
                                        connectionHelper.subscribeToSession(sessionInfo.sessionId)

                                        // Navigate to message display
                                        val intent = Intent(context, MessageDisplayActivity::class.java).apply {
                                            putExtra("sessionId", sessionInfo.sessionId)
                                            putExtra("projectName", sessionRepository.getProjectName(sessionInfo.filePath))
                                        }
                                        context.startActivity(intent)
                                    },
                                    onFavoriteClick = { sessionInfo ->
                                        if (sessionRepository.isSessionFavorite(sessionInfo.sessionId)) {
                                            sessionRepository.removeFavoriteSession(sessionInfo.sessionId)
                                        } else {
                                            sessionRepository.markSessionAsFavorite(sessionInfo.sessionId)
                                        }
                                        // Reload if on favorites tab
                                        if (selectedTab == 3) {
                                            loadSessions("favorites")
                                        }
                                    },
                                    isFavorite = sessionRepository.isSessionFavorite(session.sessionId)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Session Occupied Dialog
    sessionOccupiedInfo?.let { occupiedInfo ->
        SessionOccupiedDialog(
            sessionOccupiedInfo = occupiedInfo,
            onDismiss = { sessionOccupiedInfo = null },
            onTakeOver = {
                // Force takeover the session
                connectionHelper.subscribeToSession(occupiedInfo.sessionId, forceTakeover = true)
                sessionOccupiedInfo = null

                // Navigate to message display
                val intent = Intent(context, MessageDisplayActivity::class.java).apply {
                    putExtra("sessionId", occupiedInfo.sessionId)
                    putExtra("projectName", "Session ${occupiedInfo.sessionId.take(8)}")
                }
                context.startActivity(intent)
            },
            onCancel = { sessionOccupiedInfo = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    session: SessionInfo,
    onSessionClick: (SessionInfo) -> Unit,
    onFavoriteClick: (SessionInfo) -> Unit,
    isFavorite: Boolean
) {
    val sessionRepository = SessionRepository.getInstance(LocalContext.current)

    Card(
        onClick = { onSessionClick(session) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Project name and session ID
                Text(
                    text = sessionRepository.getProjectName(session.filePath),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = sessionRepository.formatSessionId(session.sessionId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { },
                        enabled = false,
                        label = {
                            Text(
                                if (session.isActive) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (session.isActive) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (session.isActive)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = if (session.isActive)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    // Stats
                    session.stats?.let { stats ->
                        Text(
                            text = "${stats.messageCount} messages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Last activity
                session.stats?.lastActivity?.let { lastActivity ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last: ${formatTimestamp(lastActivity)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action buttons
            Column {
                IconButton(
                    onClick = { onFavoriteClick(session) }
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    } catch (e: Exception) {
        // Fallback for non-ISO formats
        timestamp.take(19) // Just show first 19 chars
    }
}