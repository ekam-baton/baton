package com.ekam.baton.core.data.memory

import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.entity.MemoryEntity
import java.util.UUID

class EpisodicMemoryGenerator constructor(
    private val memoryDao: MemoryDao,
    private val messageDao: MessageDao
) {
    suspend fun generateEpisodicSummary(conversationId: String, agentId: String) {
        val lastMessages = messageDao.getLastNMessages(conversationId, 10)
        
        if (lastMessages.isEmpty()) return

        // The summary generation is implemented as a basic heuristic stub for the MVP.
        // We will add real summarization via an MCP LLM tool in a future update.
        val userMessages = lastMessages.filter { it.role == "user" }
        val topics = userMessages.take(3).joinToString(", ") { it.content.take(20).replace("\n", " ") }
        
        val summary = "Recent topics discussed: $topics..."

        val memory = MemoryEntity(
            id = UUID.randomUUID().toString(),
            layer = "episodic",
            agentId = agentId,
            conversationId = conversationId,
            title = "Conversation Summary",
            content = summary,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            relevanceScore = 1.0f,
            tags = "[\"summary\"]",
            isActive = true
        )
        memoryDao.upsertMemory(memory)
    }
}
