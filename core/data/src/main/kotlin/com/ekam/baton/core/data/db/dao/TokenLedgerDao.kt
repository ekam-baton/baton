package com.ekam.baton.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenLedgerDao {
    @Insert
    suspend fun insertLedgerEntry(entry: TokenLedgerEntity)

    @Query("SELECT * FROM token_ledger WHERE agent_id = :agentId ORDER BY timestamp DESC")
    fun getLedgerForAgent(agentId: String): Flow<List<TokenLedgerEntity>>

    @Query("SELECT * FROM token_ledger WHERE swarm_id = :swarmId ORDER BY timestamp DESC")
    fun getLedgerForSwarm(swarmId: String): Flow<List<TokenLedgerEntity>>

    @Query("SELECT SUM(estimated_cost_usd) FROM token_ledger WHERE timestamp >= :startTime")
    fun getTotalCostSince(startTime: Long): Flow<Double>

    @Query("SELECT SUM(total_tokens) FROM token_ledger WHERE swarm_id = :swarmId")
    fun getTotalTokensForSwarm(swarmId: String): Flow<Int>
}
