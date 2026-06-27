package com.ekam.baton.core.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.ekam.baton.core.data.db.dao.ConversationDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.ConversationEntity
import com.ekam.baton.core.data.db.entity.MessageEntity
import com.ekam.baton.core.data.db.entity.MemoryEntity
import com.ekam.baton.core.data.memory.MemoryInjectionEngine
import com.ekam.baton.core.data.memory.WorkingMemoryManager
import com.ekam.baton.core.network.mcp.McpMessageSender
import com.ekam.baton.core.network.mcp.McpTransport
import com.ekam.baton.core.network.mcp.McpConnectionManager
import com.ekam.baton.core.network.mcp.McpTool
import com.ekam.baton.core.network.mcp.ToolAuthorizationManager
import com.ekam.baton.core.data.model.Conversation
import com.ekam.baton.core.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

// ── Fakes ────────────────────────────────────────────────────────────────────

class FakeConversationPagingSource(private val data: List<ConversationEntity>) : PagingSource<Int, ConversationEntity>() {
    override fun getRefreshKey(state: PagingState<Int, ConversationEntity>): Int? = null
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ConversationEntity> = LoadResult.Page(data, null, null)
}

class FakeMessagePagingSource(private val data: List<MessageEntity>) : PagingSource<Int, MessageEntity>() {
    override fun getRefreshKey(state: PagingState<Int, MessageEntity>): Int? = null
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MessageEntity> = LoadResult.Page(data, null, null)
}

class FakeConversationDao : ConversationDao {
    val conversations = mutableMapOf<String, ConversationEntity>()
    override fun getAllConversations(query: String): PagingSource<Int, ConversationEntity> = 
        FakeConversationPagingSource(conversations.values.toList())
    override suspend fun getConversationById(id: String): ConversationEntity? = conversations[id]
    override suspend fun upsertConversation(conversation: ConversationEntity) { conversations[conversation.id] = conversation }
    override suspend fun deleteConversation(id: String) { conversations.remove(id) }
}

class FakeMessageDao : MessageDao {
    val messages = mutableListOf<MessageEntity>()
    override fun getMessagesForConversation(conversationId: String): PagingSource<Int, MessageEntity> = 
        FakeMessagePagingSource(messages.filter { it.conversationId == conversationId })
    override suspend fun insertMessage(message: MessageEntity) { messages.add(message) }
    override suspend fun updateMessage(message: MessageEntity) {
        val idx = messages.indexOfFirst { it.id == message.id }
        if (idx != -1) messages[idx] = message
    }
    override suspend fun getMessageById(id: String): MessageEntity? = messages.find { it.id == id }
    override suspend fun getLastNMessages(conversationId: String, n: Int): List<MessageEntity> =
        messages.filter { it.conversationId == conversationId }.takeLast(n)
}

class ChatFakeMemoryDao : MemoryDao {
    override fun getAllMemories(): Flow<List<MemoryEntity>> = flow { emit(emptyList()) }
    override fun getMemoriesByLayer(layer: String): Flow<List<MemoryEntity>> = flow { emit(emptyList()) }
    override fun getMemoriesForAgent(agentId: String): Flow<List<MemoryEntity>> = flow { emit(emptyList()) }
    override fun searchMemories(query: String): Flow<List<MemoryEntity>> = flow { emit(emptyList()) }
    override suspend fun upsertMemory(memory: MemoryEntity) {}
    override suspend fun deleteMemory(id: String) {}
    override suspend fun toggleMemoryActive(id: String, isActive: Boolean) {}
    override suspend fun getTopSemanticMemories(agentId: String): List<MemoryEntity> = emptyList()
    override suspend fun getRecentEpisodicMemories(agentId: String, cutoffTime: Long): List<MemoryEntity> = emptyList()
    override suspend fun getWorkingMemories(conversationId: String): List<MemoryEntity> = emptyList()
    override suspend fun updateLastAccessedTime(ids: List<String>, time: Long) {}
    override suspend fun clearWorkingMemoriesForConversation(conversationId: String) {}
    override suspend fun clearAllMemories() {}
}

class FakeMcpTransport : McpTransport {
    var responseChunks = listOf("Hello", " there!")
    override suspend fun initialize(endpointUrl: String, authHeader: String?): Result<JsonObject> = Result.success(buildJsonObject {})
    override suspend fun listTools(endpointUrl: String, authHeader: String?): Result<List<McpTool>> = Result.success(listOf(McpTool("chat", "Chat tool", buildJsonObject {})))
    override fun callTool(endpointUrl: String, authHeader: String?, toolName: String, arguments: JsonObject): Flow<String> = flow {
        responseChunks.forEach { emit(it) }
    }
    override suspend fun ping(endpointUrl: String): Boolean = true
}

// ── Test Class ───────────────────────────────────────────────────────────────

class ChatRepositoryTest {

    private lateinit var conversationDao: FakeConversationDao
    private lateinit var messageDao: FakeMessageDao
    private lateinit var agentDao: FakeAgentDao
    private lateinit var memoryDao: ChatFakeMemoryDao
    private lateinit var transport: FakeMcpTransport
    private lateinit var mcpMessageSender: McpMessageSender
    private lateinit var repository: ChatRepository

    @Before
    fun setUp() {
        conversationDao = FakeConversationDao()
        messageDao = FakeMessageDao()
        agentDao = FakeAgentDao()
        memoryDao = ChatFakeMemoryDao()
        transport = FakeMcpTransport()
        
        val authManager = ToolAuthorizationManager()
        val connectionManager = McpConnectionManager(transport)
        mcpMessageSender = McpMessageSender(connectionManager, transport, authManager)
        
        val engine = MemoryInjectionEngine(memoryDao)
        val workingMemoryManager = WorkingMemoryManager(memoryDao)
        
        repository = ChatRepository(
            conversationDao,
            messageDao,
            agentDao,
            memoryDao,
            mcpMessageSender,
            engine,
            workingMemoryManager
        )
    }

    @Test
    fun `sendMessageWithResponse saves user message and streaming assistant message`() = runTest {
        // Arrange
        val agentId = "agent-1"
        val convId = "conv-1"
        agentDao.upsertAgent(AgentEntity(
            id = agentId,
            name = "Bot",
            description = "Desc",
            mcpEndpointUrl = "url",
            authType = "none",
            authConfig = "{}",
            colorAccent = "#FFFFFF"
        ))
        conversationDao.upsertConversation(ConversationEntity(convId, agentId, "Old Title"))

        // Act
        val resultFlow = repository.sendMessageWithResponse(convId, "Hi Bot")
        val chunks = resultFlow.toList()

        // Assert
        assertEquals(listOf("Hello", " there!"), chunks)

        // Verify messages in DB
        val storedMessages = messageDao.messages
        assertEquals(2, storedMessages.size)
        
        val userMsg = storedMessages.find { it.role == "user" }
        assertEquals("Hi Bot", userMsg?.content)

        val assistantMsg = storedMessages.find { it.role == "assistant" }
        assertEquals("Hello there!", assistantMsg?.content)
        assertFalse(assistantMsg?.isStreaming ?: true)
    }

    @Test
    fun `sendMessageWithResponse updates conversation metadata`() = runTest {
        // Arrange
        val agentId = "agent-1"
        val convId = "conv-1"
        agentDao.upsertAgent(AgentEntity(
            id = agentId,
            name = "Bot",
            description = "Desc",
            mcpEndpointUrl = "url",
            authType = "none",
            authConfig = "{}",
            colorAccent = "#FFFFFF"
        ))
        
        val initialConv = Conversation(
            id = convId,
            agentId = agentId,
            title = "New Chat",
            messageCount = 0,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        conversationDao.upsertConversation(initialConv.toEntity())

        // Act
        repository.sendMessageWithResponse(convId, "First message here").toList()

        // Assert
        val conv = conversationDao.getConversationById(convId)
        assertEquals(2, conv?.messageCount) // User + Assistant
        assertEquals("First message here...", conv?.title)
    }
}
