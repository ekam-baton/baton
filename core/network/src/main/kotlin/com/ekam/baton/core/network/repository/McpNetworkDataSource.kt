package com.ekam.baton.core.network.repository

import com.ekam.baton.core.network.api.McpApi
import com.ekam.baton.core.network.dto.McpRequestDto
import com.ekam.baton.core.network.dto.McpResponseDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class McpNetworkDataSource @Inject constructor(
    private val mcpApi: McpApi
) {
    suspend fun sendMessage(
        url: String,
        authorization: String?,
        request: McpRequestDto
    ): McpNetworkResult<McpResponseDto> {
        return try {
            val response = mcpApi.sendMessage(url, authorization, request)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    McpNetworkResult.Success(body)
                } else {
                    McpNetworkResult.Error(response.code(), "Response body is null")
                }
            } else {
                McpNetworkResult.Error(response.code(), response.message() ?: "Unknown network error")
            }
        } catch (e: Exception) {
            McpNetworkResult.Error(-1, e.message ?: "Network exception occurred", e)
        }
    }
}
