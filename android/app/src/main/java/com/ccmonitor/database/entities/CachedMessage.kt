package com.ccmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "cached_messages",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["timestamp"]),
        Index(value = ["sessionId", "timestamp"])
    ]
)
data class CachedMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val messageType: String, // user, assistant, claude, state, etc.
    val content: String,
    val timestamp: String,
    val parentUuid: String = "",
    val cwd: String = "",
    val isHistorical: Boolean = false,
    val cachedAt: Long = System.currentTimeMillis(),
    val projectPath: String = ""
)