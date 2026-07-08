package com.ekam.baton.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "world_room_props")
data class WorldRoomPropEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "room_id")
    val roomId: String,

    /** Asset key matching WorldAssetLibrary — e.g. "DUAL_MONITOR_DESK", "BOOKSHELF" */
    @ColumnInfo(name = "prop_type")
    val propType: String,

    /** Position within the room's local iso grid */
    @ColumnInfo(name = "grid_x")
    val gridX: Float,

    @ColumnInfo(name = "grid_y")
    val gridY: Float,

    @ColumnInfo(name = "grid_z", defaultValue = "0.0")
    val gridZ: Float = 0f,

    /** Seeded offset so props don't all animate in sync (golden-angle spread) */
    @ColumnInfo(name = "anim_seed", defaultValue = "0")
    val animSeed: Int = 0,

    @ColumnInfo(name = "custom_color_hex")
    val customColorHex: String? = null
)
