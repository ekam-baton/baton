package com.ekam.baton.core.data.model

import com.ekam.baton.core.data.db.entity.ConversationEntity

import java.util.UUID

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val agentId: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val messageCount: Int = 0
)

fun ConversationEntity.toDomainModel() = Conversation(
    id = id,
    agentId = agentId,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isPinned = isPinned,
    messageCount = messageCount
)

fun Conversation.toEntity() = ConversationEntity(
    id = id,
    agentId = agentId,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isPinned = isPinned,
    messageCount = messageCount
)
