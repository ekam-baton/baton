package com.ekam.baton.core.data.memory

import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * In-memory fake of [MemoryDao] used across MemoryInjectionEngine tests.
 */
private class InjectionFakeDao : MemoryDao {

    val memories = mutableListOf<MemoryEntity>()
    private val flow = MutableStateFlow<List<MemoryEntity>>(emptyList())

    /** Tracks calls to [updateLastAccessedTime] for assertion. */
    val lastAccessedUpdates = mutableListOf<Pair<List<String>, Long>>()

    private fun emit() { flow.value = memories.toList() }

    override fun getAllMemories(): Flow<List<MemoryEntity>> = flow
    override fun getMemoriesByLayer(layer: String): Flow<List<MemoryEntity>> =
        flow.map { it.filter { m -> m.layer == layer } }
    override fun getMemoriesForAgent(agentId: String): Flow<List<MemoryEntity>> =
        flow.map { it.filter { m -> m.agentId == agentId } }
    override fun searchMemories(query: String): Flow<List<MemoryEntity>> =
        flow.map { list ->
            val q = query.lowercase()
            list.filter { it.title.lowercase().contains(q) || it.content.lowercase().contains(q) || it.tags.lowercase().contains(q) }
        }

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
        val i = memories.indexOfFirst { it.id == id }
        if (i != -1) { memories[i] = memories[i].copy(isActive = isActive); emit() }
    }

    override suspend fun getTopSemanticMemories(agentId: String): List<MemoryEntity> =
        memories.filter { it.layer == "semantic" && it.agentId == agentId && it.isActive }
            .sortedByDescending { it.relevanceScore }

    override suspend fun getRecentEpisodicMemories(agentId: String, cutoffTime: Long): List<MemoryEntity> =
        memories.filter { it.layer == "episodic" && it.agentId == agentId && it.createdAt >= cutoffTime && it.isActive }

    override suspend fun getWorkingMemories(conversationId: String): List<MemoryEntity> =
        memories.filter { it.layer == "working" && it.conversationId == conversationId && it.isActive }

    override suspend fun updateLastAccessedTime(ids: List<String>, time: Long) {
        lastAccessedUpdates.add(ids to time)
        ids.forEach { id ->
            val i = memories.indexOfFirst { it.id == id }
            if (i != -1) { memories[i] = memories[i].copy(lastAccessedAt = time) }
        }
        emit()
    }

    override suspend fun clearWorkingMemoriesForConversation(conversationId: String) {
        memories.forEachIndexed { i, m ->
            if (m.layer == "working" && m.conversationId == conversationId) {
                memories[i] = m.copy(isActive = false)
            }
        }
        emit()
    }

    override suspend fun clearAllMemories() {
        memories.clear()
        emit()
    }
}

class MemoryInjectionEngineTest {

    private lateinit var fakeDao: InjectionFakeDao
    private lateinit var engine: MemoryInjectionEngine

    private fun memory(
        id: String,
        layer: String,
        agentId: String? = "agent-1",
        conversationId: String? = null,
        title: String = "Title",
        content: String = "Content",
        createdAt: Long = System.currentTimeMillis(),
        lastAccessedAt: Long = 0L,
        relevanceScore: Float = 0.9f,
        tags: String = "[]",
        isActive: Boolean = true
    ) = MemoryEntity(
        id = id, layer = layer, agentId = agentId,
        conversationId = conversationId, title = title,
        content = content, createdAt = createdAt,
        lastAccessedAt = lastAccessedAt,
        relevanceScore = relevanceScore, tags = tags,
        isActive = isActive
    )

    @Before
    fun setUp() {
        fakeDao = InjectionFakeDao()
        engine = MemoryInjectionEngine(fakeDao)
    }

    // ── no memories → raw user message ───────────────────────────

    @Test
    fun `buildContextBlock with no memories returns just user message`() = runTest {
        val result = engine.buildContextBlock("agent-1", "conv-1", "Hello!")
        assertEquals("Hello!", result)
    }

    // ── semantic memories only ───────────────────────────────────

    @Test
    fun `buildContextBlock with semantic memories includes SEMANTIC MEMORY block`() = runTest {
        fakeDao.upsertMemory(
            memory(id = "s1", layer = "semantic", agentId = "agent-1", title = "Fact", content = "User likes cats")
        )

        val result = engine.buildContextBlock("agent-1", "conv-1", "Hi")

        assertTrue(result.contains("[SEMANTIC MEMORY]"))
        assertTrue(result.contains("• Fact: User likes cats"))
        assertFalse(result.contains("[PAST INTERACTIONS]"))
        assertFalse(result.contains("[CURRENT SESSION]"))
        assertTrue(result.contains("User message: Hi"))
    }

    // ── episodic memories only ───────────────────────────────────

    @Test
    fun `buildContextBlock with episodic memories includes PAST INTERACTIONS block`() = runTest {
        fakeDao.upsertMemory(
            memory(
                id = "e1", layer = "episodic", agentId = "agent-1",
                title = "Past Chat", content = "Discussed travel plans",
                createdAt = System.currentTimeMillis() // well within 30-day cutoff
            )
        )

        val result = engine.buildContextBlock("agent-1", "conv-1", "Hey")

        assertTrue(result.contains("[PAST INTERACTIONS]"))
        assertTrue(result.contains("• Past Chat: Discussed travel plans"))
        assertFalse(result.contains("[SEMANTIC MEMORY]"))
        assertFalse(result.contains("[CURRENT SESSION]"))
        assertTrue(result.contains("User message: Hey"))
    }

    // ── working memories only ────────────────────────────────────

    @Test
    fun `buildContextBlock with working memories includes CURRENT SESSION block`() = runTest {
        fakeDao.upsertMemory(
            memory(
                id = "w1", layer = "working", agentId = null,
                conversationId = "conv-1", content = "User asked about pricing"
            )
        )

        val result = engine.buildContextBlock("agent-1", "conv-1", "Tell me more")

        assertTrue(result.contains("[CURRENT SESSION]"))
        assertTrue(result.contains("• User asked about pricing"))
        assertFalse(result.contains("[SEMANTIC MEMORY]"))
        assertFalse(result.contains("[PAST INTERACTIONS]"))
        assertTrue(result.contains("User message: Tell me more"))
    }

    // ── all three types ──────────────────────────────────────────

    @Test
    fun `buildContextBlock with all three types includes all blocks`() = runTest {
        fakeDao.upsertMemory(
            memory(id = "s1", layer = "semantic", agentId = "agent-1", title = "Pref", content = "Likes dark mode")
        )
        fakeDao.upsertMemory(
            memory(
                id = "e1", layer = "episodic", agentId = "agent-1",
                title = "Session", content = "Talked about Kotlin",
                createdAt = System.currentTimeMillis()
            )
        )
        fakeDao.upsertMemory(
            memory(
                id = "w1", layer = "working", agentId = null,
                conversationId = "conv-1", content = "Current topic: testing"
            )
        )

        val result = engine.buildContextBlock("agent-1", "conv-1", "Continue")

        assertTrue(result.contains("[SEMANTIC MEMORY]"))
        assertTrue(result.contains("• Pref: Likes dark mode"))
        assertTrue(result.contains("[PAST INTERACTIONS]"))
        assertTrue(result.contains("• Session: Talked about Kotlin"))
        assertTrue(result.contains("[CURRENT SESSION]"))
        assertTrue(result.contains("• Current topic: testing"))
        assertTrue(result.contains("User message: Continue"))
    }

    @Test
    fun `buildContextBlock starts with context preamble when memories exist`() = runTest {
        fakeDao.upsertMemory(
            memory(id = "s1", layer = "semantic", agentId = "agent-1")
        )

        val result = engine.buildContextBlock("agent-1", "conv-1", "Hi")
        assertTrue(result.startsWith("You have the following context about this user:"))
    }

    // ── lastAccessedAt update ────────────────────────────────────

    @Test
    fun `updates lastAccessedAt for fetched memories`() = runTest {
        fakeDao.upsertMemory(
            memory(id = "s1", layer = "semantic", agentId = "agent-1", lastAccessedAt = 0L)
        )
        fakeDao.upsertMemory(
            memory(
                id = "e1", layer = "episodic", agentId = "agent-1",
                lastAccessedAt = 0L, createdAt = System.currentTimeMillis()
            )
        )

        engine.buildContextBlock("agent-1", "conv-1", "Hi")

        // The fake records every call to updateLastAccessedTime
        assertEquals(1, fakeDao.lastAccessedUpdates.size)
        val (ids, time) = fakeDao.lastAccessedUpdates[0]
        assertTrue(ids.contains("s1"))
        assertTrue(ids.contains("e1"))
        assertTrue(time > 0)
    }

    @Test
    fun `does not call updateLastAccessedTime when no memories found`() = runTest {
        engine.buildContextBlock("agent-1", "conv-1", "Hi")

        assertTrue(fakeDao.lastAccessedUpdates.isEmpty())
    }

    // ── memory isolation between agents / conversations ──────────

    @Test
    fun `buildContextBlock ignores semantic memories for other agents`() = runTest {
        fakeDao.upsertMemory(
            memory(id = "s-other", layer = "semantic", agentId = "other-agent", title = "X", content = "Y")
        )

        val result = engine.buildContextBlock("agent-1", "conv-1", "Hi")
        assertEquals("Hi", result) // no memories for agent-1
    }

    @Test
    fun `buildContextBlock ignores working memories for other conversations`() = runTest {
        fakeDao.upsertMemory(
            memory(id = "w-other", layer = "working", agentId = null, conversationId = "conv-other", content = "Z")
        )

        val result = engine.buildContextBlock("agent-1", "conv-1", "Hi")
        assertEquals("Hi", result)
    }

    @Test
    fun `buildContextBlock ignores inactive memories`() = runTest {
        fakeDao.upsertMemory(
            memory(id = "s-inactive", layer = "semantic", agentId = "agent-1", isActive = false)
        )

        val result = engine.buildContextBlock("agent-1", "conv-1", "Hi")
        assertEquals("Hi", result)
    }
}
