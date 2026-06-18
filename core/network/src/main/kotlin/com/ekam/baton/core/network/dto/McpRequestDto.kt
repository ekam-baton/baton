package com.ekam.baton.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class McpRequestDto(
    val systemPrompt: String? = null,
    val messages: List<McpMessageDto>
    // Note: Tools and tool_calls can be added here later
)
