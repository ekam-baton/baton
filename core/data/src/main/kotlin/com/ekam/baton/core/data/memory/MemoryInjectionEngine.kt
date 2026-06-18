package com.ekam.baton.core.data.memory

import com.ekam.baton.core.data.db.dao.MemoryDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryInjectionEngine @Inject constructor(
    private val memoryDao: MemoryDao
) {
    suspend fun buildContextBlock(
        agentId: String,
        conversationId: String,
        userMessage: String
    ): String {
        // 1. Fetch Semantic Memories (Top 5)
        val semanticMemories = memoryDao.getTopSemanticMemories(agentId)

        // 2. Fetch Episodic Memories (Last 30 days, Top 3)
        // 30 days in ms = 30L * 24 * 60 * 60 * 1000
        val cutoffTime = System.currentTimeMillis() - 2592000000L
        val episodicMemories = memoryDao.getRecentEpisodicMemories(agentId, cutoffTime)

        // 3. Fetch Working Memories (Current session)
        val workingMemories = memoryDao.getWorkingMemories(conversationId)

        // Update lastAccessedAt for all fetched memories
        val allFetchedIds = (semanticMemories + episodicMemories + workingMemories).map { it.id }
        if (allFetchedIds.isNotEmpty()) {
            memoryDao.updateLastAccessedTime(allFetchedIds, System.currentTimeMillis())
        }

        // Assemble Block
        if (allFetchedIds.isEmpty()) {
            return userMessage
        }

        val contextBuilder = StringBuilder("You have the following context about this user:\n\n")

        if (semanticMemories.isNotEmpty()) {
            contextBuilder.append("[SEMANTIC MEMORY]\n")
            semanticMemories.forEach { mem ->
                contextBuilder.append("• ${mem.title}: ${mem.content}\n")
            }
            contextBuilder.append("\n")
        }

        if (episodicMemories.isNotEmpty()) {
            contextBuilder.append("[PAST INTERACTIONS]\n")
            episodicMemories.forEach { mem ->
                contextBuilder.append("• ${mem.title}: ${mem.content}\n")
            }
            contextBuilder.append("\n")
        }

        if (workingMemories.isNotEmpty()) {
            contextBuilder.append("[CURRENT SESSION]\n")
            workingMemories.forEach { mem ->
                contextBuilder.append("• ${mem.content}\n")
            }
            contextBuilder.append("\n")
        }

        contextBuilder.append("---\nUser message: $userMessage")
        return contextBuilder.toString()
    }
}
