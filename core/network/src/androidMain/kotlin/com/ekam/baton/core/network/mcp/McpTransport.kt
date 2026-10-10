package com.ekam.baton.core.network.mcp

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface McpTransport {
    suspend fun initialize(endpointUrl: String, authHeader: String?, agentId: String? = null): Result<JsonObject>
    suspend fun listTools(endpointUrl: String, authHeader: String?, agentId: String? = null): Result<List<McpTool>>
    fun callTool(endpointUrl: String, authHeader: String?, toolName: String, arguments: JsonObject, agentId: String? = null): Flow<String>
    suspend fun ping(endpointUrl: String): Boolean
    suspend fun uploadFile(endpointUrl: String, authHeader: String?, uri: String, context: android.content.Context, agentId: String? = null): Result<String>
}
