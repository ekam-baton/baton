package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.toDomainModel
import com.ekam.baton.core.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class AgentRepository constructor(
    private val agentDao: AgentDao
) {
    open fun getAllAgents(): Flow<List<Agent>> {
        return agentDao.getAllAgents().map { list -> list.map { it.toDomainModel() } }
    }

    open suspend fun getAgentById(id: String): Agent? {
        return agentDao.getAgentById(id)?.toDomainModel()
    }

    open suspend fun upsertAgent(agent: Agent) {
        agentDao.upsertAgent(agent.toEntity())
    }

    open suspend fun deleteAgent(id: String) {
        agentDao.deleteAgent(id)
    }

    open suspend fun updateLastUsed(id: String, timestamp: Long) {
        agentDao.updateLastUsed(id, timestamp)
    }

    open suspend fun setBlocked(id: String, isBlocked: Boolean) {
        agentDao.updateBlockedStatus(id, isBlocked)
    }

    open fun isAgentBlocked(id: String): Flow<Boolean> {
        return agentDao.isAgentBlocked(id).map { it ?: false }
    }
}
