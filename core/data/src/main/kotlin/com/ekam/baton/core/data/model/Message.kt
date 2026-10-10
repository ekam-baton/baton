package com.ekam.baton.core.data.model

import com.ekam.baton.core.data.db.entity.MessageEntity

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class Message(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val attachments: String?,
    val timestamp: Long,
    val isStreaming: Boolean,
    val isFailed: Boolean,
    val tokenCount: Int?,
    val suggestions: List<String>? = null
)

fun MessageEntity.toDomainModel() = Message(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    attachments = attachments,
    timestamp = timestamp,
    isStreaming = isStreaming,
    isFailed = isFailed,
    tokenCount = tokenCount,
    suggestions = suggestions?.let { 
        try { Json.decodeFromString<List<String>>(it) } catch (e: Exception) { null }
    }
)

fun Message.toEntity() = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role,
    content = content,
    attachments = attachments,
    timestamp = timestamp,
    isStreaming = isStreaming,
    isFailed = isFailed,
    tokenCount = tokenCount,
    suggestions = suggestions?.let { Json.encodeToString(it) }
)
