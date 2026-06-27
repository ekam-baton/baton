package com.ekam.baton.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekam.baton.core.data.db.entity.AgentActionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentActionLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAgentActionLog(log: AgentActionLogEntity)

    @Query("SELECT * FROM agent_action_logs ORDER BY timestamp ASC")
    fun getAllAgentActionLogs(): Flow<List<AgentActionLogEntity>>

    @Query("SELECT * FROM agent_action_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastAgentActionLog(): AgentActionLogEntity?

    @Query("SELECT * FROM agent_action_logs ORDER BY timestamp ASC")
    suspend fun getAllAgentActionLogsSync(): List<AgentActionLogEntity>
}
