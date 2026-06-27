package com.ekam.baton.core.network.tunnel

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

interface PoolableConnection {
    val id: String
    suspend fun disconnect()
    val lastAccessedAt: Long
}

class ConnectionPoolManager constructor() {
    private val activeConnections = ConcurrentHashMap<String, PoolableConnection>()
    private val mutex = Mutex()
    private val MAX_ACTIVE_CONNECTIONS = 15 // Hard cap to prevent OS resource exhaustion

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun registerConnection(connection: PoolableConnection) {
        mutex.withLock {
            if (activeConnections.size >= MAX_ACTIVE_CONNECTIONS && !activeConnections.containsKey(connection.id)) {
                evictLruConnection()
            }
            activeConnections[connection.id] = connection
        }
    }

    suspend fun unregisterConnection(id: String) {
        mutex.withLock {
            val conn = activeConnections.remove(id)
            conn?.disconnect()
        }
    }
    
    fun getActiveConnectionCount(): Int = activeConnections.size

    private suspend fun evictLruConnection() {
        var oldestConn: PoolableConnection? = null
        var oldestTime = Long.MAX_VALUE

        activeConnections.values.forEach { conn ->
            if (conn.lastAccessedAt < oldestTime) {
                oldestTime = conn.lastAccessedAt
                oldestConn = conn
            }
        }

        oldestConn?.let {
            println("ConnectionPoolManager: Evicting LRU connection: ${it.id}")
            activeConnections.remove(it.id)
            it.disconnect()
        }
    }
    
    suspend fun evictIdleConnections(maxIdleTimeMs: Long) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val toEvict = activeConnections.values.filter { (now - it.lastAccessedAt) > maxIdleTimeMs }
            toEvict.forEach { conn ->
                Log.d("ConnectionPool", "Evicting idle connection: ${conn.id}")
                activeConnections.remove(conn.id)
                conn.disconnect()
            }
        }
    }

    /**
     * Starts a background monitor that periodically checks connection health.
     * This handles network state recovery by evicting dead/failed connections
     * so that the UI layer is forced to request a fresh connection.
     */
    fun startResilienceMonitor(intervalMs: Long = 30000L) {
        scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(intervalMs)
                try {
                    evictIdleConnections(60000 * 5) // 5 minutes idle
                    // Note: Actual reconnect logic is typically driven by the consumer 
                    // requesting a new session when the old one is evicted or closed.
                    // This monitor ensures dead resources are aggressively purged.
                } catch (e: Exception) {
                    Log.e("ConnectionPool", "Error in resilience monitor", e)
                }
            }
        }
    }
}
