package com.ekam.baton.feature.agents

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.network.mcp.McpConnectionManager
import com.ekam.baton.core.network.mcp.McpMessageSender
import com.ekam.baton.core.network.mcp.McpTool
import com.ekam.baton.core.network.mcp.ToolAuthorizationManager
import com.ekam.baton.core.network.tunnel.Status
import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator
import com.ekam.baton.core.network.tunnel.TunnelValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentDetailsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleAgent = AgentEntity(
        id = "agent-alpha",
        name = "Alpha Swarm Node",
        description = "Core cluster diagnostic agent",
        mcpEndpointUrl = "https://alpha.trycloudflare.com",
        authType = "NONE",
        authConfig = "{}",
        colorAccent = "#306EE8",
        securityMode = "sovereign",
        securityConfig = "{}",
        relayUrl = "https://relay.baton.network",
        relayToken = "relay-token-xyz"
    )

    private class FakeAgentDao(initialAgents: List<AgentEntity>) : AgentDao {
        private val list = initialAgents.toMutableList()
        private val flow = MutableStateFlow<List<AgentEntity>>(list.toList())

        override fun getAllAgents(): Flow<List<AgentEntity>> = flow

        override suspend fun getAgentById(id: String): AgentEntity? = list.find { it.id == id }

        override suspend fun upsertAgent(agent: AgentEntity) {
            list.removeAll { it.id == agent.id }
            list.add(agent)
            flow.value = list.toList()
        }

        override suspend fun deleteAgent(id: String) {
            list.removeAll { it.id == id }
            flow.value = list.toList()
        }

        override suspend fun updateLastUsed(id: String, timestamp: Long) {}
        override suspend fun updateBlockedStatus(id: String, isBlocked: Boolean) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx != -1) {
                list[idx] = list[idx].copy(isBlocked = isBlocked)
                flow.value = list.toList()
            }
        }
        override fun isAgentBlocked(id: String): Flow<Boolean?> =
            flow.map { agents -> agents.find { it.id == id }?.isBlocked }
    }

    private class FakeAuditDao : AuditDao {
        val insertedLogs = mutableListOf<AuditLogEntity>()

        override suspend fun insertAuditLog(log: AuditLogEntity) {
            insertedLogs.add(log)
        }

        override fun getAllAuditLogs(): Flow<List<AuditLogEntity>> = flowOf(insertedLogs.toList())

        override suspend fun getLastAuditLog(): AuditLogEntity? = insertedLogs.lastOrNull()

        override suspend fun getAllAuditLogsSync(): List<AuditLogEntity> = insertedLogs.toList()

        override suspend fun pruneOldLogs(keepLastN: Int) {
            if (insertedLogs.size > keepLastN) {
                val excess = insertedLogs.size - keepLastN
                repeat(excess) { insertedLogs.removeAt(0) }
            }
        }
    }

    private class FakeMcpTransport : com.ekam.baton.core.network.mcp.McpTransport {
        override suspend fun initialize(endpointUrl: String, authHeader: String?, agentId: String?): Result<JsonObject> = Result.success(JsonObject(emptyMap()))
        override suspend fun listTools(endpointUrl: String, authHeader: String?, agentId: String?): Result<List<McpTool>> = Result.success(emptyList())
        override fun callTool(endpointUrl: String, authHeader: String?, toolName: String, arguments: JsonObject, agentId: String?): Flow<String> = flowOf()
        override suspend fun ping(endpointUrl: String): Boolean = true
        override suspend fun uploadFile(endpointUrl: String, authHeader: String?, uri: String, context: android.content.Context, agentId: String?): Result<String> = Result.success("file-id")
    }

    private class FakeMcpMessageSender(
        val toolsToReturn: List<McpTool> = emptyList(),
        val toolOutputToEmit: String = "Test execution success"
    ) : McpMessageSender(
        connectionManager = McpConnectionManager(FakeMcpTransport(), FakeMcpTransport()),
        authorizationManager = ToolAuthorizationManager(),
        context = null,
        httpClient = OkHttpClient()
    ) {
        override suspend fun getAvailableTools(agentId: String, endpointUrl: String, authHeader: String?): List<McpTool> {
            return toolsToReturn
        }

        override suspend fun executeTool(
            agentId: String,
            endpointUrl: String,
            authHeader: String?,
            toolName: String,
            arguments: JsonObject,
            relayUrl: String?,
            relayToken: String?
        ): Flow<String> = flow {
            emit(toolOutputToEmit)
        }
    }

    private class FakeTunnelValidator(val statusToReturn: Status = Status.VALID) : TunnelEndpointValidator(
        json = Json { ignoreUnknownKeys = true },
        client = OkHttpClient()
    ) {
        override suspend fun validateEndpoint(urlString: String): TunnelValidationResult {
            return TunnelValidationResult(statusToReturn, "test-host", emptyList(), null)
        }
    }

    private lateinit var fakeDao: FakeAgentDao
    private lateinit var fakeAuditDao: FakeAuditDao
    private lateinit var fakeMcpSender: FakeMcpMessageSender
    private lateinit var fakeTunnelValidator: FakeTunnelValidator
    private lateinit var toolAuthManager: ToolAuthorizationManager
    private lateinit var repository: AgentRepository
    private lateinit var viewModel: AgentDetailsViewModel

    private val sampleTools = listOf(
        McpTool(
            name = "query_metrics",
            description = "Query telemetry metrics",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("metric_name", buildJsonObject {
                        put("type", "string")
                        put("description", "Name of metric to query")
                    })
                    put("time_range", buildJsonObject {
                        put("type", "integer")
                        put("description", "Time range in seconds")
                    })
                })
            }
        ),
        McpTool(
            name = "execute_reboot",
            description = "Destructive node restart",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("force", buildJsonObject {
                        put("type", "boolean")
                    })
                })
            }
        )
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeAgentDao(listOf(sampleAgent))
        fakeAuditDao = FakeAuditDao()
        fakeMcpSender = FakeMcpMessageSender(toolsToReturn = sampleTools, toolOutputToEmit = "Metrics: CPU 42%")
        fakeTunnelValidator = FakeTunnelValidator(Status.VALID)
        toolAuthManager = ToolAuthorizationManager()
        repository = AgentRepository(fakeDao)

        viewModel = AgentDetailsViewModel(
            agentRepository = repository,
            mcpMessageSender = fakeMcpSender,
            toolAuthManager = toolAuthManager,
            auditDao = fakeAuditDao,
            tunnelValidator = fakeTunnelValidator
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadAgent loads target agent and populates tools and latency`() = runTest(testDispatcher) {
        viewModel.loadAgent("agent-alpha")
        advanceUntilIdle()

        val loadedAgent = viewModel.agent.value
        assertNotNull(loadedAgent)
        assertEquals("Alpha Swarm Node", loadedAgent?.name)
        assertEquals("https://alpha.trycloudflare.com", loadedAgent?.mcpEndpointUrl)

        val discoveredTools = viewModel.tools.value
        assertEquals(2, discoveredTools.size)
        assertEquals("query_metrics", discoveredTools[0].name)
        assertEquals("execute_reboot", discoveredTools[1].name)
        assertFalse(viewModel.isLoadingTools.value)
        assertNull(viewModel.toolsError.value)

        // Latency ping measured
        assertNotNull(viewModel.pingLatencyMs.value)
        assertTrue(viewModel.pingLatencyMs.value!! >= 0)
    }

    @Test
    fun `isDestructiveTool correctly identifies destructive actions`() {
        assertFalse(viewModel.isDestructiveTool("query_metrics"))
        assertFalse(viewModel.isDestructiveTool("read_file"))
        assertFalse(viewModel.isDestructiveTool("get_status"))

        assertTrue(viewModel.isDestructiveTool("execute_reboot"))
        assertTrue(viewModel.isDestructiveTool("delete_node"))
        assertTrue(viewModel.isDestructiveTool("write_config"))
        assertTrue(viewModel.isDestructiveTool("run_script"))
    }

    @Test
    fun `selectTool generates JSON parameter skeleton from input schema`() = runTest(testDispatcher) {
        val tool = sampleTools[0]
        viewModel.selectTool(tool)

        assertEquals("query_metrics", viewModel.selectedTool.value?.name)
        val skeleton = viewModel.testParamsInput.value
        assertTrue(skeleton.contains("metric_name"))
        assertTrue(skeleton.contains("time_range"))

        viewModel.updateTestParams("{\"metric_name\": \"cpu_load\", \"time_range\": 300}")
        assertEquals("{\"metric_name\": \"cpu_load\", \"time_range\": 300}", viewModel.testParamsInput.value)

        viewModel.clearSelectedTool()
        assertNull(viewModel.selectedTool.value)
    }

    @Test
    fun `executeSelectedTool fails when input is malformed JSON`() = runTest(testDispatcher) {
        viewModel.loadAgent("agent-alpha")
        advanceUntilIdle()

        viewModel.selectTool(sampleTools[0])
        viewModel.updateTestParams("{invalid_json: true")

        viewModel.executeSelectedTool()
        advanceUntilIdle()

        assertNotNull(viewModel.testExecutionError.value)
        assertTrue(viewModel.testExecutionError.value!!.contains("JSON Schema Error"))
        assertNull(viewModel.testExecutionOutput.value)
    }

    @Test
    fun `executeSelectedTool runs tool, captures output, and records audit log`() = runTest(testDispatcher) {
        viewModel.loadAgent("agent-alpha")
        advanceUntilIdle()

        viewModel.selectTool(sampleTools[0])
        viewModel.updateTestParams("{\"metric_name\": \"cpu_load\"}")

        viewModel.executeSelectedTool()
        advanceUntilIdle()

        assertEquals("Metrics: CPU 42%", viewModel.testExecutionOutput.value)
        assertNull(viewModel.testExecutionError.value)
        assertFalse(viewModel.isExecutingTool.value)

        // Verifies cryptographic audit log insertion
        assertEquals(1, fakeAuditDao.insertedLogs.size)
        val log = fakeAuditDao.insertedLogs[0]
        assertEquals("McpDiagnosticExecution", log.entityName)
        assertEquals("agent-alpha:query_metrics", log.entityId)
        assertEquals("EXECUTE", log.action)
        assertTrue(log.payloadJson.contains("query_metrics"))
    }

    @Test
    fun `deleteAgent removes agent from repository and calls callback`() = runTest(testDispatcher) {
        viewModel.loadAgent("agent-alpha")
        advanceUntilIdle()

        var onDeletedCalled = false
        viewModel.deleteAgent {
            onDeletedCalled = true
        }
        advanceUntilIdle()

        assertTrue(onDeletedCalled)
        val remaining = repository.getAllAgents().first()
        assertTrue(remaining.isEmpty())
    }
}
