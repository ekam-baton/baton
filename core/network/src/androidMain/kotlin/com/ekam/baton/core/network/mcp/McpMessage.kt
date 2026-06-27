package com.ekam.baton.core.network.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class McpResponse(
    val jsonrpc: String,
    val id: String?,
    val result: JsonObject? = null,
    val error: McpError? = null
)

@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: JsonObject? = null
)

@Serializable
data class McpTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject
)

@Serializable
data class McpToolCallParams(
    val name: String,
    val arguments: JsonObject
)

@Serializable
data class McpInitializeParams(
    val protocolVersion: String = "2024-11-05",
    val capabilities: JsonObject,
    val clientInfo: JsonObject
)
