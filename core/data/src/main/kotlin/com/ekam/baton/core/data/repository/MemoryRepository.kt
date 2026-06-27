package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.model.Memory
import com.ekam.baton.core.data.model.toDomainModel
import com.ekam.baton.core.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.util.AuditCryptoUtils

class MemoryRepository constructor(
    private val memoryDao: MemoryDao,
    private val auditDao: AuditDao
) {
    fun getAllMemories(): Flow<List<Memory>> = memoryDao.getAllMemories().map { list -> list.map { it.toDomainModel() } }

    fun getMemoriesByLayer(layer: String): Flow<List<Memory>> = memoryDao.getMemoriesByLayer(layer).map { list -> list.map { it.toDomainModel() } }

    fun getMemoriesForAgent(agentId: String): Flow<List<Memory>> = memoryDao.getMemoriesForAgent(agentId).map { list -> list.map { it.toDomainModel() } }

    fun searchMemories(query: String): Flow<List<Memory>> = memoryDao.searchMemories(query).map { list -> list.map { it.toDomainModel() } }

    suspend fun upsertMemory(memory: Memory) {
        val entity = memory.toEntity()
        val lastAudit = auditDao.getLastAuditLog()
        val prevHash = lastAudit?.hash ?: ""
        val payload = """{"id":"${entity.id}","title":"${entity.title}","content":"${entity.content}","isActive":${entity.isActive}}"""
        val newHash = AuditCryptoUtils.generateHash(payload, prevHash)

        val hashedEntity = entity.copy(previousHash = prevHash, hash = newHash)
        memoryDao.upsertMemory(hashedEntity)

        auditDao.insertAuditLog(AuditLogEntity(
            entityName = "MemoryEntity",
            entityId = hashedEntity.id,
            action = "UPSERT",
            deviceId = "local_device",
            payloadJson = payload,
            previousHash = prevHash,
            hash = newHash
        ))
    }

    suspend fun deleteMemory(id: String) {
        val lastAudit = auditDao.getLastAuditLog()
        val prevHash = lastAudit?.hash ?: ""
        val payload = """{"id":"$id","deleted":true}"""
        val newHash = AuditCryptoUtils.generateHash(payload, prevHash)

        memoryDao.deleteMemory(id)

        auditDao.insertAuditLog(AuditLogEntity(
            entityName = "MemoryEntity",
            entityId = id,
            action = "DELETE",
            deviceId = "local_device",
            payloadJson = payload,
            previousHash = prevHash,
            hash = newHash
        ))
    }

    suspend fun toggleMemoryActive(id: String, isActive: Boolean) {
        val lastAudit = auditDao.getLastAuditLog()
        val prevHash = lastAudit?.hash ?: ""
        val payload = """{"id":"$id","isActive":$isActive}"""
        val newHash = AuditCryptoUtils.generateHash(payload, prevHash)

        memoryDao.toggleMemoryActive(id, isActive)

        auditDao.insertAuditLog(AuditLogEntity(
            entityName = "MemoryEntity",
            entityId = id,
            action = "UPDATE_ACTIVE",
            deviceId = "local_device",
            payloadJson = payload,
            previousHash = prevHash,
            hash = newHash
        ))
    }
}
