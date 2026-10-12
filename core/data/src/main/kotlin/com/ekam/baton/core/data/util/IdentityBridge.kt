package com.ekam.baton.core.data.util

import java.security.MessageDigest

object IdentityBridge {
    /**
     * Deterministically derives a permanent, collision-resistant 9-digit numeric ID from an agent's DID or UUID.
     * Guaranteed to produce the exact same ID every time for the same DID.
     */
    fun deriveNumericId(did: String): Long {
        if (did.isBlank()) return 100_000_001L
        val digest = MessageDigest.getInstance("SHA-256").digest(did.toByteArray())
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (digest[i].toLong() and 0xFF)
        }
        val positive = value and 0x7FFFFFFFFFFFFFFFL
        return 100_000_000L + (positive % 900_000_000L) // 9 digits: 100,000,000 .. 999,999,999
    }

    /**
     * Formats the combined bot token format (<numeric_id>:<secret_token>)
     * standard for external bot platforms and webhook bridges.
     */
    fun formatBotToken(numericId: Long, relayToken: String?): String {
        val token = relayToken?.takeIf { it.isNotBlank() } ?: "btn_sovereign"
        return "$numericId:$token"
    }
}
