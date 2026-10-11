package com.ekam.baton.feature.agents.tunnel

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.network.security.LocalNetworkPolicyProvider
import com.ekam.baton.core.network.tunnel.ConnectionPoolManager
import com.ekam.baton.core.network.tunnel.PoolableConnection
import com.ekam.baton.core.network.tunnel.Status
import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator
import com.ekam.baton.core.network.tunnel.TunnelValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TunnelSetupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeAgents = listOf(
        AgentEntity(
            id = "agent-1",
            name = "Cloudflare Worker Agent",
            description = "Remote Agent",
            mcpEndpointUrl = "https://agent1.trycloudflare.com",
            authType = "NONE",
            authConfig = "{}",
            colorAccent = "#306EE8",
            securityMode = "STANDARD",
            securityConfig = "{}"
        ),
        AgentEntity(
            id = "agent-2",
            name = "Ngrok Local Agent",
            description = "Local Testing Agent",
            mcpEndpointUrl = "https://agent2.ngrok-free.app",
            authType = "API_KEY",
            authConfig = "{}",
            colorAccent = "#00D2FF",
            securityMode = "STANDARD",
            securityConfig = "{}"
        )
    )

    private class FakeAgentDao(val list: List<AgentEntity>) : AgentDao {
        override fun getAllAgents(): Flow<List<AgentEntity>> = flowOf(list)
        override suspend fun getAgentById(id: String): AgentEntity? = list.find { it.id == id }
        override suspend fun upsertAgent(agent: AgentEntity) {}
        override suspend fun deleteAgent(id: String) {}
        override suspend fun updateLastUsed(id: String, timestamp: Long) {}
    }

    private class FakeLocalNetworkPolicyProvider : LocalNetworkPolicyProvider {
        val allowedHosts = mutableSetOf<String>()
        override fun allowDuringPairing(hostname: String) { allowedHosts.add(hostname) }
        override fun removePairingHost(hostname: String) { allowedHosts.remove(hostname) }
        override suspend fun isLocalNetworkAllowed(hostname: String): Boolean = allowedHosts.contains(hostname)
    }

    private class FakeTunnelEndpointValidator : TunnelEndpointValidator(Json {}, OkHttpClient()) {
        override suspend fun validateEndpoint(urlString: String): TunnelValidationResult {
            return if (urlString.contains("unreachable")) {
                TunnelValidationResult(Status.UNREACHABLE, null, null, "Connection refused")
            } else {
                TunnelValidationResult(Status.VALID, "agent-mcp-server", listOf("read", "write"), null)
            }
        }
    }

    private lateinit var agentDao: FakeAgentDao
    private lateinit var agentRepository: AgentRepository
    private lateinit var policyProvider: FakeLocalNetworkPolicyProvider
    private lateinit var endpointValidator: FakeTunnelEndpointValidator
    private lateinit var connectionPoolManager: ConnectionPoolManager
    private lateinit var viewModel: TunnelSetupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        agentDao = FakeAgentDao(fakeAgents)
        agentRepository = AgentRepository(agentDao)
        policyProvider = FakeLocalNetworkPolicyProvider()
        endpointValidator = FakeTunnelEndpointValidator()
        connectionPoolManager = ConnectionPoolManager()
        viewModel = TunnelSetupViewModel(
            endpointValidator = endpointValidator,
            localNetworkPolicyProvider = policyProvider,
            agentRepository = agentRepository,
            connectionPoolManager = connectionPoolManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test agents flow emits registered agents`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        val agents = viewModel.agents.first()
        assertEquals(2, agents.size)
        assertEquals("Cloudflare Worker Agent", agents[0].name)
    }

    @Test
    fun `test active connection count reflects pool manager`() = runTest {
        assertEquals(0, viewModel.getActiveConnectionCount())

        val dummyConnection = object : PoolableConnection {
            override val id: String = "conn-1"
            override suspend fun disconnect() {}
            override val lastAccessedAt: Long = System.currentTimeMillis()
        }
        connectionPoolManager.registerConnection(dummyConnection)
        assertEquals(1, viewModel.getActiveConnectionCount())
    }

    @Test
    fun `test validateUrl updates validationResult and unsets validating flag`() = runTest {
        assertNull(viewModel.validationResult.value)
        assertFalse(viewModel.isValidating.value)

        viewModel.validateUrl("https://agent1.trycloudflare.com")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isValidating.value)
        val result = viewModel.validationResult.value
        assertNotNull(result)
        assertEquals(Status.VALID, result!!.status)
        assertEquals("agent-mcp-server", result.serverName)
    }

    @Test
    fun `test probeSingleEndpoint updates tunnelHealthMap with metrics`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.tunnelHealthMap.value.isEmpty())

        viewModel.probeSingleEndpoint("agent-1", "https://agent1.trycloudflare.com")
        testDispatcher.scheduler.advanceUntilIdle()

        val healthMap = viewModel.tunnelHealthMap.value
        assertEquals(1, healthMap.size)
        val health = healthMap["agent-1"]
        assertNotNull(health)
        assertEquals(Status.VALID, health!!.status)
        assertEquals("agent-mcp-server", health.serverName)
        assertTrue(health.latencyMs >= 0)
    }

    @Test
    fun `test probeAllTunnelEndpoints probes all registered agents`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.agents.value.size)

        viewModel.probeAllTunnelEndpoints()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isProbingAll.value)
        val healthMap = viewModel.tunnelHealthMap.value
        assertEquals(2, healthMap.size)
        assertTrue(healthMap.containsKey("agent-1"))
        assertTrue(healthMap.containsKey("agent-2"))
        assertEquals(Status.VALID, healthMap["agent-1"]!!.status)
        assertEquals(Status.VALID, healthMap["agent-2"]!!.status)
    }
}
