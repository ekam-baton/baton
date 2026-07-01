package com.ekam.baton.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents an AI Agent configured in BATON.
 */
@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "avatar_uri")
    val avatarUri: String? = null,

    @ColumnInfo(name = "provider_type", defaultValue = "local_mcp")
    val providerType: String = "local_mcp", // "local_mcp", "anthropic", "gemini", "openai"

    @ColumnInfo(name = "mcp_endpoint_url")
    val mcpEndpointUrl: String,

    @ColumnInfo(name = "relay_url")
    val relayUrl: String? = null,

    @ColumnInfo(name = "auth_type")
    val authType: String, // enum string: "none", "oauth", "api_key", "bearer"

    @ColumnInfo(name = "auth_config")
    val authConfig: String, // JSON blob storing auth details

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,

    @ColumnInfo(name = "color_accent")
    val colorAccent: String, // hex color, e.g. "#3D8EFF"

    @ColumnInfo(name = "is_authenticated")
    val isAuthenticated: Boolean = false,

    @ColumnInfo(name = "last_auth_at")
    val lastAuthAt: Long? = null,

    @ColumnInfo(name = "security_mode", defaultValue = "standard")
    val securityMode: String = "standard", // "standard", "secured", "signed", "sovereign"

    @ColumnInfo(name = "security_config", defaultValue = "{}")
    val securityConfig: String = "{}", // JSON blob storing security settings and keys

    @ColumnInfo(name = "relay_token")
    val relayToken: String? = null,

    @ColumnInfo(name = "previous_hash", defaultValue = "")
    val previousHash: String = "",

    @ColumnInfo(name = "hash", defaultValue = "")
    val hash: String = ""
)
