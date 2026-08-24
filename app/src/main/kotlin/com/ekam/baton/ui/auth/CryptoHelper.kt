package com.ekam.baton.ui.auth

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private const val ITERATION_COUNT = 600_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128

    fun encryptBackupKey(password: String, keyToBackup: ByteArray): String {
        val secureRandom = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val ciphertext = cipher.doFinal(keyToBackup)

        // Format: 1:salt:iv:ciphertext (base64 encoded)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        return "1:$saltB64:$ivB64:$cipherB64"
    }

    fun decryptBackupKey(password: String, encryptedPayload: String): ByteArray {
        val parts = encryptedPayload.split(":")
        
        val (saltB64, ivB64, cipherB64, iterations) = if (parts.size == 4 && parts[0] == "1") {
            // New format: 1:salt:iv:ciphertext
            Tuple4(parts[1], parts[2], parts[3], 600_000)
        } else if (parts.size == 3) {
            // Legacy format: salt:iv:ciphertext
            Tuple4(parts[0], parts[1], parts[2], 100_000)
        } else {
            throw IllegalArgumentException("Invalid backup payload format")
        }

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(cipherB64, Base64.NO_WRAP)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return cipher.doFinal(ciphertext)
    }
}

private data class Tuple4<A, B, C, D>(val v1: A, val v2: B, val v3: C, val v4: D)
