package com.ekam.baton.core.data.forensic

import android.content.Context
import com.ekam.baton.core.data.db.dao.AuditDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EvidenceExportManager(
    private val context: Context,
    private val auditDao: AuditDao,
    private val cryptoManager: ForensicCryptoManager
) {

    /**
     * Packages all cryptographic logs and screenshots into a standard ZIP archive.
     * Generates a detached cryptographic signature (.sig) for the ZIP archive.
     * This aligns with ISO/IEC 27037 by using globally standardized verification formats.
     * 
     * @return A Pair containing (Archive Zip File, Detached Signature File).
     */
    suspend fun generateEvidencePackage(): Pair<File, File> = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "evidence_exports").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()
        val archiveFile = File(exportDir, "baton_evidence_$timestamp.zip")
        val signatureFile = File(exportDir, "baton_evidence_$timestamp.sig")

        // 1. Export Audit Logs from the Merkle Tree database
        val logs = auditDao.getAllAuditLogsSync()
        val logsJsonArray = JSONArray()
        logs.forEach { log ->
            logsJsonArray.put(JSONObject().apply {
                put("id", log.id)
                put("timestamp", log.timestamp)
                put("user_id", log.userId)
                put("action_type", log.action)
                put("payload", log.payloadJson)
                put("previous_hash", log.previousHash)
                put("current_hash", log.hash)
                put("hardware_signature", log.signature)
            })
        }

        val logsFile = File(exportDir, "audit_ledger.json")
        FileOutputStream(logsFile).use { it.write(logsJsonArray.toString(4).toByteArray()) }

        // 2. Gather forensic screenshots
        val screenshotDir = File(context.filesDir, "forensic_evidence")
        val screenshotFiles = screenshotDir.listFiles()?.toList() ?: emptyList()

        // 3. Zip everything up into standard format
        ZipOutputStream(FileOutputStream(archiveFile)).use { zout ->
            // Add logs
            zout.putNextEntry(ZipEntry("audit_ledger.json"))
            FileInputStream(logsFile).copyTo(zout)
            zout.closeEntry()

            // Add screenshots and manifests
            screenshotFiles.forEach { file ->
                zout.putNextEntry(ZipEntry("screenshots/${file.name}"))
                FileInputStream(file).copyTo(zout)
                zout.closeEntry()
            }
        }

        // 4. Cryptographically sign the ZIP package for Chain of Custody (Detached Signature)
        val zipBytes = archiveFile.readBytes()
        val packageHash = cryptoManager.hashData(zipBytes)
        val packageSignature = cryptoManager.signData(packageHash)

        // 5. Write the detached signature file
        FileOutputStream(signatureFile).use { fout ->
            fout.write(packageSignature)
        }

        // Cleanup temp files
        logsFile.delete()

        Pair(archiveFile, signatureFile)
    }
}
