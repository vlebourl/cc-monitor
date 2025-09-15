package com.ccmonitor.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ccmonitor.database.dao.CachedMessageDao
import com.ccmonitor.database.dao.SessionCacheDao
import com.ccmonitor.database.entities.CachedMessage
import com.ccmonitor.database.entities.SessionCache

@Database(
    entities = [CachedMessage::class, SessionCache::class],
    version = 1,
    exportSchema = true
)
abstract class ClaudeCodeDatabase : RoomDatabase() {
    abstract fun cachedMessageDao(): CachedMessageDao
    abstract fun sessionCacheDao(): SessionCacheDao

    companion object {
        @Volatile
        private var INSTANCE: ClaudeCodeDatabase? = null

        fun getDatabase(context: Context): ClaudeCodeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClaudeCodeDatabase::class.java,
                    "claude_code_database"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Database created for the first time
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Database opened
            }
        }
    }
}