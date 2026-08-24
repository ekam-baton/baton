package com.ekam.baton.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class CloudBackupViewModel : ViewModel() {

    fun backupToCloud(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Generate a dummy byte array to backup
                val dummyData = "This is dummy backup data".toByteArray(Charsets.UTF_8)
                
                // 2. Derive a key from password (simplified for stub, normally use Argon2 or PBKDF2)
                val md = MessageDigest.getInstance("SHA-256")
                val keyBytes = md.digest(password.toByteArray(Charsets.UTF_8))
                val secretKey = SecretKeySpec(keyBytes, "AES")
                
                // 3. Encrypt using AES-GCM
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(12)
                SecureRandom().nextBytes(iv)
                val parameterSpec = GCMParameterSpec(128, iv)
                
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
                val encryptedData = cipher.doFinal(dummyData)
                
                // Combine IV and encrypted data
                val payload = iv + encryptedData
                
                // 4. POST to Identity Server /backups/upload
                val url = URL("https://identity.ekam.com/backups/upload")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/octet-stream")
                
                connection.outputStream.use { os ->
                    os.write(payload)
                }
                
                val responseCode = connection.responseCode
                println("Cloud backup response code: $responseCode")
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
