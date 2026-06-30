package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.ConversationDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.entity.ConversationEntity
import com.ekam.baton.core.data.db.entity.MessageEntity
import com.ekam.baton.core.data.db.entity.AgentEntity
import kotlinx.coroutines.flow.Flow
import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.util.AuditCryptoUtils
import com.ekam.baton.core.data.memory.MemoryInjectionEngine
import com.ekam.baton.core.data.memory.WorkingMemoryManager
import com.ekam.baton.core.network.mcp.McpMessageSender
import com.ekam.baton.core.network.mcp.AttachmentDto
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import java.util.UUID
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.Conversation
import com.ekam.baton.core.data.model.Message
import com.ekam.baton.core.data.model.toDomainModel
import com.ekam.baton.core.data.model.toEntity
import androidx.paging.map
import kotlinx.coroutines.flow.map

class ChatRepository constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val agentDao: AgentDao,
    private val memoryDao: MemoryDao,
    private val auditDao: AuditDao,
    private val mcpMessageSender: McpMessageSender,
    private val memoryInjectionEngine: MemoryInjectionEngine,
    private val workingMemoryManager: WorkingMemoryManager
) {
    fun getAllAgents(): Flow<List<Agent>> {
        return agentDao.getAllAgents().map { list -> list.map { it.toDomainModel() } }
    }

    suspend fun getAgentById(id: String): Agent? {
        return agentDao.getAgentById(id)?.toDomainModel()
    }

    suspend fun upsertAgent(agent: Agent) {
        agentDao.upsertAgent(agent.toEntity())
    }

    fun getAllConversations(query: String = ""): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Conversation>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            conversationDao.getAllConversations(query)
        }.flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }
    }

    suspend fun getConversationById(id: String): Conversation? {
        return conversationDao.getConversationById(id)?.toDomainModel()
    }

    suspend fun upsertConversation(conversation: Conversation) {
        conversationDao.upsertConversation(conversation.toEntity())
    }

    suspend fun deleteConversation(id: String) {
        conversationDao.deleteConversation(id)
    }

    fun getMessagesForConversation(conversationId: String): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Message>> {
        return androidx.paging.Pager(
            config = androidx.paging.PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            messageDao.getMessagesForConversation(conversationId)
        }.flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }
    }

    suspend fun insertMessage(message: MessageEntity) {
        val lastAudit = auditDao.getLastAuditLog()
        val prevHash = lastAudit?.hash ?: ""
        // FIX: Use JSONObject to safely escape user content and prevent JSON injection
        val payload = org.json.JSONObject().apply {
            put("id", message.id)
            put("conversationId", message.conversationId)
            put("role", message.role)
            put("content", message.content)
        }.toString()
        val newHash = AuditCryptoUtils.generateHash(payload, prevHash)

        val hashedMessage = message.copy(previousHash = prevHash, hash = newHash)
        messageDao.insertMessage(hashedMessage)

        auditDao.insertAuditLog(AuditLogEntity(
            entityName = "MessageEntity",
            entityId = hashedMessage.id,
            action = "INSERT",
            deviceId = "local_device",
            payloadJson = payload,
            previousHash = prevHash,
            hash = newHash
        ))
    }

    suspend fun updateMessage(message: MessageEntity) {
        val lastAudit = auditDao.getLastAuditLog()
        val prevHash = lastAudit?.hash ?: ""
        // FIX: Use JSONObject to safely escape user content and prevent JSON injection
        val payload = org.json.JSONObject().apply {
            put("id", message.id)
            put("conversationId", message.conversationId)
            put("role", message.role)
            put("content", message.content)
        }.toString()
        val newHash = AuditCryptoUtils.generateHash(payload, prevHash)

        val hashedMessage = message.copy(previousHash = prevHash, hash = newHash)
        messageDao.updateMessage(hashedMessage)

        auditDao.insertAuditLog(AuditLogEntity(
            entityName = "MessageEntity",
            entityId = hashedMessage.id,
            action = "UPDATE",
            deviceId = "local_device",
            payloadJson = payload,
            previousHash = prevHash,
            hash = newHash
        ))
    }

    suspend fun getLastNMessages(conversationId: String, n: Int): List<MessageEntity> {
        return messageDao.getLastNMessages(conversationId, n)
    }

    suspend fun sendMessageWithResponse(
        conversationId: String,
        content: String,
        attachments: List<AttachmentDto> = emptyList()
    ): kotlinx.coroutines.flow.Flow<String> {
        val attachmentsJson = if (attachments.isNotEmpty()) {
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(AttachmentDto.serializer()), 
                attachments
            )
        } else null

        val userMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = "user",
            content = content,
            attachments = attachmentsJson
        )
        insertMessage(userMsg)

        // 2. Extract working memory facts
        workingMemoryManager.extractKeyFacts(conversationId, content)

        // 3. Update conversation metadata
        val conv = conversationDao.getConversationById(conversationId)
        if (conv != null) {
            conversationDao.upsertConversation(
                conv.copy(
                    updatedAt = System.currentTimeMillis(),
                    messageCount = conv.messageCount + 1,
                    title = if (conv.messageCount == 0) content.take(30) + "..." else conv.title
                )
            )
        }

        // 4. Create placeholder for assistant response
        val tempMessageId = UUID.randomUUID().toString()
        val assistantMsg = MessageEntity(
            id = tempMessageId,
            conversationId = conversationId,
            role = "assistant",
            content = "",
            isStreaming = true
        )
        insertMessage(assistantMsg)

        val agentId = conv?.agentId ?: throw IllegalStateException("Conversation has no agent")
        val agent = agentDao.getAgentById(agentId) ?: throw IllegalStateException("Agent not found")

        // 5. Build Context
        val enrichedContextMessage = memoryInjectionEngine.buildContextBlock(
            agentId = agentId,
            conversationId = conversationId,
            userMessage = content
        )

        // 6. Map History
        val history = messageDao.getLastNMessages(conversationId, 20)
            .filter { it.id != tempMessageId && it.id != userMsg.id }
            .sortedBy { it.timestamp }
            .map { com.ekam.baton.core.network.dto.McpMessageDto(role = it.role, content = it.content) }

        // 7. Call Network and return Flow
        return mcpMessageSender.sendUserMessage(
            agentId = agentId,
            endpointUrl = agent.mcpEndpointUrl,
            authHeader = null,
            conversationHistory = history,
            newUserMessage = enrichedContextMessage,
            attachments = attachments
        ).catch { e ->
            val finalMsg = messageDao.getMessageById(tempMessageId)
            if (finalMsg != null) {
                updateMessage(
                    finalMsg.copy(
                        content = finalMsg.content + "\n\nError: ${e.message}",
                        isStreaming = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            throw e
        }.onCompletion {
            val finalMsg = messageDao.getMessageById(tempMessageId)
            if (finalMsg != null) {
                updateMessage(finalMsg.copy(isStreaming = false))
            }
            
            // Trigger metadata update again for the response
            val finalConv = conversationDao.getConversationById(conversationId)
            if (finalConv != null) {
                conversationDao.upsertConversation(
                    finalConv.copy(
                        updatedAt = System.currentTimeMillis(),
                        messageCount = finalConv.messageCount + 1
                    )
                )
            }
        }.onEach { chunk ->
            val currentMsg = messageDao.getMessageById(tempMessageId)
            if (currentMsg != null) {
                updateMessage(
                    currentMsg.copy(
                        content = currentMsg.content + chunk,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun getMessageById(id: String): MessageEntity? = messageDao.getMessageById(id)

    suspend fun getAvailableTools(agentId: String): List<com.ekam.baton.core.network.mcp.McpTool> {
        val agent = agentDao.getAgentById(agentId) ?: return emptyList()
        return mcpMessageSender.getAvailableTools(agentId, agent.mcpEndpointUrl, null)
    }

    suspend fun executeToolManual(
        conversationId: String,
        toolName: String,
        arguments: kotlinx.serialization.json.JsonObject
    ): kotlinx.coroutines.flow.Flow<String> {
        val conv = conversationDao.getConversationById(conversationId) ?: throw IllegalStateException("Conversation not found")
        val agent = agentDao.getAgentById(conv.agentId) ?: throw IllegalStateException("Agent not found")
        
        val tempMessageId = UUID.randomUUID().toString()
        val toolMsg = MessageEntity(
            id = tempMessageId,
            conversationId = conversationId,
            role = "assistant",
            content = "Executing $toolName...",
            isStreaming = true
        )
        insertMessage(toolMsg)

        return mcpMessageSender.executeTool(
            agentId = agent.id,
            endpointUrl = agent.mcpEndpointUrl,
            authHeader = null,
            toolName = toolName,
            arguments = arguments
        ).catch { e ->
            val finalMsg = messageDao.getMessageById(tempMessageId)
            if (finalMsg != null) {
                updateMessage(finalMsg.copy(content = finalMsg.content + "\n\nError: ${e.message}", isStreaming = false))
            }
            throw e
        }.onCompletion {
            val finalMsg = messageDao.getMessageById(tempMessageId)
            if (finalMsg != null) {
                updateMessage(finalMsg.copy(isStreaming = false))
            }
        }.onEach { chunk ->
            val currentMsg = messageDao.getMessageById(tempMessageId)
            if (currentMsg != null) {
                val newContent = if (currentMsg.content.startsWith("Executing")) chunk else currentMsg.content + chunk
                updateMessage(currentMsg.copy(content = newContent, timestamp = System.currentTimeMillis()))
            }
        }
    }
}
