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
 * In-memory fake of [MemoryDao] used across WorkingMemoryManager tests.
 */
private class WorkingMemoryFakeDao : MemoryDao {

    val memories = mutableListOf<MemoryEntity>()
    private val flow = MutableStateFlow<List<MemoryEntity>>(emptyList())

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

    override suspend fun getRecentEpisodicMemories(agentId: String, cutoffTime: Long): List<MemoryEntity> =
        memories.filter { it.layer == "episodic" && it.agentId == agentId && it.createdAt >= cutoffTime && it.isActive }

    override suspend fun getWorkingMemories(conversationId: String): List<MemoryEntity> =
        memories.filter { it.layer == "working" && it.conversationId == conversationId && it.isActive }

    override suspend fun updateLastAccessedTime(ids: List<String>, time: Long) {
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
}

class WorkingMemoryManagerTest {

    private lateinit var fakeDao: WorkingMemoryFakeDao
    private lateinit var manager: WorkingMemoryManager

    @Before
    fun setUp() {
        fakeDao = WorkingMemoryFakeDao()
        manager = WorkingMemoryManager(fakeDao)
    }

    // ── extractKeyFacts: individual regex patterns ───────────────

    @Test
    fun `extractKeyFacts with 'my name is John' creates a fact`() = runTest {
        manager.extractKeyFacts("conv-1", "my name is John")

        val stored = fakeDao.memories
        assertEquals(1, stored.size)
        assertEquals("Extracted Fact", stored[0].title)
        assertEquals("John", stored[0].content)
        assertEquals("working", stored[0].layer)
        assertEquals("conv-1", stored[0].conversationId)
    }

    @Test
    fun `extractKeyFacts with 'i work at Google' creates a fact`() = runTest {
        manager.extractKeyFacts("conv-1", "i work at Google")

        assertEquals(1, fakeDao.memories.size)
        assertEquals("Google", fakeDao.memories[0].content)
    }

    @Test
    fun `extractKeyFacts with 'i prefer dark mode' creates a fact`() = runTest {
        manager.extractKeyFacts("conv-1", "i prefer dark mode")

        assertEquals(1, fakeDao.memories.size)
        assertEquals("Dark mode", fakeDao.memories[0].content)
    }

    @Test
    fun `extractKeyFacts with 'remember that I like coffee' creates a fact`() = runTest {
        manager.extractKeyFacts("conv-1", "remember that I like coffee")

        assertEquals(1, fakeDao.memories.size)
        assertEquals("I like coffee", fakeDao.memories[0].content)
    }

    @Test
    fun `extractKeyFacts with no matching patterns creates no facts`() = runTest {
        manager.extractKeyFacts("conv-1", "Hello, how are you?")

        assertTrue(fakeDao.memories.isEmpty())
    }

    @Test
    fun `extractKeyFacts with random text creates no facts`() = runTest {
        manager.extractKeyFacts("conv-1", "The weather is nice today")

        assertTrue(fakeDao.memories.isEmpty())
    }

    // ── extractKeyFacts: multiple patterns in one message ────────

    @Test
    fun `extractKeyFacts with multiple patterns in one message`() = runTest {
        manager.extractKeyFacts("conv-1", "my name is Alice and i work at Meta")

        // "my name is alice" → captured up to "and" boundary → "alice"
        // "i work at meta" → "meta"
        assertEquals(2, fakeDao.memories.size)

        val contents = fakeDao.memories.map { it.content }.toSet()
        assertTrue(contents.contains("Alice"))
        assertTrue(contents.contains("Meta"))
    }

    @Test
    fun `extractKeyFacts with three patterns`() = runTest {
        manager.extractKeyFacts(
            "conv-1",
            "my name is Bob. i prefer light mode. remember that I need a reminder at 5pm"
        )

        assertEquals(3, fakeDao.memories.size)

        val contents = fakeDao.memories.map { it.content }.toSet()
        assertTrue(contents.contains("Bob"))
        assertTrue(contents.contains("Light mode"))
        assertTrue(contents.contains("I need a reminder at 5pm"))
    }

    // ── extractKeyFacts: edge-cases ──────────────────────────────

    @Test
    fun `extractKeyFacts is case insensitive`() = runTest {
        manager.extractKeyFacts("conv-1", "MY NAME IS Sarah")

        assertEquals(1, fakeDao.memories.size)
        // The regex runs on lowercased text; replaceFirstChar uppercases first char
        assertEquals("Sarah", fakeDao.memories[0].content)
    }

    @Test
    fun `extractKeyFacts trims whitespace from captured group`() = runTest {
        manager.extractKeyFacts("conv-1", "my name is   Dave  ")

        assertEquals(1, fakeDao.memories.size)
        assertEquals("Dave", fakeDao.memories[0].content)
    }

    // ── addWorkingMemory ─────────────────────────────────────────

    @Test
    fun `addWorkingMemory creates a memory with layer working`() = runTest {
        manager.addWorkingMemory("conv-1", "some content", "Title")

        val stored = fakeDao.memories
        assertEquals(1, stored.size)
        assertEquals("working", stored[0].layer)
        assertEquals("conv-1", stored[0].conversationId)
        assertEquals("some content", stored[0].content)
        assertEquals("Title", stored[0].title)
        assertTrue(stored[0].isActive)
        assertFalse(stored[0].id.isBlank())
    }

    @Test
    fun `addWorkingMemory creates distinct ids for each call`() = runTest {
        manager.addWorkingMemory("conv-1", "a", "T1")
        manager.addWorkingMemory("conv-1", "b", "T2")

        val ids = fakeDao.memories.map { it.id }.toSet()
        assertEquals(2, ids.size) // unique
    }

    // ── clearWorkingMemory ───────────────────────────────────────

    @Test
    fun `clearWorkingMemory deactivates all working memories for conversation`() = runTest {
        manager.addWorkingMemory("conv-1", "A", "T1")
        manager.addWorkingMemory("conv-1", "B", "T2")
        manager.addWorkingMemory("conv-2", "C", "T3") // different conversation

        manager.clearWorkingMemory("conv-1")

        val conv1 = fakeDao.memories.filter { it.conversationId == "conv-1" }
        assertTrue(conv1.all { !it.isActive })

        // conv-2 should be untouched
        val conv2 = fakeDao.memories.filter { it.conversationId == "conv-2" }
        assertTrue(conv2.all { it.isActive })
    }

    @Test
    fun `clearWorkingMemory with no memories is no-op`() = runTest {
        manager.clearWorkingMemory("nonexistent")
        // Should not throw; memories list stays empty
        assertTrue(fakeDao.memories.isEmpty())
    }
}
