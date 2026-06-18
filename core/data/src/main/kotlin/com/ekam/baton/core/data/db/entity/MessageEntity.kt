package com.ekam.baton.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a single message within a conversation.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "role")
    val role: String, // "user" | "assistant" | "system" | "tool_result"

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "attachments")
    val attachments: String? = null, // JSON array of attachment metadata

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_streaming")
    val isStreaming: Boolean = false,

    @ColumnInfo(name = "tool_call_json")
    val toolCallJson: String? = null, // raw MCP tool call/result JSON if applicable

    @ColumnInfo(name = "token_count")
    val tokenCount: Int? = null
)
