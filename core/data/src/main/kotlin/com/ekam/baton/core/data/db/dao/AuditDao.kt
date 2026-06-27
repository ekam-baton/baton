package com.ekam.baton.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp ASC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastAuditLog(): AuditLogEntity?

    @Query("SELECT * FROM audit_logs ORDER BY timestamp ASC")
    suspend fun getAllAuditLogsSync(): List<AuditLogEntity>
}
