package com.ekam.baton.core.network.mcp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

data class McpSession(
    val agentId: String,
    val endpointUrl: String,
    val authHeader: String?,
    val availableTools: List<McpTool>,
    var lastAccessedAt: Long
)

class McpConnectionManager constructor(
    private val transport: McpTransport
) {
    private val sessions = ConcurrentHashMap<String, McpSession>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    
    private val MAX_IDLE_TIME_MS = 5 * 60 * 1000L // 5 minutes

    init {
        // Background loop for eviction and pings
        scope.launch {
            while (true) {
                delay(60_000)
                mutex.withLock {
                    val now = System.currentTimeMillis()
                    val toEvict = mutableListOf<String>()
                    
                    sessions.values.forEach { session ->
                        if (now - session.lastAccessedAt > MAX_IDLE_TIME_MS) {
                            toEvict.add(session.agentId)
                        } else {
                            // Ping active sessions
                            val isAlive = transport.ping(session.endpointUrl)
                            if (!isAlive) {
                                toEvict.add(session.agentId)
                            }
                        }
                    }
                    
                    toEvict.forEach { id ->
                        Log.d("McpConnectionManager", "Evicting idle/dead session: $id")
                        sessions.remove(id)
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
            existing.lastAccessedAt = System.currentTimeMillis()
            return Result.success(existing)
        }

        // Initialize handshake
        val initResult = transport.initialize(endpointUrl, authHeader)
        if (initResult.isFailure) {
            return Result.failure(initResult.exceptionOrNull() ?: Exception("Init failed"))
        }

        // List tools
        val toolsResult = transport.listTools(endpointUrl, authHeader)
        val tools = if (toolsResult.isSuccess) toolsResult.getOrNull() ?: emptyList() else emptyList()

        val session = McpSession(
            agentId = agentId,
            endpointUrl = endpointUrl,
            authHeader = authHeader,
            availableTools = tools,
            lastAccessedAt = System.currentTimeMillis()
        )
        sessions[agentId] = session
        return Result.success(session)
    }

    fun markSessionAccessed(agentId: String) {
        sessions[agentId]?.lastAccessedAt = System.currentTimeMillis()
    }

    fun disconnectAll() {
        sessions.clear()
    }
}
