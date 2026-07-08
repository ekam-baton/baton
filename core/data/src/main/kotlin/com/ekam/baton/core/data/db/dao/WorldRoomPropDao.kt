package com.ekam.baton.core.data.db.dao

import androidx.room.*
import com.ekam.baton.core.data.db.entity.WorldRoomPropEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldRoomPropDao {
    @Query("SELECT * FROM world_room_props WHERE room_id = :roomId ORDER BY anim_seed ASC")
    fun observeByRoom(roomId: String): Flow<List<WorldRoomPropEntity>>

    @Query("SELECT * FROM world_room_props WHERE room_id = :roomId ORDER BY anim_seed ASC")
    suspend fun getByRoomId(roomId: String): List<WorldRoomPropEntity>

    @Upsert
    suspend fun upsert(prop: WorldRoomPropEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(props: List<WorldRoomPropEntity>)

    @Query("DELETE FROM world_room_props WHERE room_id = :roomId")
    suspend fun deleteByRoomId(roomId: String)

    @Delete
    suspend fun delete(prop: WorldRoomPropEntity)
}
