package com.ekam.baton.feature.agents.tunnel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import android.annotation.SuppressLint
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator
import com.ekam.baton.core.network.tunnel.Status
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.component.inject

class TunnelConnectivityMonitor(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), org.koin.core.component.KoinComponent {

    private val agentRepository: AgentRepository by inject()
    private val endpointValidator: TunnelEndpointValidator by inject()
    private val appPreferences: AppPreferences by inject()

    override suspend fun doWork(): Result {
        // Fetch all agents
        val agents = agentRepository.getAllAgents().firstOrNull() ?: return Result.success()

        // Filter tunnel agents
        val tunnelAgents = agents.filter {
            val host = try { java.net.URL(it.mcpEndpointUrl).host } catch (e: Exception) { "" }
            host.endsWith(".trycloudflare.com") || host.endsWith(".ngrok.io") || 
            host.endsWith(".ngrok-free.app") || host.endsWith(".bore.pub") || host == "localhost"
        }

        if (tunnelAgents.isEmpty()) {
            return Result.success()
        }

        // Get previous statuses from AppPreferences
        val previousStatuses = appPreferences.tunnelStatusMap.firstOrNull() ?: emptyMap()
        val currentStatuses = mutableMapOf<String, String>()

        for (agent in tunnelAgents) {
            val result = endpointValidator.validateEndpoint(agent.mcpEndpointUrl)
            val isOnline = result.status == Status.VALID || result.status == Status.REACHABLE_NO_MCP
            val statusStr = if (isOnline) "ONLINE" else "OFFLINE"
            
            currentStatuses[agent.id] = statusStr

            val previousStatus = previousStatuses[agent.id]
            if (previousStatus != null && previousStatus != statusStr) {
                // Status changed, post notification
                if (isOnline) {
                    postNotification("Agent ${agent.name} is back online", "Connected to ${agent.mcpEndpointUrl}")
                } else {
                    postNotification("Agent ${agent.name} is offline", "Cannot reach ${agent.mcpEndpointUrl}")
                }
            }
        }

        // Save current statuses
        appPreferences.setTunnelStatusMap(currentStatuses)

        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(title: String, content: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val builder = NotificationCompat.Builder(applicationContext, "baton_tunnel_alerts")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
