package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.AgentActionLogDao
import com.ekam.baton.core.data.db.entity.AgentActionLogEntity
import com.ekam.baton.core.data.util.AuditCryptoUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

        val dataToHash = buildJsonObject {
            put("promptId", promptId)
            put("agentId", agentId)
            put("actionType", actionType)
            put("payload", payloadJson)
        }.toString()
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
