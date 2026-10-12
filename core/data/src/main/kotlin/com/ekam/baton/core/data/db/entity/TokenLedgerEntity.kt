package com.ekam.baton.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

import androidx.room.Index

/**
 * Immutable ledger tracking every token consumed and its associated cost.
 * Critical for Cost Visibility constraints and protecting against run-away agent loops.
 */
@Entity(
    tableName = "token_ledger",
    indices = [
        Index(value = ["swarm_id", "timestamp"], name = "index_token_ledger_swarm"),
        Index(value = ["agent_id", "timestamp"], name = "index_token_ledger_agent")
    ]
)
data class TokenLedgerEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "agent_id")
    val agentId: String,

    @ColumnInfo(name = "swarm_id")
    val swarmId: String? = null, // Nullable if the agent was acting alone

    @ColumnInfo(name = "model_name")
    val modelName: String, // e.g., "claude-3-5-sonnet", "gemini-1.5-pro"

    @ColumnInfo(name = "prompt_tokens", defaultValue = "0")
    val promptTokens: Int = 0,

    @ColumnInfo(name = "completion_tokens", defaultValue = "0")
    val completionTokens: Int = 0,

    @ColumnInfo(name = "total_tokens", defaultValue = "0")
    val totalTokens: Int = 0,

    @ColumnInfo(name = "estimated_cost_usd")
    val estimatedCostUsd: Double = 0.0,

    @ColumnInfo(name = "burn_rate_tks")
    val burnRateTks: Double = 0.0, // Tokens per second metric at time of execution

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "action_context")
    val actionContext: String // What were they doing? e.g., "Code generation", "WebRTC signaling context"
)
