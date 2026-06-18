package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao
) {
    fun getAllMemories(): Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    fun getMemoriesByLayer(layer: String): Flow<List<MemoryEntity>> = memoryDao.getMemoriesByLayer(layer)

    fun getMemoriesForAgent(agentId: String): Flow<List<MemoryEntity>> = memoryDao.getMemoriesForAgent(agentId)

    fun searchMemories(query: String): Flow<List<MemoryEntity>> = memoryDao.searchMemories(query)

    suspend fun upsertMemory(memory: MemoryEntity) {
        memoryDao.upsertMemory(memory)
    }

    suspend fun deleteMemory(id: String) {
        memoryDao.deleteMemory(id)
    }

    suspend fun toggleMemoryActive(id: String, isActive: Boolean) {
        memoryDao.toggleMemoryActive(id, isActive)
    }
}
