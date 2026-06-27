package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.toDomainModel
import com.ekam.baton.core.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AgentRepository constructor(
    private val agentDao: AgentDao
) {
    fun getAllAgents(): Flow<List<Agent>> {
        return agentDao.getAllAgents().map { list -> list.map { it.toDomainModel() } }
    }

    suspend fun getAgentById(id: String): Agent? {
        return agentDao.getAgentById(id)?.toDomainModel()
    }

    suspend fun upsertAgent(agent: Agent) {
        agentDao.upsertAgent(agent.toEntity())
    }

    suspend fun deleteAgent(id: String) {
        agentDao.deleteAgent(id)
    }

    suspend fun updateLastUsed(id: String, timestamp: Long) {
        agentDao.updateLastUsed(id, timestamp)
    }
}
