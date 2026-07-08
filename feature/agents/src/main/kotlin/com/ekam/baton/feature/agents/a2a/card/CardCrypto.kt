package com.ekam.baton.feature.agents.a2a.card

import java.security.MessageDigest
import java.util.UUID

/**
 * Handles cryptographic identity generation for Agent Identity Cards.
 * 
 * Each agent receives a Decentralized Identifier (DID) and a unique visual
 * fingerprint block based on their unique ID.
 */
object CardCrypto {
    
    /**
     * Generates a deterministic DID (Decentralized Identifier) string for the agent.
     * Format: did:baton:agent:<sha256_hash>
     */
    fun generateAgentDID(agentId: String): String {
        val bytes = agentId.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val hashStr = digest.joinToString("") { "%02x".format(it) }
        // Take first 16 chars for the visual DID on the card
        return "did:baton:agent:${hashStr.take(16)}"
    }

    /**
     * Generates a deterministic 8x8 boolean matrix (64 bits) used for drawing
     * the agent's unique cryptographic data block (similar to a QR code).
     */
    fun generateVisualFingerprint(agentId: String): BooleanArray {
        val bytes = agentId.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes) // 32 bytes
        
        // We need 64 bits (8 bytes) to form an 8x8 grid.
        // We'll take the first 8 bytes of the SHA-256 hash.
        val fingerprint = BooleanArray(64)
        for (i in 0 until 8) {
            val byte = digest[i].toInt()
            for (j in 0 until 8) {
                // Extract each bit
                val bit = (byte shr j) and 1
                fingerprint[i * 8 + j] = (bit == 1)
            }
        }
        return fingerprint
    }
    
    /**
     * Generates a 4-segment serial number for the card.
     */
    fun generateSerialNumber(agentId: String): String {
        val uuid = runCatching { UUID.fromString(agentId) }.getOrNull() ?: UUID.nameUUIDFromBytes(agentId.toByteArray())
        val parts = uuid.toString().uppercase().split("-")
        return "${parts[0]}-${parts[1]}-${parts[2]}-${parts[3]}"
    }
}
