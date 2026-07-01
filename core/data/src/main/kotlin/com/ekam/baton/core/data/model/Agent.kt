package com.ekam.baton.core.data.model

import com.ekam.baton.core.data.db.entity.AgentEntity

import java.util.UUID

data class Agent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val avatarUri: String? = null,
    val providerType: String = "local_mcp",
    val mcpEndpointUrl: String,
    val relayUrl: String? = null,
    val authType: String,
    val authConfig: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val colorAccent: String,
    val securityMode: String = "disabled",
    val securityConfig: String = "",
    val isAuthenticated: Boolean = false,
    val lastAuthAt: Long? = null,
    val relayToken: String? = null
)

fun AgentEntity.toDomainModel() = Agent(
    id = id,
    name = name,
    description = description,
    avatarUri = avatarUri,
    providerType = providerType,
    mcpEndpointUrl = mcpEndpointUrl,
    relayUrl = relayUrl,
    authType = authType,
    authConfig = authConfig,
    isActive = isActive,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    colorAccent = colorAccent,
    securityMode = securityMode,
    securityConfig = securityConfig,
    isAuthenticated = isAuthenticated,
    lastAuthAt = lastAuthAt,
    relayToken = relayToken
)

fun Agent.toEntity() = AgentEntity(
    id = id,
    name = name,
    description = description,
    avatarUri = avatarUri,
    providerType = providerType,
    mcpEndpointUrl = mcpEndpointUrl,
    relayUrl = relayUrl,
    authType = authType,
    authConfig = authConfig,
    isActive = isActive,
    createdAt = createdAt,
    lastUsedAt = lastUsedAt,
    colorAccent = colorAccent,
    securityMode = securityMode,
    securityConfig = securityConfig,
    isAuthenticated = isAuthenticated,
    lastAuthAt = lastAuthAt,
    relayToken = relayToken
)
