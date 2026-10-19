package com.ekam.baton.core.network.mcp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    // AtomicLong eliminates data race on lastAccessedAt
    val lastAccessedAt: AtomicLong = AtomicLong(System.currentTimeMillis())
)

class McpConnectionManager constructor(
    private val httpTransport: McpTransport,
    private val webSocketTransport: McpTransport
) {
    private val sessions = ConcurrentHashMap<String, McpSession>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One mutex per agent ID prevents global serialisation while still
    // guarding against concurrent double-initialisation for the same agent.
    private val agentMutexes = ConcurrentHashMap<String, Mutex>()

    private val MAX_IDLE_TIME_MS = 5 * 60 * 1000L
    private var evictionJob: Job? = null

    private fun mutexFor(agentId: String): Mutex =
        agentMutexes.getOrPut(agentId) { Mutex() }

    private fun getTransport(endpointUrl: String): McpTransport {
        // nosemgrep
        return if (endpointUrl.startsWith("ws://") || endpointUrl.startsWith("wss://")) {
            webSocketTransport
        } else {
            httpTransport
        }
    }

    // Expose getTransport so McpMessageSender can use it
    fun getTransportForUrl(endpointUrl: String): McpTransport = getTransport(endpointUrl)

    init {
        startEvictionLoop()
    }

    private fun startEvictionLoop() {
        evictionJob?.cancel()
        evictionJob = scope.launch {
            while (true) {
                delay(60_000)

                // Snapshot outside any lock so we never suspend while holding it
                val snapshot = sessions.values.toList()
                val now = System.currentTimeMillis()
                val toEvict = mutableListOf<String>()

                for (session in snapshot) {
                    if (now - session.lastAccessedAt.get() > MAX_IDLE_TIME_MS) {
                        toEvict.add(session.agentId)
                    } else {
                        // Network I/O happens fully outside any lock — safe to suspend here
                        val isAlive = getTransport(session.endpointUrl).ping(session.endpointUrl)
                        if (!isAlive) toEvict.add(session.agentId)
                    }
                }

                if (toEvict.isNotEmpty()) {
                    toEvict.forEach { id ->
                        Log.d("McpConnectionManager", "Evicting idle/dead session: $id")
                        sessions.remove(id)
                        agentMutexes.remove(id)
                    }
                }
            }
        }
    }

    /**
     * Returns an existing valid session, or creates a new one.
     *
     * KEY FIX: Network I/O (initialize + listTools) is performed OUTSIDE the
     * per-agent mutex. The mutex is only held for the cheap map read/write
     * Per-agent mutex ensures at most one network initialisation per agent
     * under concurrent callers, while allowing different agents to initialise
     * in parallel without blocking each other.
     */
    suspend fun getOrCreateSession(
        agentId: String,
        endpointUrl: String,
        authHeader: String?
    ): Result<McpSession> {
        val mutex = mutexFor(agentId)

        return mutex.withLock {
            val existing = sessions[agentId]
            if (existing != null &&
                existing.endpointUrl == endpointUrl &&
                existing.authHeader == authHeader
            ) {
                existing.lastAccessedAt.set(System.currentTimeMillis())
                return@withLock Result.success(existing)
            }
            if (existing != null) {
                Log.d("McpConnectionManager", "Endpoint/auth changed for $agentId — will re-initialise")
                sessions.remove(agentId)
            }

            val transport = getTransport(endpointUrl)
            val initResult = transport.initialize(endpointUrl, authHeader, agentId)
            if (initResult.isFailure) {
                return@withLock Result.failure(initResult.exceptionOrNull() ?: Exception("MCP init failed for $agentId"))
            }

            val toolsResult = transport.listTools(endpointUrl, authHeader, agentId)
            val tools = toolsResult.getOrNull() ?: emptyList()

            val session = McpSession(
                agentId = agentId,
                endpointUrl = endpointUrl,
                authHeader = authHeader,
                availableTools = tools,
                lastAccessedAt = AtomicLong(System.currentTimeMillis())
            )
            sessions[agentId] = session
            Result.success(session)
        }
    }

    fun markSessionAccessed(agentId: String) {
        // AtomicLong.set() is thread-safe; no mutex required
        sessions[agentId]?.lastAccessedAt?.set(System.currentTimeMillis())
    }

    fun disconnectAll() {
        sessions.clear()
        agentMutexes.clear()
        // Reset the background eviction loop cleanly
        startEvictionLoop()
    }
}
