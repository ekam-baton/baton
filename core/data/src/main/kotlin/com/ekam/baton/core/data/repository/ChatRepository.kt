package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.ConversationDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.entity.ConversationEntity
import com.ekam.baton.core.data.db.entity.MessageEntity
import com.ekam.baton.core.data.db.entity.AgentEntity
import kotlinx.coroutines.flow.Flow
import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.network.repository.McpNetworkDataSource
import com.ekam.baton.core.network.dto.McpRequestDto
import com.ekam.baton.core.network.dto.McpMessageDto
import com.ekam.baton.core.network.repository.McpNetworkResult
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val agentDao: AgentDao,
    private val memoryDao: MemoryDao,
    private val mcpNetworkDataSource: McpNetworkDataSource
) {
    fun getAllAgents(): Flow<List<AgentEntity>> {
        return agentDao.getAllAgents()
    }

    fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllConversations()
    }

    suspend fun getConversationById(id: String): ConversationEntity? {
        return conversationDao.getConversationById(id)
    }

    suspend fun upsertConversation(conversation: ConversationEntity) {
        conversationDao.upsertConversation(conversation)
    }

    suspend fun deleteConversation(id: String) {
        conversationDao.deleteConversation(id)
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForConversation(conversationId)
    }

    suspend fun insertMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
    }

    suspend fun updateMessage(message: MessageEntity) {
        messageDao.updateMessage(message)
    }

    suspend fun getLastNMessages(conversationId: String, n: Int): List<MessageEntity> {
        return messageDao.getLastNMessages(conversationId, n)
    }

    suspend fun generateAgentResponse(conversationId: String, agentId: String, temporaryMessageId: String) {
        val agent = agentDao.getAgentById(agentId) ?: return
        val tempMsg = messageDao.getMessageById(temporaryMessageId) ?: return

        // 1. Fetch Context (Working Memory)
        val workingMemories = memoryDao.getMemoriesByLayer("working").firstOrNull() ?: emptyList()
        val systemPrompt = if (workingMemories.isNotEmpty()) {
            "Active Session Context:\n" + workingMemories.joinToString("\n") { it.content }
        } else {
            null
        }

        // 2. Fetch History (Up to last 20 messages for context window, excluding the temporary one)
        val historyEntities = messageDao.getLastNMessages(conversationId, 20)
            .filter { it.id != temporaryMessageId }
            .sortedBy { it.timestamp }
            
        val dtoMessages = historyEntities.map { 
            McpMessageDto(role = it.role, content = it.content) 
        }

        val requestDto = McpRequestDto(
            systemPrompt = systemPrompt,
            messages = dtoMessages
        )

        // 3. Network Call
        val result = mcpNetworkDataSource.sendMessage(
            url = agent.mcpEndpointUrl,
            authorization = null, // Auth configuration handling can be added later based on agent.authType
            request = requestDto
        )

        // 4. Persistence
        val updatedMessage = when (result) {
            is McpNetworkResult.Success -> {
                tempMsg.copy(
                    content = result.data.message.content,
                    isStreaming = false,
                    timestamp = System.currentTimeMillis()
                )
            }
            is McpNetworkResult.Error -> {
                tempMsg.copy(
                    content = "Error communicating with agent: ${result.message}",
                    isStreaming = false,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
        
        messageDao.updateMessage(updatedMessage)
    }
}
