package com.ekam.baton.core.network.mcp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class McpSession(
    val agentId: String,
    val endpointUrl: String,
    val authHeader: String?,
    val availableTools: List<McpTool>,
    // FIX: AtomicLong eliminates data race on lastAccessedAt
    val lastAccessedAt: AtomicLong = AtomicLong(System.currentTimeMillis())
)

class McpConnectionManager constructor(
    private val transport: McpTransport
) {
    private val sessions = ConcurrentHashMap<String, McpSession>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val MAX_IDLE_TIME_MS = 5 * 60 * 1000L

    init {
        scope.launch {
            while (true) {
                delay(60_000)

                // FIX: Snapshot OUTSIDE the mutex so we never suspend while holding the lock
                val snapshot = mutex.withLock { sessions.values.toList() }
                val now = System.currentTimeMillis()
                val toEvict = mutableListOf<String>()

                for (session in snapshot) {
                    if (now - session.lastAccessedAt.get() > MAX_IDLE_TIME_MS) {
                        toEvict.add(session.agentId)
                    } else {
                        // Network I/O happens outside the lock — safe to suspend here
                        val isAlive = transport.ping(session.endpointUrl)
                        if (!isAlive) toEvict.add(session.agentId)
                    }
                }

                if (toEvict.isNotEmpty()) {
                    mutex.withLock {
                        toEvict.forEach { id ->
                            Log.d("McpConnectionManager", "Evicting idle/dead session: $id")
                            sessions.remove(id)
                        }
                    }
                }
            }
        }
    }

    suspend fun getOrCreateSession(
        agentId: String,
        endpointUrl: String,
        authHeader: String?
    ): Result<McpSession> = mutex.withLock {
        val existing = sessions[agentId]
        if (existing != null) {
            // FIX: Invalidate stale session if endpoint or auth credentials have changed
            if (existing.endpointUrl == endpointUrl && existing.authHeader == authHeader) {
                existing.lastAccessedAt.set(System.currentTimeMillis())
                return Result.success(existing)
            } else {
                Log.d("McpConnectionManager", "Endpoint/auth changed for $agentId — re-initializing")
                sessions.remove(agentId)
            }
        }

        val initResult = transport.initialize(endpointUrl, authHeader)
        if (initResult.isFailure) {
            return Result.failure(initResult.exceptionOrNull() ?: Exception("Init failed"))
        }

        val toolsResult = transport.listTools(endpointUrl, authHeader)
        val tools = toolsResult.getOrNull() ?: emptyList()

        val session = McpSession(
            agentId = agentId,
            endpointUrl = endpointUrl,
            authHeader = authHeader,
            availableTools = tools,
            lastAccessedAt = AtomicLong(System.currentTimeMillis())
        )
        sessions[agentId] = session
        return Result.success(session)
    }

    fun markSessionAccessed(agentId: String) {
        // FIX: AtomicLong.set() is thread-safe; no mutex required
        sessions[agentId]?.lastAccessedAt?.set(System.currentTimeMillis())
    }

    fun disconnectAll() {
        sessions.clear()
        // FIX: Cancel the background eviction loop
        scope.cancel()
    }
}