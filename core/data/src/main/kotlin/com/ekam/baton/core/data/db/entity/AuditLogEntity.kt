package com.ekam.baton.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Append-only cryptographic ledger for E-Discovery and auditing.
 */
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "entity_name")
    val entityName: String, // e.g. "MessageEntity", "MemoryEntity"

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "action")
    val action: String, // "INSERT", "UPDATE", "DELETE"

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "device_id")
    val deviceId: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "previous_hash")
    val previousHash: String, // The hash of the PREVIOUS audit log entry, forming a chain

    @ColumnInfo(name = "hash")
    val hash: String // The SHA-256 hash of this entry's contents + previous_hash
)
