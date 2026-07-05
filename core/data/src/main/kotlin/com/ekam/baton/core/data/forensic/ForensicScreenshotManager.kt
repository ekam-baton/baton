package com.ekam.baton.core.data.forensic

import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONObject

class ForensicScreenshotManager(
    private val context: Context,
    private val cryptoManager: ForensicCryptoManager,
    private val timeProvider: TrustedTimeProvider
) {

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    /**
     * Captures a bitmap, immediately hashes it, signs the hash using hardware Keystore,
     * and writes both the PNG and a cryptographic manifest to the internal file system.
     * 
     * @param bitmap The screenshot or view bitmap to secure.
     * @param actionContext A description of what is being captured (e.g., "Agent Settings Screen").
     * @param userId The identity of the user triggering the capture.
     * @return The absolute path to the generated .baton_evidence package or manifest.
     */
    suspend fun secureAndStoreScreenshot(
        bitmap: Bitmap,
        actionContext: String,
        userId: String
    ): File = withContext(Dispatchers.IO) {
        val captureId = UUID.randomUUID().toString()
        val timestamp = timeProvider.getTrustedTimestamp()
        
        // 1. Create the forensic directory
        val evidenceDir = File(context.filesDir, "forensic_evidence").apply { mkdirs() }
        val imageFile = File(evidenceDir, "${captureId}.png")
        val manifestFile = File(evidenceDir, "${captureId}_manifest.json")

        // 2. Compress the bitmap to PNG in memory
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val imageBytes = outputStream.toByteArray()

        // 3. Generate cryptographic hash (Integrity)
        val imageHashBytes = cryptoManager.hashData(imageBytes)
        val imageHashHex = cryptoManager.bytesToHex(imageHashBytes)

        // 4. Sign the hash using hardware keystore (Non-Repudiation)
        val signatureBytes = cryptoManager.signData(imageHashBytes)
        val signatureHex = cryptoManager.bytesToHex(signatureBytes)

        // 5. Write the PNG to disk
        FileOutputStream(imageFile).use { it.write(imageBytes) }

        // 6. Write the cryptographic manifest
        val manifest = JSONObject().apply {
            put("capture_id", captureId)
            put("timestamp", timestamp)
            put("device_id", deviceId)
            put("user_id", userId)
            put("action_context", actionContext)
            put("image_hash_sha256", imageHashHex)
            put("hardware_signature_ecdsa", signatureHex)
            put("filename", imageFile.name)
        }

        FileOutputStream(manifestFile).use { 
            it.write(manifest.toString(4).toByteArray()) 
        }

        // Return the manifest file as the primary reference
        manifestFile
    }
}
