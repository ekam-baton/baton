package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.BatonDatabase

class WipeDataManager constructor(
    private val database: BatonDatabase
) {
    suspend fun wipeAllData() {
        database.clearAllTables()
    }

    suspend fun clearAllMemories() {
        database.memoryDao().clearAllMemories()
    }
}
