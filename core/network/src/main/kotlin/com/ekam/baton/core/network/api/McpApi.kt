package com.ekam.baton.core.network.api

import com.ekam.baton.core.network.dto.McpRequestDto
import com.ekam.baton.core.network.dto.McpResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface McpApi {

    /**
     * Sends a message to the agent at the specified [url].
     * 
     * @param url The dynamically resolved endpoint URL for the agent.
     * @param authorization An optional auth header (e.g. "Bearer token" or "Basic base64").
     * @param request The MCP payload containing messages and system prompt.
     */
    @POST
    suspend fun sendMessage(
        @Url url: String,
        @Header("Authorization") authorization: String?,
        @Body request: McpRequestDto
    ): Response<McpResponseDto>
}
