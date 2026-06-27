package com.ekam.baton.core.data.model

import com.ekam.baton.core.data.db.entity.MemoryEntity

data class Memory(
    val id: String,
    val layer: String,
    val agentId: String?,
    val conversationId: String?,
    val title: String,
    val content: String,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val relevanceScore: Float,
    val tags: String,
    val isActive: Boolean
)

fun MemoryEntity.toDomainModel() = Memory(
    id = id,
    layer = layer,
    agentId = agentId,
    conversationId = conversationId,
    title = title,
    content = content,
    createdAt = createdAt,
    lastAccessedAt = lastAccessedAt,
    relevanceScore = relevanceScore,
    tags = tags,
    isActive = isActive
)

fun Memory.toEntity() = MemoryEntity(
    id = id,
    layer = layer,
    agentId = agentId,
    conversationId = conversationId,
    title = title,
    content = content,
    createdAt = createdAt,
    lastAccessedAt = lastAccessedAt,
    relevanceScore = relevanceScore,
    tags = tags,
    isActive = isActive
)
