package com.ekam.baton.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ekam.baton.core.data.db.entity.SwarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SwarmDao {
    @Query("SELECT * FROM swarms")
    fun getAllSwarms(): Flow<List<SwarmEntity>>

    @Query("SELECT * FROM swarms WHERE is_active = 1")
    fun getActiveSwarms(): Flow<List<SwarmEntity>>

    @Query("SELECT * FROM swarms WHERE id = :id")
    suspend fun getSwarmById(id: String): SwarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwarm(swarm: SwarmEntity)

    @Update
    suspend fun updateSwarm(swarm: SwarmEntity)

    @Query("UPDATE swarms SET is_active = 0, disbanded_at = :timestamp WHERE id = :id")
    suspend fun disbandSwarm(id: String, timestamp: Long = System.currentTimeMillis())
}
