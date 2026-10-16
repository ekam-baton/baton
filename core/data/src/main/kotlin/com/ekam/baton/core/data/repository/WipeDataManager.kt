package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.BatonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class WipeDataManager constructor(
    private val database: BatonDatabase? = null
) {
    open suspend fun wipeAllData() = withContext(Dispatchers.IO) {
        database?.clearAllTables()
    }

    open suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        database?.memoryDao()?.clearAllMemories()
    }

    open suspend fun emergencyCryptographicShred() = withContext(Dispatchers.IO) {
        try {
            database?.clearAllTables()
        } catch (_: Exception) {}

        try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.contains("baton", ignoreCase = true) || alias.contains("androidx", ignoreCase = true)) {
                    keyStore.deleteEntry(alias)
                }
            }
        } catch (_: Exception) {}
    }
}
