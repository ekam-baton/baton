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
        const val EXTRA_SWARM_ID = "extra_swarm_id"
        const val EXTRA_SWARM_NAME = "extra_swarm_name"
        const val EXTRA_ACTIVE_NODES_COUNT = "extra_active_nodes_count"

        fun start(context: Context, swarmId: String? = null, swarmName: String? = null, activeNodes: Int = 1) {
            try {
                val intent = Intent(context, SwarmForegroundService::class.java).apply {
                    action = ACTION_START_SWARM
                    putExtra(EXTRA_SWARM_ID, swarmId)
                    putExtra(EXTRA_SWARM_NAME, swarmName)
                    putExtra(EXTRA_ACTIVE_NODES_COUNT, activeNodes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("SwarmForegroundService", "Failed to start SwarmForegroundService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SwarmForegroundService::class.java).apply {
                    action = ACTION_STOP_SWARM
                }
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("SwarmForegroundService", "Failed to stop SwarmForegroundService", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SWARM -> {
                val swarmName = intent.getStringExtra(EXTRA_SWARM_NAME) ?: "Baton Sovereign Swarm"
                val activeNodes = intent.getIntExtra(EXTRA_ACTIVE_NODES_COUNT, 1)
                val notification = buildNotification(swarmName, activeNodes)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
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

    private fun buildNotification(swarmName: String, activeNodes: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Baton Sovereign Swarm: $swarmName")
            .setContentText("Active cluster: $activeNodes connected peer nodes. Routing P2P telemetrics...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
