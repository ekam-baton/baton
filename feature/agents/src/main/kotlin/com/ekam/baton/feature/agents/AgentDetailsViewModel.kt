package com.ekam.baton.feature.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.util.AuditCryptoUtils
import com.ekam.baton.core.network.mcp.McpMessageSender
import com.ekam.baton.core.network.mcp.McpTool
import com.ekam.baton.core.network.mcp.ToolAuthorizationManager
import com.ekam.baton.core.network.mcp.ToolAuthorizationRequest
import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.system.measureTimeMillis

class AgentDetailsViewModel(
    private val agentRepository: AgentRepository,
    private val mcpMessageSender: McpMessageSender,
    private val toolAuthManager: ToolAuthorizationManager,
    private val auditDao: AuditDao,
    private val tunnelValidator: TunnelEndpointValidator
) : ViewModel() {

    private val jsonPretty = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    private val _agentId = MutableStateFlow<String?>(null)

    val agent: StateFlow<Agent?> = kotlinx.coroutines.flow.combine(
        _agentId,
        agentRepository.getAllAgents()
    ) { id, list ->
        if (id == null) null else list.find { it.id == id }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    private val _tools = MutableStateFlow<List<McpTool>>(emptyList())
    val tools: StateFlow<List<McpTool>> = _tools.asStateFlow()

    private val _isLoadingTools = MutableStateFlow(false)
    val isLoadingTools: StateFlow<Boolean> = _isLoadingTools.asStateFlow()

    private val _toolsError = MutableStateFlow<String?>(null)
    val toolsError: StateFlow<String?> = _toolsError.asStateFlow()

    private val _selectedTool = MutableStateFlow<McpTool?>(null)
    val selectedTool: StateFlow<McpTool?> = _selectedTool.asStateFlow()

    private val _testParamsInput = MutableStateFlow("")
    val testParamsInput: StateFlow<String> = _testParamsInput.asStateFlow()

    private val _isExecutingTool = MutableStateFlow(false)
    val isExecutingTool: StateFlow<Boolean> = _isExecutingTool.asStateFlow()

    private val _testExecutionOutput = MutableStateFlow<String?>(null)
    val testExecutionOutput: StateFlow<String?> = _testExecutionOutput.asStateFlow()

    private val _testExecutionError = MutableStateFlow<String?>(null)
    val testExecutionError: StateFlow<String?> = _testExecutionError.asStateFlow()

    private val _pingLatencyMs = MutableStateFlow<Long?>(null)
    val pingLatencyMs: StateFlow<Long?> = _pingLatencyMs.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    val toolAuthRequests = toolAuthManager.authorizationRequests

    fun isDestructiveTool(toolName: String): Boolean {
        return toolAuthManager.isDestructive(toolName)
    }

    fun resolveToolAuth(request: ToolAuthorizationRequest, isApproved: Boolean) {
        request.onResult(isApproved)
    }

    fun loadAgent(id: String) {
        if (_agentId.value == id) return
        _agentId.value = id
        viewModelScope.launch {
            val current = agentRepository.getAllAgents().first().find { it.id == id }
            if (current != null) {
                refreshToolsForAgent(current)
                pingAgentEndpoint(current)
            }
        }
    }

    fun refreshTools() {
        val current = agent.value ?: return
        refreshToolsForAgent(current)
    }

    private fun refreshToolsForAgent(current: Agent) {
        viewModelScope.launch {
            if (current.mcpEndpointUrl.isBlank()) {
                _tools.value = emptyList()
                _toolsError.value = "Air-gapped agent with no remote MCP endpoint configured"
                return@launch
            }
            _isLoadingTools.value = true
            _toolsError.value = null
            try {
                val fetched = mcpMessageSender.getAvailableTools(current.id, current.mcpEndpointUrl, null)
                _tools.value = fetched
                if (fetched.isEmpty()) {
                    _toolsError.value = "Agent advertised 0 capabilities over MCP endpoint"
                }
            } catch (e: Exception) {
                _toolsError.value = "Capability discovery failed: ${e.message}"
            } finally {
                _isLoadingTools.value = false
            }
        }
    }

    fun pingEndpoint() {
        val current = agent.value ?: return
        pingAgentEndpoint(current)
    }

    private fun pingAgentEndpoint(current: Agent) {
        viewModelScope.launch {
            if (current.mcpEndpointUrl.isBlank()) {
                _pingLatencyMs.value = null
                return@launch
            }
            _isPinging.value = true
            try {
                var probeResult: com.ekam.baton.core.network.tunnel.TunnelValidationResult? = null
                val elapsed = measureTimeMillis {
                    probeResult = tunnelValidator.validateEndpoint(current.mcpEndpointUrl)
                }
                if (probeResult?.status == com.ekam.baton.core.network.tunnel.Status.VALID) {
                    _pingLatencyMs.value = elapsed
                } else {
                    _pingLatencyMs.value = -1L
                }
            } catch (e: Exception) {
                _pingLatencyMs.value = -1L
            } finally {
                _isPinging.value = false
            }
        }
    }

    fun selectTool(tool: McpTool) {
        _selectedTool.value = tool
        _testExecutionOutput.value = null
        _testExecutionError.value = null

        val props = tool.inputSchema["properties"] as? JsonObject
        val skeleton = buildJsonObject {
            props?.forEach { (key, elem) ->
                val propObj = elem as? JsonObject
                val type = propObj?.get("type")?.jsonPrimitive?.content ?: "string"
                when (type) {
                    "string" -> put(key, "sample_value")
                    "integer", "number" -> put(key, 0)
                    "boolean" -> put(key, true)
                    "array" -> put(key, buildJsonArray {})
                    "object" -> put(key, buildJsonObject {})
                    else -> put(key, "")
                }
            }
        }
        _testParamsInput.value = jsonPretty.encodeToString(JsonObject.serializer(), skeleton)
    }

    fun updateTestParams(input: String) {
        _testParamsInput.value = input
    }

    fun clearSelectedTool() {
        _selectedTool.value = null
        _testExecutionOutput.value = null
        _testExecutionError.value = null
    }

    fun executeSelectedTool() {
        val current = agent.value ?: return
        val tool = _selectedTool.value ?: return

        viewModelScope.launch {
            _isExecutingTool.value = true
            _testExecutionOutput.value = null
            _testExecutionError.value = null

            val parsedArgs: JsonObject = try {
                if (_testParamsInput.value.isBlank()) {
                    JsonObject(emptyMap())
                } else {
                    jsonPretty.decodeFromString(JsonObject.serializer(), _testParamsInput.value)
                }
            } catch (e: Exception) {
                _testExecutionError.value = "JSON Schema Error: ${e.message}"
                _isExecutingTool.value = false
                return@launch
            }

            try {
                val outputBuffer = StringBuilder()
                mcpMessageSender.executeTool(
                    agentId = current.id,
                    endpointUrl = current.mcpEndpointUrl,
                    authHeader = null,
                    toolName = tool.name,
                    arguments = parsedArgs,
                    relayUrl = current.relayUrl,
                    relayToken = current.relayToken
                ).collect { chunk ->
                    outputBuffer.append(chunk)
                    _testExecutionOutput.value = outputBuffer.toString()
                }

                val finalOutput = outputBuffer.toString()
                val prevLog = auditDao.getLastAuditLog()
                val prevHash = prevLog?.hash ?: "0".repeat(64)
                val payload = "{\"tool\": \"${tool.name}\", \"agentId\": \"${current.id}\", \"chars\": ${finalOutput.length}}"
                val hash = AuditCryptoUtils.generateHash(payload, prevHash)

                auditDao.insertAuditLog(
                    AuditLogEntity(
                        entityName = "McpDiagnosticExecution",
                        entityId = "${current.id}:${tool.name}",
                        action = "EXECUTE",
                        deviceId = "local_device",
                        payloadJson = payload,
                        previousHash = prevHash,
                        hash = hash
                    )
                )
            } catch (e: Exception) {
                _testExecutionError.value = "Execution failed: ${e.message}"
            } finally {
                _isExecutingTool.value = false
            }
        }
    }

    fun deleteAgent(onDeleted: () -> Unit) {
        val current = agent.value ?: return
        viewModelScope.launch {
            try {
                agentRepository.deleteAgent(current.id)
                onDeleted()
            } catch (e: Exception) {
                // handle error
            }
        }
    }
}
