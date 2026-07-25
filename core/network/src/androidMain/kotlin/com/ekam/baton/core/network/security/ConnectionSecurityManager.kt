package com.ekam.baton.core.network.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles X25519 key generation, Android Keystore wrapper encryption,
 * shared secret derivation, and AES-256-GCM E2EE via Rust JNI.
 */
class ConnectionSecurityManager constructor(
    private val context: Context?
) {
    private val keyStoreAlias = "baton_connection_security_master"
    private val secureRandom = SecureRandom()
    private var softwareMasterKey: SecretKey? = null

    init {
        initMasterKey()
    }

    private fun initMasterKey() {
        try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(keyStoreAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                try {
                    // Try StrongBox first
                    val builder = KeyGenParameterSpec.Builder(
                        keyStoreAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        builder.setIsStrongBoxBacked(true)
                    }
                    
                    keyGenerator.init(builder.build())
                    keyGenerator.generateKey()
                } catch (e: Exception) { // StrongBoxUnavailableException or general
                    // Fallback to regular TEE
                    keyGenerator.init(
                        KeyGenParameterSpec.Builder(
                            keyStoreAlias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                    keyGenerator.generateKey()
                }
            }
        } catch (e: Exception) {
            // Fallback for JVM unit tests
            val keyBytes = ByteArray(32)
            SecureRandom().nextBytes(keyBytes)
            softwareMasterKey = SecretKeySpec(keyBytes, "AES")
        }
    }

    private fun getMasterKey(): SecretKey {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            ks.getKey(keyStoreAlias, null) as SecretKey
        } catch (e: Exception) {
            if (softwareMasterKey == null) {
                val keyBytes = ByteArray(32)
                SecureRandom().nextBytes(keyBytes)
                softwareMasterKey = SecretKeySpec(keyBytes, "AES")
            }
            softwareMasterKey!!
        }
    }

    /**
     * Encrypts the client's X25519 private key using the Keystore-backed master key.
     */
    fun encryptPrivateKey(privateKey: ByteArray): Pair<String, String> {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            cipher.init(Cipher.ENCRYPT_MODE, getMasterKey(), GCMParameterSpec(128, iv))
            val ciphertext = cipher.doFinal(privateKey)
            return Base64.getEncoder().encodeToString(ciphertext) to Base64.getEncoder().encodeToString(cipher.iv)
        } catch (e: Exception) {
            throw SecurityException("Failed to encrypt private key", e)
        }
    }

    /**
     * Decrypts the client's X25519 private key using the Keystore-backed master key.
     */
    fun decryptPrivateKey(ciphertextBase64: String, ivBase64: String): ByteArray {
        try {
            val ciphertext = Base64.getDecoder().decode(ciphertextBase64.trim())
            val iv = Base64.getDecoder().decode(ivBase64.trim())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(128, iv))
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw SecurityException("Failed to decrypt private key", e)
        }
    }

    /**
     * Generates a new client X25519 keypair and returns public key + encrypted private key details.
     */
    fun generateClientKeys(): ClientKeyDetails {
        try {
            val privateKey = generatePrivateKeyRust()
            val publicKey = getPublicKeyRust(privateKey)
            val (encPrivKey, iv) = encryptPrivateKey(privateKey)
            return ClientKeyDetails(
                publicKeyHex = toHex(publicKey),
                encryptedPrivateKeyBase64 = encPrivKey,
                privateKeyIvBase64 = iv
            )
        } catch (e: UnsatisfiedLinkError) {
            throw SecurityException("Native crypto library not loaded", e)
        }
    }

    /**
     * Derives a shared symmetric key from the client's private key and agent's public key.
     */
    fun deriveSharedSecret(privateKey: ByteArray, peerPublicKeyHex: String): ByteArray {
        try {
            val peerPublicKey = fromHex(peerPublicKeyHex)
            return deriveSharedSecretRust(privateKey, peerPublicKey)
        } catch (e: UnsatisfiedLinkError) {
            throw SecurityException("Native crypto library not loaded", e)
        }
    }

    /**
     * Encrypts payload with AES-256-GCM using a per-request key derived via HKDF.
     */
    fun encryptPayload(plaintext: String, sharedKey: ByteArray, nonce: String, timestamp: Long): EncryptedPayload {
        try {
            val json = encryptPayloadRust(plaintext.toByteArray(Charsets.UTF_8), sharedKey, nonce, timestamp)
            val ct = json.substringAfter("\"ciphertext\":\"").substringBefore("\"")
            val iv = json.substringAfter("\"iv\":\"").substringBefore("\"")
            return EncryptedPayload(
                ciphertextBase64 = ct,
                ivBase64 = iv
            )
        } catch (e: UnsatisfiedLinkError) {
            throw SecurityException("Native crypto library not loaded", e)
        }
    }

    /**
     * Decrypts payload with AES-256-GCM using a per-request key derived via HKDF.
     */
    fun decryptPayload(ciphertextBase64: String, ivBase64: String, sharedKey: ByteArray, nonce: String, timestamp: Long): String {
        try {
            val decrypted = decryptPayloadRust(ciphertextBase64, ivBase64, sharedKey, nonce, timestamp)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: UnsatisfiedLinkError) {
            throw SecurityException("Native crypto library not loaded", e)
        }
    }

    /**
     * Computes an HMAC-SHA256 signature for signed mode requests.
     */
    fun computeSignature(
        timestamp: Long,
        nonce: String,
        ciphertextBase64: String,
        sharedKey: ByteArray
    ): String {
        try {
            return computeSignatureRust(timestamp, nonce, ciphertextBase64, sharedKey)
        } catch (e: UnsatisfiedLinkError) {
            throw SecurityException("Native crypto library not loaded", e)
        }
    }

    fun toHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun fromHex(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    /**
     * Pillar 6: Zero-Knowledge Key Backup.
     * Encrypts the raw private key using a key derived from a passphrase.
     * Returns a serialized string: "1:saltBase64:ivBase64:ciphertextBase64"
     */
    fun encryptForBackup(privateKey: ByteArray, passphrase: CharArray): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        
        val spec = PBEKeySpec(passphrase, salt, 600000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKeyBytes = factory.generateSecret(spec).encoded
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(privateKey)

        val encoder = Base64.getEncoder()
        return "1:${encoder.encodeToString(salt)}:${encoder.encodeToString(iv)}:${encoder.encodeToString(ciphertext)}"
    }

    /**
     * Pillar 6: Zero-Knowledge Key Backup.
     * Decrypts the raw private key from a serialized vault string using the passphrase.
     */
    fun decryptFromBackup(vaultData: String, passphrase: CharArray): ByteArray {
        val parts = vaultData.split(":")
        if (parts.size != 4 || parts[0] != "1") {
            throw IllegalArgumentException("Invalid vault format or unsupported version")
        }
        
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[1])
        val iv = decoder.decode(parts[2])
        val ciphertext = decoder.decode(parts[3])

        val spec = PBEKeySpec(passphrase, salt, 600000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKeyBytes = factory.generateSecret(spec).encoded
        val secretKey = SecretKeySpec(secretKeyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    companion object {
        init {
            try {
                System.loadLibrary("baton_crypto")
            } catch (e: UnsatisfiedLinkError) {
                // Silently ignore UnsatisfiedLinkError for JVM unit tests
            }
        }

        @JvmStatic
        private external fun generatePrivateKeyRust(): ByteArray

        @JvmStatic
        private external fun getPublicKeyRust(privateKey: ByteArray): ByteArray

        @JvmStatic
        private external fun deriveSharedSecretRust(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray

        @JvmStatic
        private external fun encryptPayloadRust(
            plaintext: ByteArray,
            sharedKey: ByteArray,
            nonce: String,
            timestamp: Long
        ): String

        @JvmStatic
        private external fun decryptPayloadRust(
            ciphertextBase64: String,
            ivBase64: String,
            sharedKey: ByteArray,
            nonce: String,
            timestamp: Long
        ): ByteArray

        @JvmStatic
        private external fun computeSignatureRust(
            timestamp: Long,
            nonce: String,
            ciphertextBase64: String,
            sharedKey: ByteArray
        ): String

        @JvmStatic
        external fun ratchetInitAliceRust(
            sharedSecret: ByteArray,
            peerPublic: ByteArray
        ): String

        @JvmStatic
        external fun ratchetInitBobRust(
            sharedSecret: ByteArray,
            bobKeypair: ByteArray
        ): String

        @JvmStatic
        external fun ratchetEncryptRust(
            stateJson: String,
            plaintext: ByteArray
        ): String

        @JvmStatic
        external fun ratchetDecryptRust(
            stateJson: String,
            headerPubB64: String,
            headerN: Int,
            headerPn: Int,
            ciphertextB64: String
        ): String
    }
}

data class ClientKeyDetails(
    val publicKeyHex: String,
    val encryptedPrivateKeyBase64: String,
    val privateKeyIvBase64: String
)

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)
