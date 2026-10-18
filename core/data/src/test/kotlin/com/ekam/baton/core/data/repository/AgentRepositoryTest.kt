package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.ConversationDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.ConversationEntity
import com.ekam.baton.core.data.db.entity.MessageEntity
import com.ekam.baton.core.data.db.entity.MemoryEntity
import com.ekam.baton.core.data.model.toDomainModel
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

    override suspend fun updateBlockedStatus(id: String, isBlocked: Boolean) {
        val index = agents.indexOfFirst { it.id == id }
        if (index != -1) {
            agents[index] = agents[index].copy(isBlocked = isBlocked)
            emit()
        }
    }

    override fun isAgentBlocked(id: String): Flow<Boolean?> =
        kotlinx.coroutines.flow.flowOf(agents.firstOrNull { it.id == id }?.isBlocked)
}

class FakeAgentRepoConversationDao : ConversationDao {
    val conversations = mutableMapOf<String, ConversationEntity>()
    override fun getAllConversations(query: String): androidx.paging.PagingSource<Int, ConversationEntity> =
        error("Not implemented")
    override suspend fun getConversationById(id: String): ConversationEntity? = conversations[id]
    override suspend fun getConversationByAgentId(agentId: String): ConversationEntity? =
        conversations.values.find { it.agentId == agentId }
    override suspend fun upsertConversation(conversation: ConversationEntity) {
        conversations[conversation.id] = conversation
    }
    override suspend fun deleteConversation(id: String) {
        conversations.remove(id)
    }
}

class FakeAgentRepoMessageDao : MessageDao {
    val messages = mutableListOf<MessageEntity>()
    override fun getMessagesForConversation(conversationId: String): androidx.paging.PagingSource<Int, MessageEntity> =
        error("Not implemented")
    override fun observeMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> =
        kotlinx.coroutines.flow.flowOf(messages.filter { it.conversationId == conversationId })
    override suspend fun insertMessage(message: MessageEntity) {
        messages.add(message)
    }
    override suspend fun updateMessage(message: MessageEntity) {
        val idx = messages.indexOfFirst { it.id == message.id }
        if (idx != -1) messages[idx] = message
    }
    override suspend fun clearStreamingStates() {}
    override suspend fun getLastNMessages(conversationId: String, n: Int): List<MessageEntity> =
        messages.filter { it.conversationId == conversationId }.takeLast(n)
    override suspend fun getMessageById(id: String): MessageEntity? = messages.find { it.id == id }
    override suspend fun deleteMessage(id: String) {
        messages.removeAll { it.id == id }
    }
    override suspend fun deleteMessagesForConversation(conversationId: String) {
        messages.removeAll { it.conversationId == conversationId }
    }
    override suspend fun getFailedMessages(): List<MessageEntity> =
        messages.filter { it.isFailed }
    override suspend fun getFailedMessagesForConversation(conversationId: String): List<MessageEntity> =
        messages.filter { it.conversationId == conversationId && it.isFailed }
}

class FakeAgentRepoMemoryDao : MemoryDao {
    val memories = mutableListOf<MemoryEntity>()
    override fun getAllMemories(): Flow<List<MemoryEntity>> = kotlinx.coroutines.flow.flowOf(memories)
    override fun getMemoriesByLayer(layer: String): Flow<List<MemoryEntity>> =
        kotlinx.coroutines.flow.flowOf(memories.filter { it.layer == layer })
    override fun getMemoriesForAgent(agentId: String): Flow<List<MemoryEntity>> =
        kotlinx.coroutines.flow.flowOf(memories.filter { it.agentId == agentId || it.agentId == null })
    override fun searchMemories(query: String): Flow<List<MemoryEntity>> = kotlinx.coroutines.flow.flowOf(memories)
    override suspend fun upsertMemory(memory: MemoryEntity) {
        memories.removeAll { it.id == memory.id }
        memories.add(memory)
    }
    override suspend fun deleteMemory(id: String) {
        memories.removeAll { it.id == id }
    }
    override suspend fun deleteMemoriesForAgent(agentId: String) {
        memories.removeAll { it.agentId == agentId }
    }
    override suspend fun toggleMemoryActive(id: String, isActive: Boolean) {}
    override suspend fun getTopSemanticMemories(agentId: String): List<MemoryEntity> = emptyList()
    override suspend fun getRecentEpisodicMemories(agentId: String, cutoffTime: Long): List<MemoryEntity> = emptyList()
    override suspend fun getWorkingMemories(conversationId: String): List<MemoryEntity> = emptyList()
    override suspend fun updateLastAccessedTime(ids: List<String>, time: Long) {}
    override suspend fun clearWorkingMemoriesForConversation(conversationId: String) {}
    override suspend fun clearAllMemories() { memories.clear() }
    override suspend fun deleteMemoriesOlderThan(cutoffTime: Long) {}
}

class AgentRepositoryTest {

    private lateinit var fakeDao: FakeAgentDao
    private lateinit var fakeConversationDao: FakeAgentRepoConversationDao
    private lateinit var fakeMessageDao: FakeAgentRepoMessageDao
    private lateinit var fakeMemoryDao: FakeAgentRepoMemoryDao
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
        fakeConversationDao = FakeAgentRepoConversationDao()
        fakeMessageDao = FakeAgentRepoMessageDao()
        fakeMemoryDao = FakeAgentRepoMemoryDao()
        repository = AgentRepository(
            agentDao = fakeDao,
            conversationDao = fakeConversationDao,
            messageDao = fakeMessageDao,
            memoryDao = fakeMemoryDao
        )
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
        repository.upsertAgent(a.toDomainModel())

        val result = repository.getAgentById("new-1")
        assertNotNull(result)
        assertEquals("New Agent", result!!.name)
    }

    @Test
    fun `upsertAgent updates existing agent`() = runTest {
        val original = agent(id = "u-1", name = "Original")
        repository.upsertAgent(original.toDomainModel())

        val updated = original.copy(name = "Updated")
        repository.upsertAgent(updated.toDomainModel())

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
        repository.upsertAgent(a.toDomainModel())
        repository.deleteAgent("del-1")

        assertNull(repository.getAgentById("del-1"))
        assertTrue(repository.getAllAgents().first().isEmpty())
    }

    @Test
    fun `deleteAgent with unknown id does nothing`() = runTest {
        val a = agent(id = "keep")
        repository.upsertAgent(a.toDomainModel())
        repository.deleteAgent("nonexistent")

        assertEquals(1, repository.getAllAgents().first().size)
    }

    @Test
    fun `deleteAgent cascades to delete conversations, messages, and memories`() = runTest {
        val agentId = "agent-cascade"
        val a = agent(id = agentId)
        repository.upsertAgent(a.toDomainModel())

        val convId = "conv-1"
        fakeConversationDao.upsertConversation(ConversationEntity(id = convId, agentId = agentId, title = "Agent Chat"))
        fakeMessageDao.insertMessage(MessageEntity(id = "msg-1", conversationId = convId, role = "user", content = "Hello"))
        fakeMessageDao.insertMessage(MessageEntity(id = "msg-2", conversationId = convId, role = "assistant", content = "Hi"))

        fakeMemoryDao.upsertMemory(MemoryEntity(
            id = "mem-1",
            layer = "episodic",
            agentId = agentId,
            conversationId = convId,
            title = "Agent preference",
            content = "Likes brief replies",
            createdAt = 1000L,
            lastAccessedAt = 1000L,
            relevanceScore = 0.9f,
            tags = "[]"
        ))

        repository.deleteAgent(agentId)

        assertNull(repository.getAgentById(agentId))
        assertNull(fakeConversationDao.getConversationByAgentId(agentId))
        assertNull(fakeConversationDao.getConversationById(convId))
        assertTrue(fakeMessageDao.messages.isEmpty())
        assertTrue(fakeMemoryDao.memories.isEmpty())
    }

    // ── updateLastUsed ───────────────────────────────────────────

    @Test
    fun `updateLastUsed updates timestamp for existing agent`() = runTest {
        val a = agent(id = "ts-1")
        repository.upsertAgent(a.toDomainModel())

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
