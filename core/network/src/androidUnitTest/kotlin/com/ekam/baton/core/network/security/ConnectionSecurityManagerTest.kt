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
        org.junit.Assume.assumeTrue(
            "Native baton_crypto library is not loaded on this host platform",
            ConnectionSecurityManager.isNativeLoaded
        )
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
        val clientKeys = securityManager.generateClientKeys()
        val clientPrivBytes = securityManager.decryptPrivateKey(clientKeys.encryptedPrivateKeyBase64, clientKeys.privateKeyIvBase64)
        val clientPubHex = clientKeys.publicKeyHex

        // Agent Keypair
        val agentKeys = securityManager.generateClientKeys()
        val agentPrivBytes = securityManager.decryptPrivateKey(agentKeys.encryptedPrivateKeyBase64, agentKeys.privateKeyIvBase64)
        val agentPubHex = agentKeys.publicKeyHex

        // Derive client-side shared secret
        val clientSharedSecret = securityManager.deriveSharedSecret(clientPrivBytes, agentPubHex)

        // Derive agent-side shared secret
        val agentSharedSecret = securityManager.deriveSharedSecret(agentPrivBytes, clientPubHex)


        // They must match
        assertEquals(securityManager.toHex(clientSharedSecret), securityManager.toHex(agentSharedSecret))
    }

    @Test
    fun testEncryptDecryptPayload() {
        val keys = securityManager.generateClientKeys()
        val privateKey = securityManager.decryptPrivateKey(keys.encryptedPrivateKeyBase64, keys.privateKeyIvBase64)
        
        val agentKeys = securityManager.generateClientKeys()
        val agentPubHex = agentKeys.publicKeyHex
        
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
        
        val agentKeys = securityManager.generateClientKeys()
        val agentPubHex = agentKeys.publicKeyHex
        
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
    @Test
    fun testFromHexValidAndRoundtrip() {
        val sm = ConnectionSecurityManager(null)
        val original = byteArrayOf(0x00, 0x01, 0x0a, 0x0f, 0x10, 0x7f, -0x80, -0x01)
        val hex = sm.toHex(original)
        val parsed = sm.fromHex(hex)
        assertEquals(sm.toHex(original), sm.toHex(parsed))
    }

    @Test
    fun testFromHexWithWhitespaceAndNewlines() {
        val sm = ConnectionSecurityManager(null)
        val hexWithSpaces = " 00 01 0a 0f \n\r 10 7f 80 ff "
        val parsed = sm.fromHex(hexWithSpaces)
        assertEquals(8, parsed.size)
        assertEquals(0x00.toByte(), parsed[0])
        assertEquals((-1).toByte(), parsed[7])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromHexOddLengthThrows() {
        val sm = ConnectionSecurityManager(null)
        sm.fromHex("abc")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromHexInvalidCharactersThrows() {
        val sm = ConnectionSecurityManager(null)
        sm.fromHex("00zz")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDeriveSharedSecretInvalidKeyLengthThrows() {
        val sm = ConnectionSecurityManager(null)
        val dummyPrivKey = ByteArray(32)
        sm.deriveSharedSecret(dummyPrivKey, "001122") // Only 3 bytes instead of 32
    }
}
