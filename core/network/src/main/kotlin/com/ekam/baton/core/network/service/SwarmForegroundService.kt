package com.ekam.baton.core.network.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SwarmForegroundService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val CHANNEL_ID = "SwarmNetworkChannel"
        private const val NOTIFICATION_ID = 42
        const val ACTION_START_SWARM = "com.ekam.baton.action.START_SWARM"
        const val ACTION_STOP_SWARM = "com.ekam.baton.action.STOP_SWARM"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SWARM -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                // TODO: Migrate WebRTCTransport session array from A2AViewModel here
            }
            ACTION_STOP_SWARM -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        // Sticky so it restarts if OS kills it
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Agent Swarm Execution",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps WebRTC P2P Agent Tunnels open in the background."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Baton Sovereign Swarm Active")
            .setContentText("Routing peer-to-peer agent telemetrics...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
