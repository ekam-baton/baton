package com.ekam.baton.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a coordinated cluster of agents working together over WebRTC.
 * Persists the topology and leadership structure of a Swarm.
 */
@Entity(tableName = "swarms")
data class SwarmEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "leader_agent_id")
    val leaderAgentId: String,

    @ColumnInfo(name = "topology_type", defaultValue = "P2P")
    val topologyType: String = "P2P", // "P2P", "MESH", "SFU"

    @ColumnInfo(name = "worker_agent_ids")
    val workerAgentIds: String, // JSON array of Agent UUIDs

    @ColumnInfo(name = "intent_context")
    val intentContext: String, // What goal was this swarm spun up to achieve?

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "disbanded_at")
    val disbandedAt: Long? = null,

    @ColumnInfo(name = "total_uptime_ms", defaultValue = "0")
    val totalUptimeMs: Long = 0L
)
