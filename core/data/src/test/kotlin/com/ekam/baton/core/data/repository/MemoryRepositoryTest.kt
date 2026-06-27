package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.entity.MemoryEntity
import com.ekam.baton.core.data.model.toDomainModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeMemoryDao : MemoryDao {

    val memories = mutableListOf<MemoryEntity>()
    private val flow = MutableStateFlow<List<MemoryEntity>>(emptyList())

    /** IDs whose lastAccessedAt was updated, along with the time written. */
    var lastAccessedUpdates = mutableListOf<Pair<List<String>, Long>>()

    private fun emit() {
        flow.value = memories.toList()
    }

    override fun getAllMemories(): Flow<List<MemoryEntity>> = flow

    override fun getMemoriesByLayer(layer: String): Flow<List<MemoryEntity>> =
        flow.map { list -> list.filter { it.layer == layer } }

    override fun getMemoriesForAgent(agentId: String): Flow<List<MemoryEntity>> =
        flow.map { list -> list.filter { it.agentId == agentId } }

    override fun searchMemories(query: String): Flow<List<MemoryEntity>> =
        flow.map { list ->
            val q = query.lowercase()
            list.filter {
                it.title.lowercase().contains(q) ||
                    it.content.lowercase().contains(q) ||
                    it.tags.lowercase().contains(q)
            }
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
        val index = memories.indexOfFirst { it.id == id }
        if (index != -1) {
            memories[index] = memories[index].copy(isActive = isActive)
            emit()
        }
    }

    override suspend fun getTopSemanticMemories(agentId: String): List<MemoryEntity> =
        memories.filter { it.layer == "semantic" && it.agentId == agentId && it.isActive }
            .sortedByDescending { it.relevanceScore }

    override suspend fun getRecentEpisodicMemories(agentId: String, cutoffTime: Long): List<MemoryEntity> =
        memories.filter {
            it.layer == "episodic" && it.agentId == agentId && it.createdAt >= cutoffTime && it.isActive
        }

    override suspend fun getWorkingMemories(conversationId: String): List<MemoryEntity> =
        memories.filter { it.layer == "working" && it.conversationId == conversationId && it.isActive }

    override suspend fun updateLastAccessedTime(ids: List<String>, time: Long) {
        lastAccessedUpdates.add(ids to time)
        ids.forEach { id ->
            val index = memories.indexOfFirst { it.id == id }
            if (index != -1) {
                memories[index] = memories[index].copy(lastAccessedAt = time)
            }
        }
        emit()
    }

    override suspend fun clearWorkingMemoriesForConversation(conversationId: String) {
        memories.filter { it.layer == "working" && it.conversationId == conversationId }
            .forEach { mem ->
                val index = memories.indexOf(mem)
                if (index != -1) {
                    memories[index] = memories[index].copy(isActive = false)
                }
            }
        emit()
    }

    override suspend fun clearAllMemories() {
        memories.clear()
        emit()
    }
}

class MemoryRepositoryTest {

    private lateinit var fakeDao: FakeMemoryDao
    private lateinit var repository: MemoryRepository

    private fun memory(
        id: String = "m-1",
        layer: String = "semantic",
        agentId: String? = "agent-1",
        conversationId: String? = null,
        title: String = "Title",
        content: String = "Content",
        createdAt: Long = 1_000L,
        lastAccessedAt: Long = 1_000L,
        relevanceScore: Float = 0.5f,
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
        fakeDao = FakeMemoryDao()
        repository = MemoryRepository(fakeDao)
    }

    // ── getAllMemories ────────────────────────────────────────────

    @Test
    fun `getAllMemories returns empty list initially`() = runTest {
        assertTrue(repository.getAllMemories().first().isEmpty())
    }

    @Test
    fun `getAllMemories returns all inserted memories`() = runTest {
        fakeDao.upsertMemory(memory(id = "1"))
        fakeDao.upsertMemory(memory(id = "2"))

        val result = repository.getAllMemories().first()
        assertEquals(2, result.size)
    }

    // ── getMemoriesByLayer ───────────────────────────────────────

    @Test
    fun `getMemoriesByLayer filters by layer`() = runTest {
        fakeDao.upsertMemory(memory(id = "1", layer = "semantic"))
        fakeDao.upsertMemory(memory(id = "2", layer = "episodic"))
        fakeDao.upsertMemory(memory(id = "3", layer = "semantic"))

        val result = repository.getMemoriesByLayer("semantic").first()
        assertEquals(2, result.size)
        assertTrue(result.all { it.layer == "semantic" })
    }

    @Test
    fun `getMemoriesByLayer returns empty for unknown layer`() = runTest {
        fakeDao.upsertMemory(memory(id = "1", layer = "semantic"))

        val result = repository.getMemoriesByLayer("procedural").first()
        assertTrue(result.isEmpty())
    }

    // ── getMemoriesForAgent ──────────────────────────────────────

    @Test
    fun `getMemoriesForAgent filters by agentId`() = runTest {
        fakeDao.upsertMemory(memory(id = "1", agentId = "a1"))
        fakeDao.upsertMemory(memory(id = "2", agentId = "a2"))
        fakeDao.upsertMemory(memory(id = "3", agentId = "a1"))

        val result = repository.getMemoriesForAgent("a1").first()
        assertEquals(2, result.size)
        assertTrue(result.all { it.agentId == "a1" })
    }

    @Test
    fun `getMemoriesForAgent returns empty when no match`() = runTest {
        fakeDao.upsertMemory(memory(id = "1", agentId = "a1"))

        val result = repository.getMemoriesForAgent("unknown").first()
        assertTrue(result.isEmpty())
    }

    // ── searchMemories ───────────────────────────────────────────

    @Test
    fun `searchMemories matches title`() = runTest {
        fakeDao.upsertMemory(memory(id = "1", title = "Kotlin basics", content = "foo"))
        fakeDao.upsertMemory(memory(id = "2", title = "Java basics", content = "bar"))

        val result = repository.searchMemories("kotlin").first()
        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun `searchMemories matches content`() = runTest {
        fakeDao.upsertMemory(memory(id = "1", title = "T", content = "User prefers dark mode"))
        fakeDao.upsertMemory(memory(id = "2", title = "T", content = "Something else"))

        val result = repository.searchMemories("dark mode").first()
        assertEquals(1, result.size)
        assertEquals("1", result[0].id)
    }

    @Test
    fun `searchMemories matches tags`() = runTest {
        fakeDao.upsertMemory(memory(id = "1", tags = "[\"android\",\"kotlin\"]"))
        fakeDao.upsertMemory(memory(id = "2", tags = "[\"swift\"]"))

        val result = repository.searchMemories("kotlin").first()
        assertEquals(1, result.size)
    }

    @Test
    fun `searchMemories is case insensitive`() = runTest {
        fakeDao.upsertMemory(memory(id = "1", title = "KOTLIN Tutorial"))

        val result = repository.searchMemories("kotlin").first()
        assertEquals(1, result.size)
    }

    @Test
    fun `searchMemories returns empty when no match`() = runTest {
        fakeDao.upsertMemory(memory(id = "1", title = "A", content = "B", tags = "[]"))

        val result = repository.searchMemories("zzz").first()
        assertTrue(result.isEmpty())
    }

    // ── upsertMemory ─────────────────────────────────────────────

    @Test
    fun `upsertMemory inserts new memory`() = runTest {
        val m = memory(id = "new-1")
        repository.upsertMemory(m.toDomainModel())

        val all = repository.getAllMemories().first()
        assertEquals(1, all.size)
        assertEquals("new-1", all[0].id)
    }

    @Test
    fun `upsertMemory updates existing memory`() = runTest {
        repository.upsertMemory(memory(id = "u-1", title = "Old").toDomainModel())
        repository.upsertMemory(memory(id = "u-1", title = "New").toDomainModel())

        val all = repository.getAllMemories().first()
        assertEquals(1, all.size)
        assertEquals("New", all[0].title)
    }

    // ── deleteMemory ─────────────────────────────────────────────

    @Test
    fun `deleteMemory removes memory`() = runTest {
        repository.upsertMemory(memory(id = "del-1").toDomainModel())
        repository.deleteMemory("del-1")

        assertTrue(repository.getAllMemories().first().isEmpty())
    }

    @Test
    fun `deleteMemory with unknown id is no-op`() = runTest {
        repository.upsertMemory(memory(id = "keep").toDomainModel())
        repository.deleteMemory("ghost")

        assertEquals(1, repository.getAllMemories().first().size)
    }

    // ── toggleMemoryActive ───────────────────────────────────────

    @Test
    fun `toggleMemoryActive sets isActive to false`() = runTest {
        repository.upsertMemory(memory(id = "t-1", isActive = true).toDomainModel())
        repository.toggleMemoryActive("t-1", isActive = false)

        val result = repository.getAllMemories().first().first { it.id == "t-1" }
        assertFalse(result.isActive)
    }

    @Test
    fun `toggleMemoryActive sets isActive to true`() = runTest {
        repository.upsertMemory(memory(id = "t-2", isActive = false).toDomainModel())
        repository.toggleMemoryActive("t-2", isActive = true)

        val result = repository.getAllMemories().first().first { it.id == "t-2" }
        assertTrue(result.isActive)
    }
}
