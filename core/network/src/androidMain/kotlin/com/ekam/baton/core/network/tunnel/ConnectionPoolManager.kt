package com.ekam.baton.core.network.tunnel


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
        val evicted = mutex.withLock {
            val toEvict = if (activeConnections.size >= MAX_ACTIVE_CONNECTIONS && !activeConnections.containsKey(connection.id)) {
                findLruConnectionLocked()
            } else null
            toEvict?.let { activeConnections.remove(it.id) }
            activeConnections[connection.id] = connection
            toEvict
        }
        try {
            evicted?.disconnect()
        } catch (_: Exception) {}
    }

    suspend fun unregisterConnection(id: String) {
        val conn = mutex.withLock {
            activeConnections.remove(id)
        }
        try {
            conn?.disconnect()
        } catch (_: Exception) {}
    }
    
    suspend fun disconnectAll() {
        monitorJob?.cancel()
        monitorJob = null
        val toDisconnect = mutex.withLock {
            val list = activeConnections.values.toList()
            activeConnections.clear()
            list
        }
        toDisconnect.forEach { conn ->
            try {
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }
    
    fun getActiveConnectionCount(): Int = activeConnections.size

    private fun findLruConnectionLocked(): PoolableConnection? {
        var oldestConn: PoolableConnection? = null
        var oldestTime = Long.MAX_VALUE

        activeConnections.values.forEach { conn ->
            if (conn.lastAccessedAt < oldestTime) {
                oldestTime = conn.lastAccessedAt
                oldestConn = conn
            }
        }
        return oldestConn
    }
    
    suspend fun evictIdleConnections(maxIdleTimeMs: Long) {
        val toDisconnect = mutex.withLock {
            val now = System.currentTimeMillis()
            val toEvict = activeConnections.values.filter { (now - it.lastAccessedAt) > maxIdleTimeMs }
            toEvict.forEach { conn ->
                activeConnections.remove(conn.id)
            }
            toEvict
        }
        toDisconnect.forEach { conn ->
            try {
                conn.disconnect()
            } catch (e: Exception) {
            }
        }
    }

    private var monitorJob: kotlinx.coroutines.Job? = null

    /**
     * Starts a background monitor that periodically checks connection health.
     * This handles network state recovery by evicting dead/failed connections
     * so that the UI layer is forced to request a fresh connection.
     */
    fun startResilienceMonitor(intervalMs: Long = 30000L) {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(intervalMs)
                try {
                    evictIdleConnections(60000 * 5) // 5 minutes idle
                } catch (_: Exception) {
                }
            }
        }
    }
}
