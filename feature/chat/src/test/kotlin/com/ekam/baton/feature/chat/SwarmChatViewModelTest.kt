package com.ekam.baton.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.dao.SwarmDao
import com.ekam.baton.core.data.db.dao.TokenLedgerDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.MessageEntity
import com.ekam.baton.core.data.db.entity.SwarmEntity
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwarmChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeSwarm = SwarmEntity(
        id = "swarm-123",
        name = "Alpha Recon Cluster",
        leaderAgentId = "agent-leader",
        workerAgentIds = "[\"agent-worker\"]",
        topologyType = "P2P",
        intentContext = "System reconnaissance and security analysis"
    )

    private val fakeLeader = AgentEntity(
        id = "agent-leader",
        name = "Leader Bot",
        description = "Orchestrator",
        mcpEndpointUrl = "https://leader.local",
        authType = "NONE",
        authConfig = "{}",
        colorAccent = "#306EE8",
        securityMode = "ENFORCE",
        securityConfig = "{}"
    )

    private val fakeWorker = AgentEntity(
        id = "agent-worker",
        name = "Worker Bot",
        description = "Task Executor",
        mcpEndpointUrl = "https://worker.local",
        authType = "NONE",
        authConfig = "{}",
        colorAccent = "#00D2FF",
        securityMode = "ENFORCE",
        securityConfig = "{}"
    )

    private class FakeSwarmDao(val swarms: MutableMap<String, SwarmEntity>) : SwarmDao {
        override fun getAllSwarms(): Flow<List<SwarmEntity>> = flowOf(swarms.values.toList())
        override fun getActiveSwarms(): Flow<List<SwarmEntity>> = flowOf(swarms.values.filter { it.isActive })
        override suspend fun getSwarmById(id: String): SwarmEntity? = swarms[id]
        override fun observeSwarmById(id: String): Flow<SwarmEntity?> = flowOf(swarms[id])
        override suspend fun insertSwarm(swarm: SwarmEntity) { swarms[swarm.id] = swarm }
        override suspend fun updateSwarm(swarm: SwarmEntity) { swarms[swarm.id] = swarm }
        override suspend fun disbandSwarm(id: String, timestamp: Long) {
            swarms[id]?.let {
                swarms[id] = it.copy(isActive = false, disbandedAt = timestamp)
            }
        }
    }

    private class FakeTokenLedgerDao : TokenLedgerDao {
        val entries = mutableListOf<TokenLedgerEntity>()
        override suspend fun insertLedgerEntry(entry: TokenLedgerEntity) { entries.add(entry) }
        override fun getLedgerForAgent(agentId: String): Flow<List<TokenLedgerEntity>> =
            flowOf(entries.filter { it.agentId == agentId })
        override fun getLedgerForSwarm(swarmId: String): Flow<List<TokenLedgerEntity>> =
            flowOf(entries.filter { it.swarmId == swarmId })
        override fun getTotalCostSince(startTime: Long): Flow<Double> =
            flowOf(entries.filter { it.timestamp >= startTime }.sumOf { it.estimatedCostUsd })
        override fun getTotalTokensForSwarm(swarmId: String): Flow<Int> =
            flowOf(entries.filter { it.swarmId == swarmId }.sumOf { it.totalTokens })
        override fun getAllLedgerEntries(): Flow<List<TokenLedgerEntity>> =
            flowOf(entries.sortedByDescending { it.timestamp })
        override fun getGrandTotalTokens(): Flow<Int?> =
            flowOf(if (entries.isEmpty()) null else entries.sumOf { it.totalTokens })
        override fun getGrandTotalCost(): Flow<Double?> =
            flowOf(if (entries.isEmpty()) null else entries.sumOf { it.estimatedCostUsd })
        override suspend fun pruneOldLedgerEntries(keepCount: Int) {
            if (entries.size > keepCount) {
                val sorted = entries.sortedByDescending { it.timestamp }.take(keepCount)
                entries.clear()
                entries.addAll(sorted)
            }
        }
    }

    private class FakeTestAgentDao(val agents: List<AgentEntity>) : AgentDao {
        override fun getAllAgents(): Flow<List<AgentEntity>> = flowOf(agents)
        override suspend fun getAgentById(id: String): AgentEntity? = agents.find { it.id == id }
        override suspend fun upsertAgent(agent: AgentEntity) {}
        override suspend fun deleteAgent(id: String) {}
        override suspend fun updateLastUsed(id: String, timestamp: Long) {}
        override suspend fun updateBlockedStatus(id: String, isBlocked: Boolean) {}
        override fun isAgentBlocked(id: String): Flow<Boolean?> = flowOf(false)
    }

    private class FakeTestMessageDao : MessageDao {
        val messages = mutableListOf<MessageEntity>()
        override fun getMessagesForConversation(conversationId: String): androidx.paging.PagingSource<Int, MessageEntity> =
            throw UnsupportedOperationException()
        override fun observeMessagesForConversation(conversationId: String): Flow<List<MessageEntity>> =
            flowOf(messages.filter { it.conversationId == conversationId })
        override suspend fun insertMessage(message: MessageEntity) { messages.add(message) }
        override suspend fun updateMessage(message: MessageEntity) {
            val idx = messages.indexOfFirst { it.id == message.id }
            if (idx != -1) messages[idx] = message
        }
        override suspend fun getMessageById(id: String): MessageEntity? = messages.find { it.id == id }
        override suspend fun getLastNMessages(conversationId: String, n: Int): List<MessageEntity> =
            messages.filter { it.conversationId == conversationId }.takeLast(n)
        override suspend fun deleteMessage(id: String) { messages.removeAll { it.id == id } }
        override suspend fun deleteMessagesForConversation(conversationId: String) {
            messages.removeAll { it.conversationId == conversationId }
        }
        override suspend fun clearStreamingStates() {}
    }

    private lateinit var swarmDao: FakeSwarmDao
    private lateinit var tokenLedgerDao: FakeTokenLedgerDao
    private lateinit var agentDao: FakeTestAgentDao
    private lateinit var messageDao: FakeTestMessageDao
    private lateinit var viewModel: SwarmChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val swarmMap = mutableMapOf(fakeSwarm.id to fakeSwarm)
        swarmDao = FakeSwarmDao(swarmMap)
        tokenLedgerDao = FakeTokenLedgerDao()
        agentDao = FakeTestAgentDao(listOf(fakeLeader, fakeWorker))
        messageDao = FakeTestMessageDao()

        val savedState = SavedStateHandle(mapOf("swarmId" to fakeSwarm.id))
        viewModel = SwarmChatViewModel(
            swarmDao = swarmDao,
            tokenLedgerDao = tokenLedgerDao,
            agentDao = agentDao,
            messageDao = messageDao,
            savedStateHandle = savedState
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test swarmId and member agents loaded correctly`() = runTest(testDispatcher) {
        assertEquals("swarm-123", viewModel.swarmId)

        // Advance dispatcher to collect initial states
        testScheduler.advanceUntilIdle()

        val members = viewModel.memberAgents.value
        assertEquals(2, members.size)
        assertTrue(members.any { it.id == "agent-leader" })
        assertTrue(members.any { it.id == "agent-worker" })
    }

    @Test
    fun `test sendMessage orchestrates leader and worker responses and logs tokens`() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        viewModel.sendMessage("Execute security penetration verification")

        // Advance past simulated coroutine delays (800ms + 1200ms)
        testScheduler.advanceTimeBy(3000)
        testScheduler.advanceUntilIdle()

        // Verify messages in DAO: 1 user, 1 leader response, 1 worker response
        assertEquals(3, messageDao.messages.size)
        assertEquals("user", messageDao.messages[0].role)
        assertEquals("assistant", messageDao.messages[1].role)
        assertEquals("assistant", messageDao.messages[2].role)

        // Verify token ledger entries logged: 2 entries (one per agent)
        assertEquals(2, tokenLedgerDao.entries.size)
        assertEquals("agent-leader", tokenLedgerDao.entries[0].agentId)
        assertEquals("agent-worker", tokenLedgerDao.entries[1].agentId)
        assertTrue(tokenLedgerDao.entries.sumOf { it.totalTokens } > 0)

        // Verify dispatching state reset
        assertFalse(viewModel.isDispatching.value)
        assertNull(viewModel.activeAgentStatus.value)
    }

    @Test
    fun `test disbandSwarm marks swarm inactive and invokes callback`() = runTest(testDispatcher) {
        var disbandedCallbackInvoked = false

        viewModel.disbandSwarm {
            disbandedCallbackInvoked = true
        }

        testScheduler.advanceUntilIdle()

        assertTrue(disbandedCallbackInvoked)
        val swarm = swarmDao.getSwarmById("swarm-123")
        assertNotNull(swarm)
        assertFalse(swarm!!.isActive)
        assertNotNull(swarm.disbandedAt)
    }
}
