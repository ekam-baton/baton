package com.ekam.baton.feature.agents.creator

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.network.mcp.McpConnectionManager
import com.ekam.baton.core.network.mcp.McpTool
import com.ekam.baton.core.network.mcp.McpTransport
import com.ekam.baton.core.network.mdns.DiscoveredAgent
import com.ekam.baton.core.network.mdns.MdnsDiscoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BotCreatorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAgentDao : AgentDao {
        val agents = mutableListOf<AgentEntity>()
        private val flow = MutableStateFlow<List<AgentEntity>>(emptyList())

        override fun getAllAgents(): Flow<List<AgentEntity>> = flow

        override suspend fun getAgentById(id: String): AgentEntity? = agents.find { it.id == id }

        override suspend fun upsertAgent(agent: AgentEntity) {
            agents.removeAll { it.id == agent.id }
            agents.add(agent)
            flow.value = agents.toList()
        }

        override suspend fun deleteAgent(id: String) {
            agents.removeAll { it.id == id }
            flow.value = agents.toList()
        }

        override suspend fun updateLastUsed(id: String, timestamp: Long) {}
        override suspend fun updateBlockedStatus(id: String, isBlocked: Boolean) {
            val idx = agents.indexOfFirst { it.id == id }
            if (idx != -1) {
                agents[idx] = agents[idx].copy(isBlocked = isBlocked)
                flow.value = agents.toList()
            }
        }
        override fun isAgentBlocked(id: String): Flow<Boolean?> =
            flow.map { list -> list.find { it.id == id }?.isBlocked }
    }

    private class FakeMdnsDiscoveryManager : MdnsDiscoveryManager(null) {
        private val _agents = MutableStateFlow<List<DiscoveredAgent>>(emptyList())
        override val discoveredAgents = _agents.asStateFlow()
        override fun startDiscovery() {}
        override fun stopDiscovery() {}
    }

    private class FakeMcpTransport(
        var shouldFail: Boolean = false,
        var toolsToReturn: List<McpTool> = emptyList()
    ) : McpTransport {
        override suspend fun initialize(endpointUrl: String, authHeader: String?, agentId: String?): Result<JsonObject> {
            return if (shouldFail) {
                Result.failure(Exception("Hub connection refused"))
            } else {
                Result.success(JsonObject(emptyMap()))
            }
        }

        override suspend fun listTools(endpointUrl: String, authHeader: String?, agentId: String?): Result<List<McpTool>> {
            return if (shouldFail) {
                Result.failure(Exception("Failed to enumerate tools"))
            } else {
                Result.success(toolsToReturn)
            }
        }

        override fun callTool(endpointUrl: String, authHeader: String?, toolName: String, arguments: JsonObject, agentId: String?): Flow<String> = flowOf()
        override suspend fun ping(endpointUrl: String): Boolean = !shouldFail
        override suspend fun uploadFile(endpointUrl: String, authHeader: String?, uri: String, context: android.content.Context, agentId: String?): Result<String> = Result.success("ok")
    }

    private lateinit var agentDao: FakeAgentDao
    private lateinit var agentRepository: AgentRepository
    private lateinit var mdnsDiscoveryManager: FakeMdnsDiscoveryManager
    private lateinit var fakeTransport: FakeMcpTransport
    private lateinit var mcpConnectionManager: McpConnectionManager
    private lateinit var viewModel: BotCreatorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        agentDao = FakeAgentDao()
        agentRepository = AgentRepository(agentDao)
        mdnsDiscoveryManager = FakeMdnsDiscoveryManager()
        fakeTransport = FakeMcpTransport()
        mcpConnectionManager = McpConnectionManager(fakeTransport, fakeTransport)

        viewModel = BotCreatorViewModel(
            agentRepository = agentRepository,
            mdnsDiscoveryManager = mdnsDiscoveryManager,
            mcpConnectionManager = mcpConnectionManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDefaultStateInitialization() {
        val state = viewModel.uiState.value
        assertEquals(BotCreatorStep.PROFILE, state.currentStep)
        assertEquals(BrainSourceType.LOCAL_WIFI, state.brainSource)
        assertEquals("research_scout", state.selectedRoleId)
        assertFalse(state.isMinting)
    }

    @Test
    fun testRemoteTunnelBrainSourceSelection() {
        viewModel.setBrainSource(BrainSourceType.REMOTE_TUNNEL)
        assertEquals(BrainSourceType.REMOTE_TUNNEL, viewModel.uiState.value.brainSource)

        viewModel.setTunnelUrl("https://my-antigravity.trycloudflare.com/sse")
        assertEquals("https://my-antigravity.trycloudflare.com/sse", viewModel.uiState.value.tunnelUrl)
        assertEquals("https://my-antigravity.trycloudflare.com/sse", viewModel.uiState.value.enterpriseHubUrl)

        viewModel.setTunnelAuthToken("tunnel_sec_token")
        assertEquals("tunnel_sec_token", viewModel.uiState.value.tunnelAuthToken)
        assertEquals("tunnel_sec_token", viewModel.uiState.value.enterpriseAuthToken)
    }

    @Test
    fun testApplyTunnelPreset() {
        viewModel.applyTunnelPreset(
            tunnelName = "Cloudflare Quick Tunnel",
            url = "https://agent-bridge.trycloudflare.com/sse"
        )

        val state = viewModel.uiState.value
        assertEquals("https://agent-bridge.trycloudflare.com/sse", state.tunnelUrl)
    }

    @Test
    fun testParseAndApplyQrPayload_RemoteTunnelJson() = runTest {
        val jsonPayload = """
            {
                "endpoint": "https://antigravity-hub.trycloudflare.com/sse",
                "token": "btn_tunnel_abc123",
                "model": "antigravity-pro",
                "name": "Cloudflare Antigravity Bridge"
            }
        """.trimIndent()

        fakeTransport.toolsToReturn = listOf(
            McpTool(name = "read_file", inputSchema = JsonObject(emptyMap())),
            McpTool(name = "run_command", inputSchema = JsonObject(emptyMap()))
        )

        viewModel.parseAndApplyQrPayload(jsonPayload)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BrainSourceType.REMOTE_TUNNEL, state.brainSource)
        assertEquals("https://antigravity-hub.trycloudflare.com/sse", state.tunnelUrl)
        assertEquals("btn_tunnel_abc123", state.tunnelAuthToken)
        assertEquals("antigravity-pro", state.modelIdentifier)
        assertEquals("Cloudflare Antigravity Bridge", state.agentName)
        assertEquals(true, state.tunnelConnectionSuccess)
        assertEquals(2, state.discoveredTunnelTools.size)
    }

    @Test
    fun testParseAndApplyQrPayload_LocalWifiJson() = runTest {
        val jsonPayload = """
            {
                "endpoint": "http://192.168.1.150:11434/v1",
                "model": "llama3.2:latest",
                "name": "LAN Ollama Node"
            }
        """.trimIndent()

        viewModel.parseAndApplyQrPayload(jsonPayload)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BrainSourceType.LOCAL_WIFI, state.brainSource)
        assertEquals("http://192.168.1.150:11434/v1", state.endpointUrl)
        assertEquals("LAN Ollama Node", state.agentName)
    }

    @Test
    fun testParseAndApplyQrPayload_Uri() = runTest {
        val uriPayload = "baton://connect?url=https%3A%2F%2Fagent-node.ngrok-free.app%2Fsse&token=ngrok_token_999&model=claude-3-7-sonnet"

        fakeTransport.toolsToReturn = listOf(
            McpTool(name = "bash", inputSchema = JsonObject(emptyMap()))
        )

        viewModel.parseAndApplyQrPayload(uriPayload)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BrainSourceType.REMOTE_TUNNEL, state.brainSource)
        assertEquals("https://agent-node.ngrok-free.app/sse", state.tunnelUrl)
        assertEquals("ngrok_token_999", state.tunnelAuthToken)
        assertEquals("claude-3-7-sonnet", state.modelIdentifier)
        assertEquals(true, state.tunnelConnectionSuccess)
    }

    @Test
    fun testTestTunnelConnection_Success() = runTest {
        fakeTransport.toolsToReturn = listOf(
            McpTool(name = "view_file", inputSchema = JsonObject(emptyMap())),
            McpTool(name = "run_command", inputSchema = JsonObject(emptyMap())),
            McpTool(name = "search_code", inputSchema = JsonObject(emptyMap()))
        )

        viewModel.setBrainSource(BrainSourceType.REMOTE_TUNNEL)
        viewModel.setTunnelUrl("https://my-antigravity.trycloudflare.com/sse")
        viewModel.setTunnelAuthToken("tunnel_token_xyz")
        viewModel.testTunnelConnection()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isTestingTunnelConnection)
        assertEquals(true, state.tunnelConnectionSuccess)
        assertNull(state.tunnelConnectionError)
        assertEquals(3, state.discoveredTunnelTools.size)
        assertEquals("view_file", state.discoveredTunnelTools[0].name)
    }

    @Test
    fun testTestTunnelConnection_Failure() = runTest {
        fakeTransport.shouldFail = true

        viewModel.setBrainSource(BrainSourceType.REMOTE_TUNNEL)
        viewModel.setTunnelUrl("https://unreachable-tunnel.trycloudflare.com/sse")
        viewModel.testTunnelConnection()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isTestingTunnelConnection)
        assertEquals(false, state.tunnelConnectionSuccess)
        assertNotNull(state.tunnelConnectionError)
    }

    @Test
    fun testMintSovereignAgent_RemoteTunnel() = runTest {
        viewModel.setAgentName("Cloudflare Antigravity Agent")
        viewModel.setBrainSource(BrainSourceType.REMOTE_TUNNEL)
        viewModel.setTunnelUrl("https://antigravity-edge.trycloudflare.com/sse")
        viewModel.setTunnelAuthToken("bearer_tunnel_key_888")
        viewModel.setModelIdentifier("gemini-1.5-pro")

        fakeTransport.toolsToReturn = listOf(
            McpTool(name = "execute_code", inputSchema = JsonObject(emptyMap())),
            McpTool(name = "terminal_run", inputSchema = JsonObject(emptyMap()))
        )
        viewModel.testTunnelConnection()
        advanceUntilIdle()

        // Mint agent
        viewModel.mintSovereignAgent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BotCreatorStep.SUCCESS, state.currentStep)
        assertNotNull(state.mintedAgent)

        val agent = state.mintedAgent!!
        assertEquals("Cloudflare Antigravity Agent", agent.name)
        assertEquals("remote_tunnel", agent.providerType)
        assertEquals("https://antigravity-edge.trycloudflare.com/sse", agent.mcpEndpointUrl)
        assertEquals("bearer", agent.authType)
        assertEquals("bearer_tunnel_key_888", agent.authConfig)
        assertTrue(agent.isAuthenticated)

        // Check persisted securityConfig JSON
        val secConfig = JSONObject(agent.securityConfig)
        assertTrue(secConfig.optBoolean("is_remote_tunnel"))
        assertEquals(2, secConfig.optInt("discovered_tools_count"))
        val capabilities = secConfig.getJSONArray("capabilities")
        assertEquals(2, capabilities.length())
        assertEquals("execute_code", capabilities.getString(0))
        assertEquals("terminal_run", capabilities.getString(1))

        // Check Room persistence
        val saved = agentDao.getAgentById(agent.id)
        assertNotNull(saved)
        assertEquals("Cloudflare Antigravity Agent", saved!!.name)
        assertEquals("remote_tunnel", saved.providerType)
    }

    @Test
    fun testMintSovereignAgent_LocalWifi() = runTest {
        viewModel.setAgentName("Local LAN Ollama")
        viewModel.setBrainSource(BrainSourceType.LOCAL_WIFI)
        viewModel.setEndpointUrl("http://192.168.1.100:11434/v1")
        viewModel.setModelIdentifier("llama3.2:3b")

        viewModel.mintSovereignAgent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BotCreatorStep.SUCCESS, state.currentStep)
        val agent = state.mintedAgent!!
        assertEquals("Local LAN Ollama", agent.name)
        assertEquals("local_mcp", agent.providerType)
        assertEquals("http://192.168.1.100:11434/v1", agent.mcpEndpointUrl)
        assertEquals("none", agent.authType)

        val secConfig = JSONObject(agent.securityConfig)
        assertTrue(secConfig.optBoolean("is_local_wifi"))
        assertEquals("LOCAL_WIFI", secConfig.optString("connection_mode"))
    }

    @Test
    fun testMintSovereignAgent_BatonBot() = runTest {
        viewModel.setAgentName("Sovereign On-Device Core")
        viewModel.setBrainSource(BrainSourceType.BATON_BOT)
        viewModel.setModelIdentifier("llama-3.2-1b-quantized")

        viewModel.mintSovereignAgent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BotCreatorStep.SUCCESS, state.currentStep)
        val agent = state.mintedAgent!!
        assertEquals("Sovereign On-Device Core", agent.name)
        assertEquals("sovereign_on_device", agent.providerType)
        assertEquals("local://baton-sovereign-core", agent.mcpEndpointUrl)
        assertEquals("hardware_keystore", agent.authType)

        val secConfig = JSONObject(agent.securityConfig)
        assertTrue(secConfig.optBoolean("is_sovereign_on_device"))
        assertEquals("air_gapped", secConfig.optString("privacy_mode"))
        assertEquals("android_keystore_backed", secConfig.optString("hardware_attestation"))
    }

    @Test
    fun testSelectDiscoveredNode_LocalWifi() {
        val node = DiscoveredAgent(
            name = "Studio Mac M3 Max",
            ipAddress = "192.168.1.42",
            port = 11434,
            url = "http://192.168.1.42:11434/v1"
        )
        viewModel.selectDiscoveredNode(node)
        assertEquals("http://192.168.1.42:11434/v1", viewModel.uiState.value.endpointUrl)
    }

    @Test
    fun testSuggestRandomProfile() {
        assertEquals("", viewModel.uiState.value.agentName)
        viewModel.suggestRandomProfile()
        val state = viewModel.uiState.value
        assertTrue(state.agentName.isNotBlank())
        assertTrue(state.description.isNotBlank())
        assertTrue(state.colorAccent.startsWith("#"))
    }
}
