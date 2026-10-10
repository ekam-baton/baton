package com.ekam.baton.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class McpMessageDto(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val suggestions: List<String>? = null
)
