package com.ekam.baton.feature.memory

import androidx.lifecycle.SavedStateHandle
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.db.entity.MemoryEntity
import com.ekam.baton.core.data.model.Memory
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.repository.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeAgents = listOf(
        AgentEntity(
            id = "agent-1",
            name = "Architect Prime",
            description = "Systems Engineer",
            mcpEndpointUrl = "https://agent1.local",
            authType = "NONE",
            authConfig = "{}",
            colorAccent = "#306EE8",
            securityMode = "ENFORCE",
            securityConfig = "{}"
        ),
        AgentEntity(
            id = "agent-2",
            name = "Sentinel Alpha",
            description = "Security Guardian",
            mcpEndpointUrl = "https://agent2.local",
            authType = "NONE",
            authConfig = "{}",
            colorAccent = "#00D2FF",
            securityMode = "ENFORCE",
            securityConfig = "{}"
        )
    )

    private val fakeInitialMemories = listOf(
        MemoryEntity(
            id = "mem-w-1",
            layer = "working",
            agentId = "agent-1",
            conversationId = "conv-1",
            title = "Current Session Variable",
            content = "Target port is 8080",
            createdAt = 1000L,
            lastAccessedAt = 1000L,
            relevanceScore = 0.8f,
            tags = "[\"session\",\"temp\"]",
            isActive = true
        ),
        MemoryEntity(
            id = "mem-e-1",
            layer = "episodic",
            agentId = "agent-1",
            conversationId = null,
            title = "Deployment Encounter",
            content = "Successfully migrated staging cluster",
            createdAt = 2000L,
            lastAccessedAt = 2000L,
            relevanceScore = 0.9f,
            tags = "[\"cluster\",\"deployment\"]",
            isActive = true
        ),
        MemoryEntity(
            id = "mem-s-1",
            layer = "semantic",
            agentId = null, // Global Knowledge
            conversationId = null,
            title = "User Preference",
            content = "Always format responses in GitHub-flavored markdown",
            createdAt = 3000L,
            lastAccessedAt = 3000L,
            relevanceScore = 1.0f,
            tags = "[\"preference\",\"formatting\"]",
            isActive = true
        )
    )

    private class FakeMemoryDao(initialList: List<MemoryEntity>) : MemoryDao {
        val memories = initialList.toMutableList()
        private val flow = MutableStateFlow<List<MemoryEntity>>(memories.toList())

        private fun emit() {
            flow.value = memories.toList()
        }

        override fun getAllMemories(): Flow<List<MemoryEntity>> = flow
        override fun getMemoriesByLayer(layer: String): Flow<List<MemoryEntity>> = flow
        override fun getMemoriesForAgent(agentId: String): Flow<List<MemoryEntity>> = flow
        override fun searchMemories(query: String): Flow<List<MemoryEntity>> = flow
        override suspend fun upsertMemory(memory: MemoryEntity) {
            memories.removeAll { it.id == memory.id }
            memories.add(memory)
            emit()
        }
        override suspend fun deleteMemory(id: String) {
            memories.removeAll { it.id == id }
            emit()
        }
        override suspend fun toggleMemoryActive(id: String, isActive: Boolean) {
            val idx = memories.indexOfFirst { it.id == id }
            if (idx != -1) {
                memories[idx] = memories[idx].copy(isActive = isActive)
                emit()
            }
        }
        override suspend fun getTopSemanticMemories(agentId: String): List<MemoryEntity> = emptyList()
        override suspend fun getRecentEpisodicMemories(agentId: String, cutoffTime: Long): List<MemoryEntity> = emptyList()
        override suspend fun getWorkingMemories(conversationId: String): List<MemoryEntity> = emptyList()
        override suspend fun updateLastAccessedTime(ids: List<String>, time: Long) {}
        override suspend fun clearWorkingMemoriesForConversation(conversationId: String) {}
        override suspend fun clearAllMemories() {
            memories.clear()
            emit()
        }
        override suspend fun deleteMemoriesOlderThan(cutoffTime: Long) {
            memories.removeAll { it.createdAt < cutoffTime }
            emit()
        }
    }

    private class FakeAuditDao : AuditDao {
        override suspend fun insertAuditLog(log: AuditLogEntity) {}
        override fun getAllAuditLogs(): Flow<List<AuditLogEntity>> = flowOf(emptyList())
        override suspend fun getLastAuditLog(): AuditLogEntity? = null
        override suspend fun getAllAuditLogsSync(): List<AuditLogEntity> = emptyList()
        override suspend fun pruneOldLogs(keepLastN: Int) {}
    }

    private class FakeAgentDao(val agents: List<AgentEntity>) : AgentDao {
        override fun getAllAgents(): Flow<List<AgentEntity>> = flowOf(agents)
        override suspend fun getAgentById(id: String): AgentEntity? = agents.find { it.id == id }
        override suspend fun upsertAgent(agent: AgentEntity) {}
        override suspend fun deleteAgent(id: String) {}
        override suspend fun updateLastUsed(id: String, timestamp: Long) {}
        override suspend fun updateBlockedStatus(id: String, isBlocked: Boolean) {}
        override fun isAgentBlocked(id: String): Flow<Boolean?> = flowOf(false)
    }

    private lateinit var memoryDao: FakeMemoryDao
    private lateinit var auditDao: FakeAuditDao
    private lateinit var agentDao: FakeAgentDao
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var agentRepository: AgentRepository
    private lateinit var viewModel: MemoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        memoryDao = FakeMemoryDao(fakeInitialMemories)
        auditDao = FakeAuditDao()
        agentDao = FakeAgentDao(fakeAgents)
        memoryRepository = MemoryRepository(memoryDao, auditDao)
        agentRepository = AgentRepository(agentDao)
        viewModel = MemoryViewModel(memoryRepository, agentRepository, SavedStateHandle())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test agents and memory layers correctly grouped and populated`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val agents = viewModel.agents.first()
        assertEquals(2, agents.size)
        assertEquals("Architect Prime", agents[0].name)

        val layers = viewModel.memoriesByLayer.first()
        assertEquals(1, layers["working"]?.size)
        assertEquals(1, layers["episodic"]?.size)
        assertEquals(1, layers["semantic"]?.size)

        val counts = viewModel.layerCounts.first()
        assertEquals(1, counts["working"])
        assertEquals(1, counts["episodic"])
        assertEquals(1, counts["semantic"])
    }

    @Test
    fun `test agent filter isolates agent specific and global memories`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Filter by agent-1: should see agent-1 memories + global memory (mem-s-1)
        viewModel.setAgentFilter("agent-1")
        testDispatcher.scheduler.advanceUntilIdle()
        var layers = viewModel.memoriesByLayer.first()
        assertEquals(1, layers["working"]?.size)
        assertEquals(1, layers["episodic"]?.size)
        assertEquals(1, layers["semantic"]?.size)

        // Filter by agent-2: agent-2 has no working/episodic memories, but sees global semantic
        viewModel.setAgentFilter("agent-2")
        testDispatcher.scheduler.advanceUntilIdle()
        layers = viewModel.memoriesByLayer.first()
        assertNull(layers["working"])
        assertNull(layers["episodic"])
        assertEquals(1, layers["semantic"]?.size)

        // Reset filter
        viewModel.setAgentFilter(null)
        testDispatcher.scheduler.advanceUntilIdle()
        layers = viewModel.memoriesByLayer.first()
        assertEquals(1, layers["working"]?.size)
        assertEquals(1, layers["episodic"]?.size)
        assertEquals(1, layers["semantic"]?.size)
    }

    @Test
    fun `test search query matches title, content and tags`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        // Match content
        viewModel.updateSearchQuery("8080")
        testDispatcher.scheduler.advanceUntilIdle()
        var layers = viewModel.memoriesByLayer.first()
        assertEquals(1, layers["working"]?.size)
        assertNull(layers["episodic"])

        // Match tag
        viewModel.updateSearchQuery("deployment")
        testDispatcher.scheduler.advanceUntilIdle()
        layers = viewModel.memoriesByLayer.first()
        assertNull(layers["working"])
        assertEquals(1, layers["episodic"]?.size)

        // Reset query
        viewModel.updateSearchQuery("")
        testDispatcher.scheduler.advanceUntilIdle()
        layers = viewModel.memoriesByLayer.first()
        assertEquals(3, layers.values.sumOf { it.size })
    }

    @Test
    fun `test add, toggle and delete memory`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addMemory(
            layer = "semantic",
            agentId = "agent-2",
            title = "Security Standard",
            content = "Use mutual TLS everywhere",
            tags = listOf("security", "mtls")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val layers = viewModel.memoriesByLayer.first()
        assertEquals(2, layers["semantic"]?.size)

        // Toggle active
        viewModel.toggleActive("mem-w-1", false)
        testDispatcher.scheduler.advanceUntilIdle()
        val working = viewModel.memoriesByLayer.first()["working"]?.first()
        assertFalse(working!!.isActive)

        // Delete memory
        viewModel.deleteMemory("mem-w-1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.memoriesByLayer.first()["working"])
    }

    @Test
    fun `test promoteToSemantic elevates episodic memory to permanent knowledge`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val episodicMemory = viewModel.memoriesByLayer.first()["episodic"]!!.first()
        assertEquals("episodic", episodicMemory.layer)

        viewModel.promoteToSemantic(episodicMemory)
        testDispatcher.scheduler.advanceUntilIdle()

        val semanticMemories = viewModel.memoriesByLayer.first()["semantic"]!!
        assertTrue(semanticMemories.any { it.id == episodicMemory.id && it.layer == "semantic" })
        val promoted = semanticMemories.first { it.id == episodicMemory.id }
        assertEquals(1.0f, promoted.relevanceScore, 0.001f)
    }

    @Test
    fun `test consolidateWorkingMemories creates episode and archives working memories`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val workingMemories = viewModel.memoriesByLayer.first()["working"]!!
        assertEquals(1, workingMemories.size)

        viewModel.consolidateWorkingMemories(
            workingMemories = workingMemories,
            episodeTitle = "Consolidated Debug Session",
            episodeSummary = "Configured target port to 8080 and validated tunnel"
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Working memory should now be marked inactive
        val updatedWorking = viewModel.memoriesByLayer.first()["working"]!!.first()
        assertFalse(updatedWorking.isActive)

        // 2. An episodic memory should now be present
        val episodicMemories = viewModel.memoriesByLayer.first()["episodic"]!!
        assertEquals(2, episodicMemories.size)
        val synthesized = episodicMemories.first { it.title == "Consolidated Debug Session" }
        assertEquals("Configured target port to 8080 and validated tunnel", synthesized.content)
        assertEquals(0.95f, synthesized.relevanceScore, 0.001f)
    }
}
