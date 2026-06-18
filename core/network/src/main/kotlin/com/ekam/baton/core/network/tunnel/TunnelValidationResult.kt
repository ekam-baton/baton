package com.ekam.baton.core.network.tunnel

data class TunnelValidationResult(
    val status: Status,
    val serverName: String?,
    val availableTools: List<String>?,
    val error: String?
)

enum class Status {
    VALID, REACHABLE_NO_MCP, UNREACHABLE, INVALID_URL
}
