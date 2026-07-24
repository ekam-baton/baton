package com.ekam.baton.core.network.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey

/**
 * Manages the long-term Ed25519 Identity Key used for Safety Number Verification.
 * Stores the key securely in the Android Keystore.
 */
object IdentityKeyManager {
    private const val ALIAS = "baton_ed25519_identity"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    fun getOrGenerateIdentityKey(): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        
        if (keyStore.containsAlias(ALIAS)) {
            val entry = keyStore.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
            if (entry != null) {
                return KeyPair(entry.certificate.publicKey, entry.privateKey)
            }
        }
        
        return generateNewIdentityKey()
    }

    private fun generateNewIdentityKey(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Ed25519", ANDROID_KEYSTORE)
        val parameterSpec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).build()
        kpg.initialize(parameterSpec)
        return kpg.generateKeyPair()
    }

    /**
     * Extracts the raw 32-byte Ed25519 public key.
     * Java's getEncoded() returns an ASN.1 SubjectPublicKeyInfo. For Ed25519, 
     * the actual 32-byte key is the last 32 bytes of this encoded structure.
     */
    fun getRawPublicKey(publicKey: PublicKey): ByteArray {
        val encoded = publicKey.encoded
        // SubjectPublicKeyInfo for Ed25519 is 44 bytes, the last 32 bytes are the raw key
        return if (encoded.size >= 32) {
            encoded.copyOfRange(encoded.size - 32, encoded.size)
        } else {
            encoded
        }
    }
    
    fun getPublicKeyHex(): String {
        val kp = getOrGenerateIdentityKey()
        val rawBytes = getRawPublicKey(kp.public)
        return rawBytes.joinToString("") { "%02x".format(it) }
    }
}
