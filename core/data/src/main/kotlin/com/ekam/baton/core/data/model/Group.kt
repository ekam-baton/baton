package com.ekam.baton.core.data.model

import com.ekam.baton.core.data.db.entity.GroupEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class Group(
    val id: String,
    val name: String,
    val creatorId: String,
    val memberIds: List<String>,
    val createdAt: Long
)

fun GroupEntity.toDomainModel(): Group {
    val membersList = try {
        Json.decodeFromString<List<String>>(this.memberIds)
    } catch (e: Exception) {
        emptyList()
    }
    return Group(
        id = this.id,
        name = this.name,
        creatorId = this.creatorId,
        memberIds = membersList,
        createdAt = this.createdAt
    )
}

fun Group.toEntity(): GroupEntity {
    return GroupEntity(
        id = this.id,
        name = this.name,
        creatorId = this.creatorId,
        memberIds = Json.encodeToString(this.memberIds),
        createdAt = this.createdAt
    )
}
