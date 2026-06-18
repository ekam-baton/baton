package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepository @Inject constructor(
    private val agentDao: AgentDao
) {
    fun getAllAgents(): Flow<List<AgentEntity>> {
        return agentDao.getAllAgents()
    }

    suspend fun getAgentById(id: String): AgentEntity? {
        return agentDao.getAgentById(id)
    }

    suspend fun upsertAgent(agent: AgentEntity) {
        agentDao.upsertAgent(agent)
    }

    suspend fun deleteAgent(id: String) {
        agentDao.deleteAgent(id)
    }

    suspend fun updateLastUsed(id: String, timestamp: Long) {
        agentDao.updateLastUsed(id, timestamp)
    }
}
