package com.ekam.baton.core.network.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class McpSession(
    val agentId: String,
    val endpointUrl: String,
    val authHeader: String?,
    val availableTools: List<McpTool>,
    var lastPingAt: Long
)

@Singleton
class McpConnectionManager @Inject constructor(
    private val transport: McpTransport
) {
    private val sessions = ConcurrentHashMap<String, McpSession>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    init {
        // Background ping every 60s
        scope.launch {
            while (true) {
                delay(60_000)
                sessions.values.forEach { session ->
                    val isAlive = transport.ping(session.endpointUrl)
                    if (isAlive) {
                        session.lastPingAt = System.currentTimeMillis()
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
            lastPingAt = System.currentTimeMillis()
        )
        sessions[agentId] = session
        return Result.success(session)
    }

    fun disconnectAll() {
        sessions.clear()
    }
}
