package com.ekam.baton.core.network.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

class ConnectionSecurityManagerTest {

    private lateinit var securityManager: ConnectionSecurityManager

    @Before
    fun setUp() {
        securityManager = ConnectionSecurityManager(null)
    }

    @Test
    fun testGenerateClientKeys() {
        val keys = securityManager.generateClientKeys()
        assertNotNull(keys)
        assertEquals(64, keys.publicKeyHex.length) // 32 bytes public key = 64 hex chars
        assertNotNull(keys.encryptedPrivateKeyBase64)
        assertNotNull(keys.privateKeyIvBase64)
    }

    @Test
    fun testDeriveSharedSecret() {
        // Client Keypair
        val clientPrivBytes = Curve25519.generatePrivateKey()
        val clientPubBytes = Curve25519.getPublicKey(clientPrivBytes)
        val clientPubHex = securityManager.toHex(clientPubBytes)

        // Agent Keypair
        val agentPrivBytes = Curve25519.generatePrivateKey()
        val agentPubBytes = Curve25519.getPublicKey(agentPrivBytes)
        val agentPubHex = securityManager.toHex(agentPubBytes)

        // Derive client-side shared secret
        val clientSharedSecret = securityManager.deriveSharedSecret(clientPrivBytes, agentPubHex)

        // Derive agent-side shared secret
        val agentSharedSecret = securityManager.deriveSharedSecret(agentPrivBytes, clientPubHex)

        println("Client Private: " + securityManager.toHex(clientPrivBytes))
        println("Client Public: " + clientPubHex)
        println("Agent Private: " + securityManager.toHex(agentPrivBytes))
        println("Agent Public: " + agentPubHex)
        println("Client Shared: " + securityManager.toHex(clientSharedSecret))
        println("Agent Shared: " + securityManager.toHex(agentSharedSecret))

        // They must match
        assertEquals(securityManager.toHex(clientSharedSecret), securityManager.toHex(agentSharedSecret))
    }

    @Test
    fun testEncryptDecryptPayload() {
        val keys = securityManager.generateClientKeys()
        val privateKey = securityManager.decryptPrivateKey(keys.encryptedPrivateKeyBase64, keys.privateKeyIvBase64)
        
        val agentPrivBytes = Curve25519.generatePrivateKey()
        val agentPubHex = securityManager.toHex(Curve25519.getPublicKey(agentPrivBytes))
        
        val sharedSecret = securityManager.deriveSharedSecret(privateKey, agentPubHex)

        val plaintext = "Hello MCP, this is a secure message."
        val nonce = "test_nonce_123"
        val timestamp = 1700000000000L
        val encrypted = securityManager.encryptPayload(plaintext, sharedSecret, nonce, timestamp)
        
        assertNotNull(encrypted.ciphertextBase64)
        assertNotNull(encrypted.ivBase64)
        assertNotEquals(plaintext, encrypted.ciphertextBase64)

        val decrypted = securityManager.decryptPayload(encrypted.ciphertextBase64, encrypted.ivBase64, sharedSecret, nonce, timestamp)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun testComputeSignature() {
        val keys = securityManager.generateClientKeys()
        val privateKey = securityManager.decryptPrivateKey(keys.encryptedPrivateKeyBase64, keys.privateKeyIvBase64)
        val agentPrivBytes = Curve25519.generatePrivateKey()
        val agentPubHex = securityManager.toHex(Curve25519.getPublicKey(agentPrivBytes))
        
        val sharedSecret = securityManager.deriveSharedSecret(privateKey, agentPubHex)
        val ciphertext = "encrypted_base64_blob"
        val timestamp = 1700000000000L
        val nonce = "nonce12345"

        val signature = securityManager.computeSignature(timestamp, nonce, ciphertext, sharedSecret)
        assertNotNull(signature)
        assertEquals(64, signature.length) // SHA-256 HMAC in hex is 64 chars

        // Different nonce must yield different signature
        val signatureDiff = securityManager.computeSignature(timestamp, "nonce99999", ciphertext, sharedSecret)
        assertNotEquals(signature, signatureDiff)
    }
}
