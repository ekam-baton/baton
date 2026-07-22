package com.ekam.baton.core.network.mcp

interface McpProfileProvider {
    suspend fun getBatonId(): String
    suspend fun getDisplayName(): String?
}
