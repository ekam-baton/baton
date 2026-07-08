package com.ekam.baton.core.data.model

import com.ekam.baton.core.data.db.entity.WorldRoomEntity

data class WorldRoom(
    val id: String,
    val agentId: String,
    val role: AgentRole,
    val displayName: String,
    val colorHex: String,
    val worldX: Int = 0,
    val worldY: Int = 0,
    val widthTiles: Int = 8,
    val heightTiles: Int = 6,
    val createdAt: Long = System.currentTimeMillis()
)

fun WorldRoomEntity.toDomainModel() = WorldRoom(
    id = id, agentId = agentId,
    role = try { AgentRole.valueOf(roleKey) } catch (_: Exception) { AgentRole.COORDINATOR },
    displayName = displayName, colorHex = colorHex,
    worldX = worldX, worldY = worldY,
    widthTiles = widthTiles, heightTiles = heightTiles,
    createdAt = createdAt
)

fun WorldRoom.toEntity() = WorldRoomEntity(
    id = id, agentId = agentId, roleKey = role.name,
    displayName = displayName, colorHex = colorHex,
    worldX = worldX, worldY = worldY,
    widthTiles = widthTiles, heightTiles = heightTiles,
    createdAt = createdAt
)
