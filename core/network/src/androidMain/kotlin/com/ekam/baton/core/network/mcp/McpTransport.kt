package com.ekam.baton.core.network.mcp

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

interface McpTransport {
    suspend fun initialize(endpointUrl: String, authHeader: String?): Result<JsonObject>
    suspend fun listTools(endpointUrl: String, authHeader: String?): Result<List<McpTool>>
    fun callTool(endpointUrl: String, authHeader: String?, toolName: String, arguments: JsonObject): Flow<String>
    suspend fun ping(endpointUrl: String): Boolean
}
