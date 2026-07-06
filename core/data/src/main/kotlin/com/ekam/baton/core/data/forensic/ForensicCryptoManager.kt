package com.ekam.baton.core.data.forensic

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature

class ForensicCryptoManager(private val certificateManager: EnterpriseCertificateManager) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    init {
        generateKeyIfNotExists()
    }

    private fun generateKeyIfNotExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()

            keyPairGenerator.initialize(parameterSpec)
            keyPairGenerator.generateKeyPair()
        }
    }

    /**
     * Generates a SHA-256 cryptographic hash of the provided data.
     */
    fun hashData(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }

    /**
     * Digitally signs the data. 
     * If an Enterprise QTSP certificate is present, it uses that for eIDAS QES compliance.
     * Otherwise, it falls back to the hardware-backed Android Keystore key (AES compliance).
     */
    fun signData(data: ByteArray): ByteArray {
        val privateKey = certificateManager.getEnterprisePrivateKey() 
            ?: (keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey)
        
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    /**
     * Verifies the signature of the data. 
     * NOTE: Currently only verifies the local Keystore. For full QTSP verification, 
     * the enterprise certificate chain would be checked here.
     */
    fun verifySignature(data: ByteArray, signatureBytes: ByteArray): Boolean {
        val certificate = keyStore.getCertificate(KEY_ALIAS)
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(certificate)
        signature.update(data)
        return signature.verify(signatureBytes)
    }

    /**
     * Converts a ByteArray to a Hex String.
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "BatonForensicKey"
    }
}
