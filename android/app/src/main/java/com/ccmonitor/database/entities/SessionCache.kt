package com.ccmonitor.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "session_cache",
    indices = [Index(value = ["lastActivity"])]
)
data class SessionCache(
    @PrimaryKey
    val sessionId: String,
    val projectPath: String,
    val filePath: String,
    val isActive: Boolean,
    val lastActivity: String,
    val messageCount: Int = 0,
    val lastMessageType: String = "",
    val lastMessageContent: String = "",
    val cachedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)