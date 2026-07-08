package com.ekam.baton.core.data.db.dao

import androidx.room.*
import com.ekam.baton.core.data.db.entity.WorldRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldRoomDao {
    @Query("SELECT * FROM world_rooms ORDER BY created_at ASC")
    fun observeAll(): Flow<List<WorldRoomEntity>>

    @Query("SELECT * FROM world_rooms WHERE agent_id = :agentId LIMIT 1")
    suspend fun getByAgentId(agentId: String): WorldRoomEntity?

    @Query("SELECT * FROM world_rooms WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorldRoomEntity?

    @Upsert
    suspend fun upsert(room: WorldRoomEntity)

    @Delete
    suspend fun delete(room: WorldRoomEntity)

    @Query("DELETE FROM world_rooms WHERE agent_id = :agentId")
    suspend fun deleteByAgentId(agentId: String)
}
