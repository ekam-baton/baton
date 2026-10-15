package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.BatonDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WipeDataManager constructor(
    private val database: BatonDatabase
) {
    suspend fun wipeAllData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }

    suspend fun clearAllMemories() = withContext(Dispatchers.IO) {
        database.memoryDao().clearAllMemories()
    }
}
