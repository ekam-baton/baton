package com.ekam.baton.feature.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.db.entity.ConversationEntity
import com.ekam.baton.core.data.db.entity.MessageEntity
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.repository.ChatRepository
import com.ekam.baton.core.network.mcp.McpMessageSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import com.ekam.baton.core.data.memory.MemoryInjectionEngine
import com.ekam.baton.core.data.memory.WorkingMemoryManager
import com.ekam.baton.core.data.repository.MemoryRepository
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val mcpMessageSender: McpMessageSender,
    private val memoryInjectionEngine: MemoryInjectionEngine,
    private val workingMemoryManager: WorkingMemoryManager,
    private val memoryRepository: MemoryRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Note: This matches the navArgument key in BatonNavGraph.kt
    private val conversationId: String? = savedStateHandle["conversationId"]

    val agents: StateFlow<List<AgentEntity>> = chatRepository.getAllAgents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val conversations: StateFlow<List<ConversationEntity>> = chatRepository.getAllConversations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _activeMemoryCount = MutableStateFlow(0)
    val activeMemoryCount: StateFlow<Int> = _activeMemoryCount.asStateFlow()

    // We'll expose currentAgentId so ChatScreen can navigate to MemoryScreen with it
    private val _currentAgentId = MutableStateFlow<String?>(null)
    val currentAgentId: StateFlow<String?> = _currentAgentId.asStateFlow()

    init {
        conversationId?.let { id ->
            viewModelScope.launch {
                chatRepository.getMessagesForConversation(id).collect { msgs ->
                    _messages.value = msgs
                }
            }
            viewModelScope.launch {
                val conv = chatRepository.getConversationById(id)
                if (conv != null) {
                    _currentAgentId.value = conv.agentId
                    memoryRepository.getMemoriesForAgent(conv.agentId).collect { memories ->
                        _activeMemoryCount.value = memories.count { it.isActive }
                    }
                }
            }
        }
    }

    fun sendMessage(content: String, attachments: List<Uri> = emptyList()) {
        val cid = conversationId ?: return
        viewModelScope.launch {
            // Save User Message
            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = cid,
                role = "user",
                content = content,
                attachments = null
            )
            chatRepository.insertMessage(userMsg)
            
            // Extract working memory facts post-message
            workingMemoryManager.extractKeyFacts(cid, content)

            // Update conversation timestamp
            val conv = chatRepository.getConversationById(cid)
            if (conv != null) {
                chatRepository.upsertConversation(
                    conv.copy(
                        updatedAt = System.currentTimeMillis(),
                        messageCount = conv.messageCount + 1,
                        title = if (conv.messageCount == 0) content.take(30) + "..." else conv.title
                    )
                )
            }

            // Create temporary streaming assistant message
            _isStreaming.value = true

            val tempMessageId = UUID.randomUUID().toString()
            val assistantMsg = MessageEntity(
                id = tempMessageId,
                conversationId = cid,
                role = "assistant",
                content = "",
                isStreaming = true
            )
            chatRepository.insertMessage(assistantMsg)
            
            val convAfter = chatRepository.getConversationById(cid)
            if (convAfter != null) {
                chatRepository.upsertConversation(
                    convAfter.copy(
                        updatedAt = System.currentTimeMillis(),
                        messageCount = convAfter.messageCount + 1
                    )
                )
            }
            
            // Call network
            val agentId = convAfter?.agentId ?: return@launch
            val agent = agents.value.find { it.id == agentId } ?: return@launch
            
            // Inject Memory Context
            val enrichedContextMessage = memoryInjectionEngine.buildContextBlock(
                agentId = agentId,
                conversationId = cid,
                userMessage = content
            )

            // Map history
            val history = chatRepository.getLastNMessages(cid, 20)
                .filter { it.id != tempMessageId && it.id != userMsg.id }
                .sortedBy { it.timestamp }
                .map { com.ekam.baton.core.network.dto.McpMessageDto(role = it.role, content = it.content) }

            // Process attachments
            val attachmentDtos = mutableListOf<com.ekam.baton.core.network.mcp.AttachmentDto>()
            for (uri in attachments) {
                try {
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        attachmentDtos.add(com.ekam.baton.core.network.mcp.AttachmentDto(mimeType, base64))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            mcpMessageSender.sendUserMessage(
                agentId = agentId,
                endpointUrl = agent.mcpEndpointUrl,
                authHeader = null,
                conversationHistory = history,
                newUserMessage = enrichedContextMessage,
                attachments = attachmentDtos
            ).catch { e ->
                val finalMsg = chatRepository.getLastNMessages(cid, 1).firstOrNull { it.id == tempMessageId }
                if (finalMsg != null) {
                    chatRepository.updateMessage(
                        finalMsg.copy(
                            content = finalMsg.content + "\n\nError: ${e.message}",
                            isStreaming = false,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                _isStreaming.value = false
            }.collect { chunk ->
                val currentMsg = chatRepository.getLastNMessages(cid, 1).firstOrNull { it.id == tempMessageId }
                if (currentMsg != null) {
                    chatRepository.updateMessage(
                        currentMsg.copy(
                            content = currentMsg.content + chunk,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            
            // Flow complete
            val finalMsg = chatRepository.getLastNMessages(cid, 1).firstOrNull { it.id == tempMessageId }
            if (finalMsg != null) {
                chatRepository.updateMessage(finalMsg.copy(isStreaming = false))
            }
            
            // Trigger episodic memory generation every 10 messages using WorkManager
            if ((convAfter.messageCount + 1) % 10 == 0) {
                val workData = androidx.work.workDataOf(
                    "conversationId" to cid,
                    "agentId" to agentId
                )
                val request = androidx.work.OneTimeWorkRequestBuilder<com.ekam.baton.core.data.memory.EpisodicMemoryWorker>()
                    .setInputData(workData)
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueue(request)
            }
            
            _isStreaming.value = false
        }
    }
    
    fun createConversation(agentId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val newConv = ConversationEntity(
                id = newId,
                agentId = agentId,
                title = "New Chat",
            )
            chatRepository.upsertConversation(newConv)
            onCreated(newId)
        }
    }
    
    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
        }
    }
}
