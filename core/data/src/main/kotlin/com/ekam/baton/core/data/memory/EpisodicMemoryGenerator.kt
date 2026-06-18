package com.ekam.baton.core.data.memory

import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.entity.MemoryEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodicMemoryGenerator @Inject constructor(
    private val memoryDao: MemoryDao,
    private val messageDao: MessageDao
) {
    suspend fun generateEpisodicSummary(conversationId: String, agentId: String) {
        val lastMessages = messageDao.getLastNMessages(conversationId, 10)
        
        if (lastMessages.isEmpty()) return

        // TODO: For now (MVP), implement the summary generation as a stub.
        // We will add real summarization via MCP in a future update.
        val firstMessagePreview = lastMessages.lastOrNull { it.role == "user" }?.content?.take(30) ?: "interaction"
        
        val summary = "Conversation on ${java.util.Date()} about: $firstMessagePreview..."

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
