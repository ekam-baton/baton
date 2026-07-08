package com.ekam.baton.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "world_rooms")
data class WorldRoomEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "agent_id")
    val agentId: String,

    @ColumnInfo(name = "role_key")
    val roleKey: String = "COORDINATOR",

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#3D8EFF",

    @ColumnInfo(name = "world_x", defaultValue = "0")
    val worldX: Int = 0,

    @ColumnInfo(name = "world_y", defaultValue = "0")
    val worldY: Int = 0,

    @ColumnInfo(name = "width_tiles", defaultValue = "8")
    val widthTiles: Int = 8,

    @ColumnInfo(name = "height_tiles", defaultValue = "6")
    val heightTiles: Int = 6,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
