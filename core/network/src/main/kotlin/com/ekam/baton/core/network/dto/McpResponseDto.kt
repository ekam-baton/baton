package com.ekam.baton.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class McpResponseDto(
    val message: McpMessageDto
    // Note: Tool calls and other metadata can be added here later
)
