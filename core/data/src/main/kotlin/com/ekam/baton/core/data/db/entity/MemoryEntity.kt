package com.ekam.baton.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val layer: String, // "working" | "episodic" | "semantic"
    val agentId: String?, // null = global, applies to all agents
    val conversationId: String?, // for episodic, links to the conversation it was derived from
    val title: String,
    val content: String,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val relevanceScore: Float, // 0.0-1.0
    val tags: String, // JSON array of string tags
    val isActive: Boolean = true,
    @androidx.room.ColumnInfo(name = "previous_hash", defaultValue = "")
    val previousHash: String = "",
    @androidx.room.ColumnInfo(name = "hash", defaultValue = "")
    val hash: String = ""
)
