package com.ekam.baton.core.data.repository

import android.content.Context
import android.provider.Settings
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.forensic.ForensicCryptoManager
import com.ekam.baton.core.data.forensic.TrustedTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AuditRepository(
    private val auditDao: AuditDao,
    private val cryptoManager: ForensicCryptoManager,
    private val timeProvider: TrustedTimeProvider,
    private val context: Context
) {
    // For DISA STIG, we use Android's Secure Device ID if available, or generate one.
    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    /**
     * Appends a new event to the cryptographic Merkle tree ledger.
     * Ensures CISA/DISA STIG compliance by hashing and signing the payload.
     */
    suspend fun logEvent(
        entityName: String,
        entityId: String,
        action: String,
        payloadJson: String,
        userId: String
    ) = withContext(Dispatchers.IO) {
        // 1. Get the last log to form the Merkle chain
        val lastLog = auditDao.getLastAuditLog()
        val previousHash = lastLog?.hash ?: "GENESIS_HASH"

        // 2. Fetch forensically trusted NTP time
        val timestamp = timeProvider.getTrustedTimestamp()

        val id = UUID.randomUUID().toString()

        // 3. Create the payload string to hash
        val dataToHash = "$id|$entityName|$entityId|$action|$timestamp|$deviceId|$payloadJson|$previousHash|$userId"
        
        // 4. Generate the SHA-256 hash (Integrity)
        val hashBytes = cryptoManager.hashData(dataToHash.toByteArray())
        val currentHash = cryptoManager.bytesToHex(hashBytes)

        // 5. Digitally sign the hash using the Hardware-Backed Keystore (Non-Repudiation)
        val signatureBytes = cryptoManager.signData(hashBytes)
        val signatureHex = cryptoManager.bytesToHex(signatureBytes)

        // 6. Create and insert the immutable record
        val newLog = AuditLogEntity(
            id = id,
            entityName = entityName,
            entityId = entityId,
            action = action,
            timestamp = timestamp,
            deviceId = deviceId,
            payloadJson = payloadJson,
            previousHash = previousHash,
            hash = currentHash,
            signature = signatureHex,
            userId = userId
        )

        auditDao.insertAuditLog(newLog)
    }
}
