package com.ekam.baton.core.data.model

import com.ekam.baton.core.data.db.entity.MessageEntity

data class Message(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val attachments: String?,
    val timestamp: Long,
    val isStreaming: Boolean,
    val tokenCount: Int?
)

fun MessageEntity.toDomainModel() = Message(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    attachments = attachments,
    timestamp = timestamp,
    isStreaming = isStreaming,
    tokenCount = tokenCount
)

fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    attachments = attachments,
    timestamp = timestamp,
    isStreaming = isStreaming,
    tokenCount = tokenCount
)
