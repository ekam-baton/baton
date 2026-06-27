package com.ekam.baton.core.data.memory

import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.entity.MemoryEntity
import java.util.UUID

class WorkingMemoryManager constructor(
    private val memoryDao: MemoryDao
) {
    suspend fun addWorkingMemory(conversationId: String, content: String, title: String) {
        val memory = MemoryEntity(
            id = UUID.randomUUID().toString(),
            layer = "working",
            agentId = null, // Working memory is specific to conversation, not agent
            conversationId = conversationId,
            title = title,
            content = content,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis(),
            relevanceScore = 1.0f,
            tags = "[]",
            isActive = true
        )
        memoryDao.upsertMemory(memory)
    }

    suspend fun clearWorkingMemory(conversationId: String) {
        memoryDao.clearWorkingMemoriesForConversation(conversationId)
    }

    suspend fun extractKeyFacts(conversationId: String, message: String) {
        // Simple heuristic: look for "my name is", "I work at", "I prefer", "remember that"
        val lowerMessage = message.lowercase()
        val facts = mutableListOf<String>()

        val regexes = listOf(
            Regex("my name is (.*?)(?:\\.|\\n|and|but|$)"),
            Regex("i work at (.*?)(?:\\.|\\n|and|but|$)"),
            Regex("i prefer (.*?)(?:\\.|\\n|and|but|$)"),
            Regex("remember that (.*?)(?:\\.|\\n|and|but|$)")
        )

        for (regex in regexes) {
            val match = regex.find(lowerMessage)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotBlank()) {
                    facts.add(extracted)
                }
            }
        }

        // Add extracted facts to working memory
        for (fact in facts) {
            addWorkingMemory(
                conversationId = conversationId,
                content = fact.replaceFirstChar { it.uppercase() },
                title = "Extracted Fact"
            )
        }
    }
}
