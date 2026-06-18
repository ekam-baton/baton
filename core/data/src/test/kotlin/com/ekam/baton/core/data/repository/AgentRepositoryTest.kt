package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeAgentDao : AgentDao {

    private val agents = mutableListOf<AgentEntity>()
    private val flow = MutableStateFlow<List<AgentEntity>>(emptyList())

    private fun emit() {
        flow.value = agents.toList()
    }

    override fun getAllAgents(): Flow<List<AgentEntity>> = flow

    override suspend fun getAgentById(id: String): AgentEntity? =
        agents.firstOrNull { it.id == id }

    override suspend fun upsertAgent(agent: AgentEntity) {
        agents.removeAll { it.id == agent.id }
        agents.add(agent)
        emit()
    }

    override suspend fun deleteAgent(id: String) {
        agents.removeAll { it.id == id }
        emit()
    }

    override suspend fun updateLastUsed(id: String, timestamp: Long) {
        val index = agents.indexOfFirst { it.id == id }
        if (index != -1) {
            agents[index] = agents[index].copy(lastUsedAt = timestamp)
            emit()
        }
    }
}

class AgentRepositoryTest {

    private lateinit var fakeDao: FakeAgentDao
    private lateinit var repository: AgentRepository

    private fun agent(
        id: String = "agent-1",
        name: String = "Test Agent",
        description: String = "desc",
        mcpEndpointUrl: String = "https://example.com",
        authType: String = "none",
        authConfig: String = "{}",
        colorAccent: String = "#FF0000"
    ) = AgentEntity(
        id = id,
        name = name,
        description = description,
        mcpEndpointUrl = mcpEndpointUrl,
        authType = authType,
        authConfig = authConfig,
        colorAccent = colorAccent
    )

    @Before
    fun setUp() {
        fakeDao = FakeAgentDao()
        repository = AgentRepository(fakeDao)
    }

    // ── getAllAgents ──────────────────────────────────────────────

    @Test
    fun `getAllAgents returns empty flow initially`() = runTest {
        val result = repository.getAllAgents().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAllAgents returns flow of agents after insert`() = runTest {
        val a1 = agent(id = "1", name = "Alpha")
        val a2 = agent(id = "2", name = "Beta")
        fakeDao.upsertAgent(a1)
        fakeDao.upsertAgent(a2)

        val result = repository.getAllAgents().first()
        assertEquals(2, result.size)
        assertEquals("Alpha", result[0].name)
        assertEquals("Beta", result[1].name)
    }

    // ── getAgentById ─────────────────────────────────────────────

    @Test
    fun `getAgentById returns matching agent`() = runTest {
        val a = agent(id = "abc")
        fakeDao.upsertAgent(a)

        val result = repository.getAgentById("abc")
        assertNotNull(result)
        assertEquals("abc", result!!.id)
    }

    @Test
    fun `getAgentById returns null for unknown id`() = runTest {
        val result = repository.getAgentById("nonexistent")
        assertNull(result)
    }

    // ── upsertAgent ──────────────────────────────────────────────

    @Test
    fun `upsertAgent inserts new agent`() = runTest {
        val a = agent(id = "new-1", name = "New Agent")
        repository.upsertAgent(a)

        val result = repository.getAgentById("new-1")
        assertNotNull(result)
        assertEquals("New Agent", result!!.name)
    }

    @Test
    fun `upsertAgent updates existing agent`() = runTest {
        val original = agent(id = "u-1", name = "Original")
        repository.upsertAgent(original)

        val updated = original.copy(name = "Updated")
        repository.upsertAgent(updated)

        val result = repository.getAgentById("u-1")
        assertEquals("Updated", result!!.name)

        // Should still be only one entry
        val all = repository.getAllAgents().first()
        assertEquals(1, all.size)
    }

    // ── deleteAgent ──────────────────────────────────────────────

    @Test
    fun `deleteAgent removes agent`() = runTest {
        val a = agent(id = "del-1")
        repository.upsertAgent(a)
        repository.deleteAgent("del-1")

        assertNull(repository.getAgentById("del-1"))
        assertTrue(repository.getAllAgents().first().isEmpty())
    }

    @Test
    fun `deleteAgent with unknown id does nothing`() = runTest {
        val a = agent(id = "keep")
        repository.upsertAgent(a)
        repository.deleteAgent("nonexistent")

        assertEquals(1, repository.getAllAgents().first().size)
    }

    // ── updateLastUsed ───────────────────────────────────────────

    @Test
    fun `updateLastUsed updates timestamp for existing agent`() = runTest {
        val a = agent(id = "ts-1")
        repository.upsertAgent(a)

        val ts = 1_700_000_000_000L
        repository.updateLastUsed("ts-1", ts)

        val result = repository.getAgentById("ts-1")
        assertEquals(ts, result!!.lastUsedAt)
    }

    @Test
    fun `updateLastUsed with unknown id does nothing`() = runTest {
        repository.updateLastUsed("ghost", 123L)
        // No crash, no data
        assertTrue(repository.getAllAgents().first().isEmpty())
    }
}
