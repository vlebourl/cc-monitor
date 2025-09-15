package com.ccmonitor.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.*

class WebSocketService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // Service initialization will be implemented later
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // WebSocket connection logic will be implemented here
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}