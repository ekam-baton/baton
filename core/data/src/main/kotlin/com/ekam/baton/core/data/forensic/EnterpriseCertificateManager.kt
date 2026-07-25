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

class EnterpriseCertificateManager(
    private val context: Context,
    private val appPreferences: com.ekam.baton.core.data.preferences.AppPreferences
) {

    private val certDir by lazy { File(context.filesDir, "qtsp_certs").apply { mkdirs() } }
    private val keyStoreFile by lazy { File(certDir, "enterprise_keystore.bks") }

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
    suspend fun injectQualifiedCertificate(keystoreBytes: ByteArray, password: CharArray) = withContext(Dispatchers.IO) {
        val fos = File(certDir, "enterprise_keystore.bks").outputStream()
        fos.use { it.write(keystoreBytes) }
        appPreferences.qtspPassword = password
        android.util.Log.d("EnterpriseCertMgr", "Injected QTSP certificate successfully.")
    }

    /**
     * Retrieves the QTSP private key for signing.
     */
    fun getEnterprisePrivateKey(): PrivateKey? {
        if (!keyStoreFile.exists()) return null
        return try {
            val ksPassword = appPreferences.qtspPassword ?: return null
            val ks = KeyStore.getInstance("BKS")
            FileInputStream(keyStoreFile).use { fis ->
                ks.load(fis, ksPassword)
            }
            val alias = ks.aliases().toList().firstOrNull() ?: return null
            val key = ks.getKey(alias, ksPassword) as? PrivateKey
            if (key != null) {
                android.util.Log.d("EnterpriseCertMgr", "Successfully loaded QTSP private key for alias: $alias")
            } else {
                android.util.Log.w("EnterpriseCertMgr", "Private key not found for alias: $alias")
            }
            key
        } catch (e: Exception) {
            android.util.Log.e("EnterpriseCertMgr", "Failed to retrieve enterprise private key", e)
            null
        }
    }
}
