package com.ekam.baton.core.data.forensic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class EnterpriseCertificateManager(private val context: Context) {

    private val certDir by lazy { File(context.filesDir, "qtsp_certs").apply { mkdirs() } }
    private val keyStoreFile by lazy { File(certDir, "enterprise_keystore.bks") }
    private val ksPassword = "baton_qtsp_secure_password".toCharArray() // In production, this would be injected via MDM or user prompt

    /**
     * Checks if a Qualified Trust Service Provider (QTSP) certificate is installed.
     * If true, BATON can upgrade from AES (Advanced Electronic Signature) 
     * to QES (Qualified Electronic Signature) under the eIDAS regulation.
     */
    fun hasQualifiedCertificate(): Boolean {
        return keyStoreFile.exists() && getEnterprisePrivateKey() != null
    }

    /**
     * Injects a third-party PKCS#12 or BKS keystore containing a Qualified Certificate.
     * This is typically called by an MDM policy or enterprise config payload.
     */
    suspend fun injectQualifiedCertificate(keystoreBytes: ByteArray) = withContext(Dispatchers.IO) {
        val fos = File(certDir, "enterprise_keystore.bks").outputStream()
        fos.use { it.write(keystoreBytes) }
    }

    /**
     * Retrieves the QTSP private key for signing.
     */
    fun getEnterprisePrivateKey(): PrivateKey? {
        if (!keyStoreFile.exists()) return null
        return try {
            val ks = KeyStore.getInstance("BKS")
            FileInputStream(keyStoreFile).use { fis ->
                ks.load(fis, ksPassword)
            }
            val alias = ks.aliases().toList().firstOrNull() ?: return null
            ks.getKey(alias, ksPassword) as? PrivateKey
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
