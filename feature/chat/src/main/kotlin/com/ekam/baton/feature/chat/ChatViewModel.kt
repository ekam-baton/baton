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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first

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

    private val _agentActivityStatus = MutableStateFlow<String?>(null)
    val agentActivityStatus: StateFlow<String?> = _agentActivityStatus.asStateFlow()

    /** Upload progress (0.0–1.0), null when not uploading */
    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _activeMemoryCount = MutableStateFlow(0)
    val activeMemoryCount: StateFlow<Int> = _activeMemoryCount.asStateFlow()

    private val _currentAgentId = MutableStateFlow<String?>(null)
    val currentAgentId: StateFlow<String?> = _currentAgentId.asStateFlow()

    private val _currentAgent = MutableStateFlow<Agent?>(null)
    val currentAgent: StateFlow<Agent?> = _currentAgent.asStateFlow()

    private val _availableTools = MutableStateFlow<List<com.ekam.baton.core.network.mcp.McpTool>>(emptyList())
    val availableTools: StateFlow<List<com.ekam.baton.core.network.mcp.McpTool>> = _availableTools.asStateFlow()

    val toolAuthRequests = toolAuthManager.authorizationRequests

    fun resolveToolAuth(request: com.ekam.baton.core.network.mcp.ToolAuthorizationRequest, isApproved: Boolean) {
        request.onResult(isApproved)
    }

    fun clearError() {
        _uiError.value = null
    }

    fun updateAgentEndpoint(newUrl: String) {
        val current = _currentAgent.value ?: return
        viewModelScope.launch {
            try {
                val updatedAgent = current.copy(mcpEndpointUrl = newUrl)
                chatRepository.upsertAgent(updatedAgent)
                _currentAgent.value = updatedAgent
            } catch (e: Exception) {
                _uiError.value = "Failed to update agent endpoint: ${e.message}"
            }
        }
    }

    init {
        conversationId?.let { id ->
            // FIX: Single DB fetch shared across both tasks — eliminates the duplicate
            // getConversationById() call and removes the init-time race condition where
            // two coroutines were fetching the same row independently.
            viewModelScope.launch {
                val conv = chatRepository.getConversationById(id) ?: return@launch
                _currentAgentId.value = conv.agentId
                _currentAgent.value = chatRepository.getAgentById(conv.agentId)

                // Observe memory count on the same coroutine, after agent is resolved
                memoryRepository.getMemoriesForAgent(conv.agentId).collect { memories ->
                    _activeMemoryCount.value = memories.count { it.isActive }
                }
            }

            // Tool loading runs concurrently but does its own fetch — intentional
            // because it may require a network round-trip (MCP listTools) and should
            // not block the memory observer above.
            viewModelScope.launch {
                val conv = chatRepository.getConversationById(id) ?: return@launch
                try {
                    _availableTools.value = chatRepository.getAvailableTools(conv.agentId)
                } catch (e: Exception) {
                    _uiError.value = "Failed to load agent tools: ${e.message}"
                }
            }
        }
    }

    fun sendMessage(content: String, attachments: List<Uri> = emptyList()) {
        val cid = conversationId ?: return
        // Prevent double-submission while a response is already streaming
        if (_isStreaming.value) return
        viewModelScope.launch {
            _isStreaming.value = true
            _agentActivityStatus.value = if (attachments.isNotEmpty()) "Preparing media attachments..." else "Injecting working memory..."

            // Process attachments on IO dispatcher
            val attachmentDtos = withContext(Dispatchers.IO) {
                attachments.mapNotNull { uri ->
                    try {
                        val mimeType = context.contentResolver.getType(uri) ?: MIME_TYPE_OCTET_STREAM
                        var name: String? = null
                        var size: Long? = null
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (cursor.moveToFirst()) {
                                name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                                size = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                            }
                        }
                        AttachmentDto(
                            mimeType = mimeType,
                            dataBase64 = null,
                            fileName = name,
                            fileSize = size,
                            uri = uri.toString()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }

            _agentActivityStatus.value = "Authenticating..."
            val authHeader = fetchJwtToken()

            _agentActivityStatus.value = "Opening secure E2EE channel..."

            try {
                _agentActivityStatus.value = "Thinking..."
                // FIX: Use collect {} to make it clear we are consuming all chunks;
                // the plain .collect() overload with no lambda works but is misleading.
                chatRepository.sendMessageWithResponse(
                    conversationId = cid,
                    content = content,
                    attachments = attachmentDtos,
                    authHeader = authHeader
                ).collect {}
            } catch (e: Exception) {
                // Network errors are mostly handled inside repository by updating the
                // message entity, but surface general failures here as well.
                _uiError.value = "Failed to send message: ${e.message}"
            } finally {
                // FIX: Run episodic memory check BEFORE clearing the status, so the
                // "Extracting key facts..." label is only shown during actual work and
                // is never displayed on the error path (error already set above).
                try {
                    _agentActivityStatus.value = "Extracting key facts..."
                    checkEpisodicMemoryGeneration(cid)
                } catch (_: Exception) {
                    // Non-fatal; best-effort episodic memory
                } finally {
                    _isStreaming.value = false
                    _agentActivityStatus.value = null
                }
            }
        }
    }

    private suspend fun fetchJwtToken(): String? {
        val backendUrlStr = appPreferences.backendUrl.first()
        val jwtSecretStr = appPreferences.jwtSecret.first()
        if (jwtSecretStr.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val loginUrl = if (backendUrlStr.endsWith("/")) "${backendUrlStr}login" else "${backendUrlStr}/login"
                val url = java.net.URL(loginUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                
                val jsonInputString = "{\"secret\": \"$jwtSecretStr\"}"
                connection.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }
                
                if (connection.responseCode == 200) {
                    val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(responseBody)
                    "Bearer ${json.getString("token")}"
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
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
            try {
                val existing = chatRepository.getConversationByAgentId(agentId)
                if (existing != null) {
                    onCreated(existing.id)
                    return@launch
                }
                val newId = UUID.randomUUID().toString()
                val newConv = Conversation(
                    id = newId,
                    agentId = agentId,
                    title = DEFAULT_NEW_CHAT_TITLE,
                )
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

    fun deleteMessage(id: String) {
        viewModelScope.launch {
            try {
                chatRepository.deleteMessage(id)
            } catch (e: Exception) {
                _uiError.value = "Failed to delete message."
            }
        }
    }

    fun forwardMessage(message: Message, targetAgentId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val existing = chatRepository.getConversationByAgentId(targetAgentId)
                val cid = existing?.id ?: run {
                    val newId = UUID.randomUUID().toString()
                    val newConv = Conversation(
                        id = newId,
                        agentId = targetAgentId,
                        title = DEFAULT_NEW_CHAT_TITLE
                    )
                    chatRepository.upsertConversation(newConv)
                    newId
                }
                
                // Decode attachments from string to Dto
                val attachments = message.attachments?.let {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<List<AttachmentDto>>(it)
                } ?: emptyList()

                // Send the message
                chatRepository.sendMessageWithResponse(cid, message.content, attachments).collect {}
                
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch(e: Exception) {
                _uiError.value = "Failed to forward: ${e.message}"
            }
        }
    }

    fun executeTool(toolName: String, arguments: kotlinx.serialization.json.JsonObject) {
        val cid = conversationId ?: return
        viewModelScope.launch {
            _isStreaming.value = true
            _agentActivityStatus.value = "Executing tool $toolName..."
            try {
                chatRepository.executeToolManual(cid, toolName, arguments).collect {}
            } catch (e: Exception) {
                _uiError.value = "Tool execution failed: ${e.message}"
            } finally {
                _isStreaming.value = false
                _agentActivityStatus.value = null
            }
        }
    }
}
