package com.ekam.baton.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Append-only cryptographic ledger specifically for auditing AI Agent actions.
 * Links agent executions directly back to user prompts.
 */
@Entity(tableName = "agent_action_logs")
data class AgentActionLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "prompt_id")
    val promptId: String, // ID of the MessageEntity containing the user prompt

    @ColumnInfo(name = "agent_id")
    val agentId: String, // ID of the AgentEntity that executed the action

    @ColumnInfo(name = "action_type")
    val actionType: String, // e.g. "API_CALL_SPOTIFY", "TOOL_EXECUTION"

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "payload_json")
    val payloadJson: String, // Complete JSON dump of request/response/context

    @ColumnInfo(name = "previous_hash")
    val previousHash: String, // The hash of the PREVIOUS action log, forming a cryptographic chain

    @ColumnInfo(name = "hash")
    val hash: String // The SHA-256 hash of this entry's contents + previous_hash
)
