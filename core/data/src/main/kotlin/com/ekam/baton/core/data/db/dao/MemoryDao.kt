package com.ekam.baton.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekam.baton.core.data.db.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE layer = :layer ORDER BY createdAt DESC")
    fun getMemoriesByLayer(layer: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE agentId = :agentId OR agentId IS NULL ORDER BY relevanceScore DESC")
    fun getMemoriesForAgent(agentId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY relevanceScore DESC")
    fun searchMemories(query: String): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("UPDATE memories SET isActive = :isActive WHERE id = :id")
    suspend fun toggleMemoryActive(id: String, isActive: Boolean)
    @Query("SELECT * FROM memories WHERE layer = 'semantic' AND (agentId = :agentId OR agentId IS NULL) AND isActive = 1 ORDER BY relevanceScore DESC LIMIT 5")
    suspend fun getTopSemanticMemories(agentId: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE layer = 'episodic' AND agentId = :agentId AND isActive = 1 AND lastAccessedAt >= :cutoffTime ORDER BY lastAccessedAt DESC LIMIT 3")
    suspend fun getRecentEpisodicMemories(agentId: String, cutoffTime: Long): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE layer = 'working' AND conversationId = :conversationId AND isActive = 1")
    suspend fun getWorkingMemories(conversationId: String): List<MemoryEntity>

    @Query("UPDATE memories SET lastAccessedAt = :time WHERE id IN (:ids)")
    suspend fun updateLastAccessedTime(ids: List<String>, time: Long)

    @Query("UPDATE memories SET isActive = 0 WHERE layer = 'working' AND conversationId = :conversationId")
    suspend fun clearWorkingMemoriesForConversation(conversationId: String)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}
