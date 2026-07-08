package com.ekam.baton.core.data.model

import com.ekam.baton.core.data.db.entity.WorldRoomPropEntity

/**
 * A single furniture/decoration prop placed inside a WorldRoom.
 * Uses iso grid coordinates relative to the room's own [WorldRoom.worldX]/[WorldRoom.worldY] origin.
 */
data class WorldRoomProp(
    val id: String,
    val roomId: String,
    /** Asset key matching WorldAssetLibrary — e.g. "DUAL_MONITOR_DESK" */
    val propType: String,
    val gridX: Float,
    val gridY: Float,
    val gridZ: Float = 0f,
    /** Seeded offset so props don't all animate in sync */
    val animSeed: Int = 0,
    val customColorHex: String? = null
)

fun WorldRoomPropEntity.toDomainModel() = WorldRoomProp(
    id = id, roomId = roomId, propType = propType,
    gridX = gridX, gridY = gridY, gridZ = gridZ,
    animSeed = animSeed, customColorHex = customColorHex
)

fun WorldRoomProp.toEntity() = WorldRoomPropEntity(
    id = id, roomId = roomId, propType = propType,
    gridX = gridX, gridY = gridY, gridZ = gridZ,
    animSeed = animSeed, customColorHex = customColorHex
)
