package com.ekam.baton.core.data.util

import java.security.MessageDigest

object AuditCryptoUtils {
    /**
     * Generates a SHA-256 hash for a given payload and the previous hash.
     * This ensures the chain of custody is cryptographically linked.
     */
    fun generateHash(payload: String, previousHash: String): String {
        val input = "$previousHash|$payload"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
