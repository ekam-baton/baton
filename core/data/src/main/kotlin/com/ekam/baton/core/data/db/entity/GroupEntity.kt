package com.ekam.baton.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val creatorId: String,
    val memberIds: String,  // JSON array of client_id strings
    val createdAt: Long = System.currentTimeMillis()
)
