package com.ekam.baton.feature.settings

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.data.preferences.SessionManager
import com.ekam.baton.core.data.preferences.SubscriptionManager
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.repository.WipeDataManager
import com.ekam.baton.core.network.tunnel.ConnectionPoolManager
import com.ekam.baton.core.network.tunnel.PoolableConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAgentDao : AgentDao {
        val agents = mutableListOf<AgentEntity>()
        override fun getAllAgents(): Flow<List<AgentEntity>> = flowOf(agents.toList())
        override suspend fun getAgentById(id: String): AgentEntity? = agents.find { it.id == id }
        override suspend fun upsertAgent(agent: AgentEntity) {
            agents.removeAll { it.id == agent.id }
            agents.add(agent)
        }
        override suspend fun deleteAgent(id: String) {
            agents.removeAll { it.id == id }
        }
        override suspend fun updateLastUsed(id: String, timestamp: Long) {}
        override suspend fun updateBlockedStatus(id: String, isBlocked: Boolean) {}
        override fun isAgentBlocked(id: String): Flow<Boolean?> = flowOf(false)
    }

    private class FakeAuditDao : AuditDao {
        val logs = mutableListOf<AuditLogEntity>()
        override suspend fun insertAuditLog(log: AuditLogEntity) { logs.add(log) }
        override fun getAllAuditLogs(): Flow<List<AuditLogEntity>> = flowOf(logs.toList())
        override suspend fun getLastAuditLog(): AuditLogEntity? = logs.lastOrNull()
        override suspend fun getAllAuditLogsSync(): List<AuditLogEntity> = logs.toList()
        override suspend fun pruneOldLogs(keepLastN: Int) {}
    }

    private class FakeAppPreferences : AppPreferences(_context = null) {
        val stealthFlow = MutableStateFlow(false)
        val duressPinFlow = MutableStateFlow<String?>(null)
        var registrationCleared = false

        override val stealthModeEnabled: Flow<Boolean> get() = stealthFlow
        override val duressPin: Flow<String?> get() = duressPinFlow

        override suspend fun setStealthModeEnabled(enabled: Boolean) {
            stealthFlow.value = enabled
        }

        override suspend fun setDuressPin(pin: String?) {
            duressPinFlow.value = pin
        }

        override suspend fun clearRegistration() {
            registrationCleared = true
        }
    }

    private class FakeWipeDataManager : WipeDataManager(database = null) {
        var wipedAllData = false
        var shredCalled = false

        override suspend fun wipeAllData() {
            wipedAllData = true
        }

        override suspend fun emergencyCryptographicShred() {
            shredCalled = true
        }
    }

    private class MockPoolableConnection(
        override val id: String,
        override val lastAccessedAt: Long = System.currentTimeMillis()
    ) : PoolableConnection {
        var isDisconnected = false
        override suspend fun disconnect() {
            isDisconnected = true
        }
    }

    private lateinit var appPreferences: FakeAppPreferences
    private lateinit var agentDao: FakeAgentDao
    private lateinit var agentRepository: AgentRepository
    private lateinit var auditDao: FakeAuditDao
    private lateinit var wipeDataManager: FakeWipeDataManager
    private lateinit var sessionManager: SessionManager
    private lateinit var subscriptionManager: SubscriptionManager
    private lateinit var connectionPoolManager: ConnectionPoolManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        appPreferences = FakeAppPreferences()
        agentDao = FakeAgentDao()
        agentRepository = AgentRepository(agentDao)
        auditDao = FakeAuditDao()
        wipeDataManager = FakeWipeDataManager()
        sessionManager = SessionManager(appPreferences)
        subscriptionManager = SubscriptionManager()
        connectionPoolManager = ConnectionPoolManager()

        viewModel = SettingsViewModel(
            appPreferences = appPreferences,
            agentRepository = agentRepository,
            wipeDataManager = wipeDataManager,
            sessionManager = sessionManager,
            subscriptionManager = subscriptionManager,
            auditDao = auditDao,
            billingManager = null,
            ugcManager = null,
            connectionPoolManager = connectionPoolManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStealthModeToggleDisconnectsConnections() = runTest(testDispatcher) {
        // Register an active connection into the pool
        val testConn = MockPoolableConnection("conn-1")
        connectionPoolManager.registerConnection(testConn)
        assertEquals(1, connectionPoolManager.getActiveConnectionCount())
        assertFalse(testConn.isDisconnected)

        // Initial state is false
        assertEquals(false, viewModel.stealthModeActive.value)

        // Toggle stealth mode ON
        viewModel.toggleStealthMode()
        advanceUntilIdle()

        // Should be enabled, and all connections disconnected
        assertTrue(appPreferences.stealthFlow.value)
        assertTrue(testConn.isDisconnected)
        assertEquals(0, connectionPoolManager.getActiveConnectionCount())

        // Toggle stealth mode OFF
        viewModel.toggleStealthMode()
        advanceUntilIdle()
        assertFalse(appPreferences.stealthFlow.value)
    }

    @Test
    fun testDuressPinSettingAndClearing() = runTest(testDispatcher) {
        assertNull(viewModel.duressPin.value)

        // Set duress PIN
        viewModel.setDuressPin("9999")
        advanceUntilIdle()
        assertEquals("9999", appPreferences.duressPinFlow.value)

        // Clear duress PIN
        viewModel.setDuressPin(null)
        advanceUntilIdle()
        assertNull(appPreferences.duressPinFlow.value)
    }

    @Test
    fun testEmergencyCryptographicWipe() = runTest(testDispatcher) {
        val testConn = MockPoolableConnection("conn-emergency")
        connectionPoolManager.registerConnection(testConn)
        sessionManager.setLoggedIn(true)

        var completionInvoked = false

        viewModel.emergencyCryptographicWipe {
            completionInvoked = true
        }
        advanceUntilIdle()

        assertTrue(testConn.isDisconnected)
        assertTrue(wipeDataManager.shredCalled)
        assertFalse(sessionManager.isLoggedIn.value)
        assertTrue(appPreferences.registrationCleared)
        assertTrue(completionInvoked)
    }
}
