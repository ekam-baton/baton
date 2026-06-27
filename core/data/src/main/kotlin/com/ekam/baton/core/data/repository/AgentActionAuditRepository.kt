package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.AgentActionLogDao
import com.ekam.baton.core.data.db.entity.AgentActionLogEntity
import com.ekam.baton.core.data.util.AuditCryptoUtils
import kotlinx.coroutines.flow.Flow

class AgentActionAuditRepository(
    private val agentActionLogDao: AgentActionLogDao
) {

    suspend fun logAgentAction(
        promptId: String,
        agentId: String,
        actionType: String,
        payloadJson: String
    ) {
        val lastLog = agentActionLogDao.getLastAgentActionLog()
        val prevHash = lastLog?.hash ?: ""

        val dataToHash = """{"promptId":"$promptId","agentId":"$agentId","actionType":"$actionType","payload":"$payloadJson"}"""
        val newHash = AuditCryptoUtils.generateHash(dataToHash, prevHash)

        val logEntry = AgentActionLogEntity(
            promptId = promptId,
            agentId = agentId,
            actionType = actionType,
            payloadJson = payloadJson,
            previousHash = prevHash,
            hash = newHash
        )

        agentActionLogDao.insertAgentActionLog(logEntry)
    }

    fun getAllLogs(): Flow<List<AgentActionLogEntity>> {
        return agentActionLogDao.getAllAgentActionLogs()
    }
}
