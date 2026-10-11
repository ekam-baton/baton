package com.ekam.baton.feature.settings

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.dao.TokenLedgerDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExecutionLedgerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeAuditLogs = listOf(
        AuditLogEntity(
            id = "audit-1",
            entityName = "AgentEntity",
            entityId = "agent-1",
            action = "INSERT",
            timestamp = 1000L,
            deviceId = "device-alpha",
            payloadJson = "{}",
            previousHash = "0000000000000000",
            hash = "a1b2c3d4e5f6g7h8",
            signature = "signed-sig-1"
        )
    )

    private val fakeLedgerEntries = listOf(
        TokenLedgerEntity(
            id = "t-1",
            agentId = "agent-1",
            swarmId = "swarm-1",
            modelName = "claude-3-5-sonnet",
            promptTokens = 150,
            completionTokens = 350,
            totalTokens = 500,
            estimatedCostUsd = 0.0025,
            burnRateTks = 45.0,
            timestamp = 1000L,
            actionContext = "Swarm Planning"
        ),
        TokenLedgerEntity(
            id = "t-2",
            agentId = "agent-2",
            swarmId = null,
            modelName = "gpt-4o",
            promptTokens = 200,
            completionTokens = 800,
            totalTokens = 1000,
            estimatedCostUsd = 0.0050,
            burnRateTks = 30.0,
            timestamp = 2000L,
            actionContext = "Direct Q&A"
        )
    )

    private val fakeAgents = listOf(
        AgentEntity(
            id = "agent-1",
            name = "Sentinel Alpha",
            description = "Security Agent",
            mcpEndpointUrl = "https://agent1.local",
            authType = "NONE",
            authConfig = "{}",
            colorAccent = "#306EE8",
            securityMode = "ENFORCE",
            securityConfig = "{}"
        ),
        AgentEntity(
            id = "agent-2",
            name = "Recon Beta",
            description = "Reconnaissance Agent",
            mcpEndpointUrl = "https://agent2.local",
            authType = "NONE",
            authConfig = "{}",
            colorAccent = "#00D2FF",
            securityMode = "ENFORCE",
            securityConfig = "{}"
        )
    )

    private class FakeAuditDao(val logs: MutableList<AuditLogEntity>) : AuditDao {
        var pruneCalledWith: Int? = null

        override suspend fun insertAuditLog(log: AuditLogEntity) { logs.add(log) }
        override fun getAllAuditLogs(): Flow<List<AuditLogEntity>> = flowOf(logs.toList())
        override suspend fun getLastAuditLog(): AuditLogEntity? = logs.lastOrNull()
        override suspend fun getAllAuditLogsSync(): List<AuditLogEntity> = logs.toList()
        override suspend fun pruneOldLogs(keepLastN: Int) {
            pruneCalledWith = keepLastN
            if (logs.size > keepLastN) {
                val kept = logs.takeLast(keepLastN)
                logs.clear()
                logs.addAll(kept)
            }
        }
    }

    private class FakeTokenLedgerDao(val entries: MutableList<TokenLedgerEntity>) : TokenLedgerDao {
        var pruneCalledWith: Int? = null

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
            pruneCalledWith = keepCount
            if (entries.size > keepCount) {
                val kept = entries.sortedByDescending { it.timestamp }.take(keepCount)
                entries.clear()
                entries.addAll(kept)
            }
        }
    }

    private class FakeAgentDao(val agents: List<AgentEntity>) : AgentDao {
        override fun getAllAgents(): Flow<List<AgentEntity>> = flowOf(agents)
        override suspend fun getAgentById(id: String): AgentEntity? = agents.find { it.id == id }
        override suspend fun upsertAgent(agent: AgentEntity) {}
        override suspend fun deleteAgent(id: String) {}
        override suspend fun updateLastUsed(id: String, timestamp: Long) {}
    }

    private lateinit var auditDao: FakeAuditDao
    private lateinit var tokenLedgerDao: FakeTokenLedgerDao
    private lateinit var agentDao: FakeAgentDao
    private lateinit var viewModel: ExecutionLedgerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        auditDao = FakeAuditDao(fakeAuditLogs.toMutableList())
        tokenLedgerDao = FakeTokenLedgerDao(fakeLedgerEntries.toMutableList())
        agentDao = FakeAgentDao(fakeAgents)
        viewModel = ExecutionLedgerViewModel(auditDao, tokenLedgerDao, agentDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test audit logs stream reflects DAO contents`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val logs = viewModel.auditLogs.first()
        assertEquals(1, logs.size)
        assertEquals("audit-1", logs[0].id)
        assertEquals("INSERT", logs[0].action)
    }

    @Test
    fun `test token ledger entries and metrics flow calculation`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val entries = viewModel.ledgerEntries.first()
        assertEquals(2, entries.size)

        val totalTokens = viewModel.grandTotalTokens.first()
        assertEquals(1500, totalTokens)

        val totalCost = viewModel.grandTotalCost.first()
        assertEquals(0.0075, totalCost, 0.00001)
    }

    @Test
    fun `test agents map correctly associates by id`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val map = viewModel.agentsMap.first()
        assertEquals(2, map.size)
        assertEquals("Sentinel Alpha", map["agent-1"]?.name)
        assertEquals("Recon Beta", map["agent-2"]?.name)
    }

    @Test
    fun `test model filtering filters entries correctly`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        // Default (null) should return all
        assertEquals(2, viewModel.filteredLedgerEntries.value.size)

        // Filter by claude-3-5-sonnet
        viewModel.setModelFilter("claude-3-5-sonnet")
        testDispatcher.scheduler.advanceUntilIdle()
        val claudeEntries = viewModel.filteredLedgerEntries.value
        assertEquals(1, claudeEntries.size)
        assertEquals("t-1", claudeEntries[0].id)

        // Filter by ALL resets filter
        viewModel.setModelFilter("ALL")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.filteredLedgerEntries.value.size)
    }

    @Test
    fun `test pruning triggers DAO pruning functions`() = runTest {
        viewModel.triggerRollingGenesisBlock()
        viewModel.pruneLedgerEntries(500)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1000, auditDao.pruneCalledWith)
        assertEquals(500, tokenLedgerDao.pruneCalledWith)
    }

    @Test
    fun `test generateExportReport contains comprehensive analytics and proofs`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val report = viewModel.generateExportReport()

        assertTrue(report.contains("BATON V2 EXECUTION & CRYPTOGRAPHIC AUDIT REPORT"))
        assertTrue(report.contains("Total Transactions: 2"))
        assertTrue(report.contains("Grand Total Tokens: 1500"))
        assertTrue(report.contains("agent-1"))
        assertTrue(report.contains("claude-3-5-sonnet"))
        assertTrue(report.contains("Swarm Planning"))
        assertTrue(report.contains("IMMUTABLE CRYPTOGRAPHIC AUDIT TRAIL"))
        assertTrue(report.contains("a1b2c3d4e5f6g7h8"))
    }
}
