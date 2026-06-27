package com.ekam.baton.feature.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.Conversation
import com.ekam.baton.core.data.model.Message
import com.ekam.baton.core.data.repository.ChatRepository
import com.ekam.baton.core.data.repository.MemoryRepository
import com.ekam.baton.core.network.mcp.AttachmentDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val appPreferences: com.ekam.baton.core.data.preferences.AppPreferences,
    private val toolAuthManager: com.ekam.baton.core.network.mcp.ToolAuthorizationManager,
    private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"
        const val DEFAULT_NEW_CHAT_TITLE = "New Chat"
        const val EPISODIC_MEMORY_INTERVAL = 10
    }

    val keyboardShortcuts: StateFlow<List<com.ekam.baton.core.data.preferences.KeyboardShortcut>> = appPreferences.keyboardShortcuts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveKeyboardShortcuts(shortcuts: List<com.ekam.baton.core.data.preferences.KeyboardShortcut>) {
        viewModelScope.launch {
            appPreferences.setKeyboardShortcuts(shortcuts)
        }
    }


    private val conversationId: String? = savedStateHandle["conversationId"]

    val agents: StateFlow<List<Agent>> = chatRepository.getAllAgents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val conversations: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Conversation>> = _searchQuery
        .flatMapLatest { query ->
            chatRepository.getAllConversations(query)
        }
        .cachedIn(viewModelScope)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Message>> = flowOf(conversationId)
        .filterNotNull()
        .flatMapLatest { id ->
            chatRepository.getMessagesForConversation(id)
        }
        .cachedIn(viewModelScope)

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError.asStateFlow()

    private val _activeMemoryCount = MutableStateFlow(0)
    val activeMemoryCount: StateFlow<Int> = _activeMemoryCount.asStateFlow()

    private val _currentAgentId = MutableStateFlow<String?>(null)
    val currentAgentId: StateFlow<String?> = _currentAgentId.asStateFlow()

    private val _availableTools = MutableStateFlow<List<com.ekam.baton.core.network.mcp.McpTool>>(emptyList())
    val availableTools: StateFlow<List<com.ekam.baton.core.network.mcp.McpTool>> = _availableTools.asStateFlow()

    val toolAuthRequests = toolAuthManager.authorizationRequests

    fun resolveToolAuth(request: com.ekam.baton.core.network.mcp.ToolAuthorizationRequest, isApproved: Boolean) {
        request.onResult(isApproved)
    }
    
    fun clearError() {
        _uiError.value = null
    }

    init {
        conversationId?.let { id ->
            viewModelScope.launch {
                val conv = chatRepository.getConversationById(id)
                if (conv != null) {
                    _currentAgentId.value = conv.agentId
                    memoryRepository.getMemoriesForAgent(conv.agentId).collect { memories ->
                        _activeMemoryCount.value = memories.count { it.isActive }
                    }
                }
            }
            viewModelScope.launch {
                val conv = chatRepository.getConversationById(id)
                if (conv != null) {
                    try {
                        _availableTools.value = chatRepository.getAvailableTools(conv.agentId)
                    } catch (e: Exception) {
                        _uiError.value = "Failed to load agent tools: ${e.message}"
                    }
                }
            }
        }
    }

    fun sendMessage(content: String, attachments: List<Uri> = emptyList()) {
        val cid = conversationId ?: return
        viewModelScope.launch {
            _isStreaming.value = true

            // Process attachments on IO dispatcher
            val attachmentDtos = withContext(Dispatchers.IO) {
                attachments.mapNotNull { uri ->
                    try {
                        val mimeType = context.contentResolver.getType(uri) ?: MIME_TYPE_OCTET_STREAM
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            AttachmentDto(mimeType, base64)
                        } else null
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }

            try {
                chatRepository.sendMessageWithResponse(cid, content, attachmentDtos).collect()
            } catch (e: Exception) {
                // Network errors are mostly handled inside repository by updating the message entity, 
                // but surfacing general failures here as well.
                _uiError.value = "Failed to send message: ${e.message}"
            } finally {
                _isStreaming.value = false
                checkEpisodicMemoryGeneration(cid)
            }
        }
    }

    private suspend fun checkEpisodicMemoryGeneration(cid: String) {
        val conv = chatRepository.getConversationById(cid) ?: return
        if (conv.messageCount > 0 && conv.messageCount % EPISODIC_MEMORY_INTERVAL == 0) {
            val workData = androidx.work.workDataOf(
                "conversationId" to cid,
                "agentId" to conv.agentId
            )
            val request = androidx.work.OneTimeWorkRequestBuilder<com.ekam.baton.core.data.memory.EpisodicMemoryWorker>()
                .setInputData(workData)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueue(request)
        }
    }

    fun createConversation(agentId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val newConv = Conversation(
                id = newId,
                agentId = agentId,
                title = DEFAULT_NEW_CHAT_TITLE,
            )
            try {
                chatRepository.upsertConversation(newConv)
                onCreated(newId)
            } catch (e: Exception) {
                 _uiError.value = "Failed to create conversation."
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteConversation(id)
            } catch (e: Exception) {
                 _uiError.value = "Failed to delete conversation."
            }
        }
    }

    fun executeTool(toolName: String, arguments: kotlinx.serialization.json.JsonObject) {
        val cid = conversationId ?: return
        viewModelScope.launch {
            _isStreaming.value = true
            try {
                chatRepository.executeToolManual(cid, toolName, arguments).collect()
            } catch (e: Exception) {
                _uiError.value = "Tool execution failed: ${e.message}"
            } finally {
                _isStreaming.value = false
            }
        }
    }
}
