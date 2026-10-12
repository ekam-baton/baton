package com.ekam.baton.feature.agents.creator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.util.IdentityBridge
import com.ekam.baton.core.network.mdns.DiscoveredAgent
import com.ekam.baton.core.network.mdns.MdnsDiscoveryManager
import com.ekam.baton.core.network.mcp.McpConnectionManager
import com.ekam.baton.core.network.mcp.McpTool
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

enum class BotCreatorStep {
    PROFILE,
    ROLE_PRESET,
    BRAIN_CONNECTIVITY,
    SUCCESS
}

enum class BrainSourceType {
    LOCAL_WIFI,
    REMOTE_TUNNEL,
    BATON_BOT;

    companion object {
        val ENTERPRISE_REMOTE_MCP: BrainSourceType get() = REMOTE_TUNNEL
        val DIRECT_ENDPOINT: BrainSourceType get() = LOCAL_WIFI
    }
}

data class RolePreset(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val defaultCapabilities: List<String>
)

data class BotCreatorUiState(
    val currentStep: BotCreatorStep = BotCreatorStep.PROFILE,
    // Step 1: Profile
    val agentName: String = "",
    val description: String = "",
    val colorAccent: String = "#3D8EFF",
    // Step 2: Role
    val selectedRoleId: String = "research_scout",
    val customRoleName: String = "",
    val selectedCapabilities: Set<String> = setOf("Web Search", "File Analysis"),
    // Step 3: Brain (3 Mastered Methods Only)
    val brainSource: BrainSourceType = BrainSourceType.LOCAL_WIFI,
    // 1. Local Wi-Fi
    val endpointUrl: String = "http://localhost:11434/v1",
    val isWifiDiscovering: Boolean = false,
    val discoveredNodes: List<DiscoveredAgent> = emptyList(),
    // 2. Cloudflare / Ngrok Remote Tunnel
    val tunnelUrl: String = "",
    val tunnelAuthToken: String = "",
    val isTestingTunnelConnection: Boolean = false,
    val discoveredTunnelTools: List<McpTool> = emptyList(),
    val tunnelConnectionSuccess: Boolean? = null,
    val tunnelConnectionError: String? = null,
    // Model identifier
    val modelIdentifier: String = "llama3.2:latest",
    // Step 4: Minted Identity Result
    val isMinting: Boolean = false,
    val mintedAgent: Agent? = null,
    val numericAgentId: Long? = null,
    val botToken: String? = null
) {
    // Backward compatibility aliases
    val enterpriseHubUrl: String get() = tunnelUrl
    val enterpriseAuthToken: String get() = tunnelAuthToken
    val isTestingEnterpriseConnection: Boolean get() = isTestingTunnelConnection
    val discoveredEnterpriseTools: List<McpTool> get() = discoveredTunnelTools
    val connectionTestSuccess: Boolean? get() = tunnelConnectionSuccess
    val connectionTestError: String? get() = tunnelConnectionError
}

sealed interface BotCreatorUiEvent {
    data class ShowToast(val message: String) : BotCreatorUiEvent
    data class AgentCreated(val agent: Agent) : BotCreatorUiEvent
}

class BotCreatorViewModel(
    private val agentRepository: AgentRepository,
    private val mdnsDiscoveryManager: MdnsDiscoveryManager,
    private val mcpConnectionManager: McpConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BotCreatorUiState())
    val uiState: StateFlow<BotCreatorUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<BotCreatorUiEvent>()
    val uiEvents: SharedFlow<BotCreatorUiEvent> = _uiEvents.asSharedFlow()

    private var autoStopDiscoveryJob: Job? = null

    val rolePresets = listOf(
        RolePreset(
            id = "research_scout",
            title = "Research Scout",
            description = "Deep information synthesis, web retrieval, and contextual summaries",
            iconEmoji = "🔍",
            defaultCapabilities = listOf("Web Search", "Contextual Memory", "Report Generation")
        ),
        RolePreset(
            id = "coding_assistant",
            title = "Coding Assistant",
            description = "Code generation, debugging, refactoring, and local tool execution",
            iconEmoji = "💻",
            defaultCapabilities = listOf("Code Generation", "Syntax Verification", "Terminal Runner")
        ),
        RolePreset(
            id = "security_vanguard",
            title = "Security Vanguard",
            description = "Sovereign key attestation, air-gap inspection, and policy enforcement",
            iconEmoji = "🛡️",
            defaultCapabilities = listOf("Key Attestation", "Air-Gap Guard", "Audit Logging")
        ),
        RolePreset(
            id = "creative_scribe",
            title = "Creative Scribe",
            description = "Drafting prose, storytelling, documentation, and polished copy",
            iconEmoji = "✍️",
            defaultCapabilities = listOf("Markdown Styler", "Summarization", "Style Matching")
        ),
        RolePreset(
            id = "general_assistant",
            title = "General Assistant",
            description = "Flexible daily assistant for task management and rapid Q&A",
            iconEmoji = "⚡",
            defaultCapabilities = listOf("Task Orchestration", "General Q&A", "Swarm Member")
        )
    )

    init {
        // Collect discovered agents
        viewModelScope.launch {
            mdnsDiscoveryManager.discoveredAgents.collect { nodes ->
                _uiState.update { it.copy(discoveredNodes = nodes) }
            }
        }
    }

    // Step Navigation
    fun goToStep(step: BotCreatorStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun nextStep() {
        val next = when (_uiState.value.currentStep) {
            BotCreatorStep.PROFILE -> {
                if (_uiState.value.agentName.isBlank()) {
                    viewModelScope.launch {
                        _uiEvents.emit(BotCreatorUiEvent.ShowToast("Please enter an agent name to continue"))
                    }
                    return
                }
                BotCreatorStep.ROLE_PRESET
            }
            BotCreatorStep.ROLE_PRESET -> BotCreatorStep.BRAIN_CONNECTIVITY
            BotCreatorStep.BRAIN_CONNECTIVITY -> {
                mintSovereignAgent()
                return
            }
            BotCreatorStep.SUCCESS -> BotCreatorStep.SUCCESS
        }
        _uiState.update { it.copy(currentStep = next) }
    }

    fun previousStep() {
        val prev = when (_uiState.value.currentStep) {
            BotCreatorStep.PROFILE -> BotCreatorStep.PROFILE
            BotCreatorStep.ROLE_PRESET -> BotCreatorStep.PROFILE
            BotCreatorStep.BRAIN_CONNECTIVITY -> BotCreatorStep.ROLE_PRESET
            BotCreatorStep.SUCCESS -> BotCreatorStep.BRAIN_CONNECTIVITY
        }
        _uiState.update { it.copy(currentStep = prev) }
    }

    // Step 1: Profile setters
    fun setAgentName(name: String) {
        _uiState.update { it.copy(agentName = name) }
    }

    fun setDescription(desc: String) {
        _uiState.update { it.copy(description = desc) }
    }

    fun setColorAccent(colorHex: String) {
        _uiState.update { it.copy(colorAccent = colorHex) }
    }

    fun suggestRandomProfile() {
        val identities = listOf(
            Triple("Jarvis", "Autonomous cognitive assistant for task orchestration and analysis", "#00D2FF"),
            Triple("Athena", "Strategic intelligence and precision problem solving agent", "#8B5CF6"),
            Triple("DevCopilot", "Full-stack code generation, debugging, and terminal assistant", "#3D8EFF"),
            Triple("Sentinel", "Cryptographic key attestation and sovereign policy vanguard", "#10B981"),
            Triple("Scribe", "Technical documentation, storytelling, and synthesis specialist", "#F59E0B"),
            Triple("Nexus", "Multi-agent swarm coordinator and task dispatcher", "#EC4899")
        )
        val pick = identities.random()
        _uiState.update {
            it.copy(
                agentName = pick.first,
                description = pick.second,
                colorAccent = pick.third
            )
        }
    }

    // Step 2: Role setters
    fun selectRolePreset(presetId: String) {
        val preset = rolePresets.find { it.id == presetId }
        _uiState.update {
            it.copy(
                selectedRoleId = presetId,
                selectedCapabilities = preset?.defaultCapabilities?.toSet() ?: it.selectedCapabilities
            )
        }
    }

    fun setCustomRoleName(name: String) {
        _uiState.update { it.copy(customRoleName = name) }
    }

    fun toggleCapability(capability: String) {
        _uiState.update { current ->
            val updated = current.selectedCapabilities.toMutableSet()
            if (updated.contains(capability)) {
                updated.remove(capability)
            } else {
                updated.add(capability)
            }
            current.copy(selectedCapabilities = updated)
        }
    }

    // Step 3: Brain & Connectivity setters
    fun setBrainSource(source: BrainSourceType) {
        _uiState.update { it.copy(brainSource = source) }
    }

    fun setEndpointUrl(url: String) {
        _uiState.update { it.copy(endpointUrl = url) }
    }

    fun setModelIdentifier(model: String) {
        _uiState.update { it.copy(modelIdentifier = model) }
    }

    fun setTunnelUrl(url: String) {
        _uiState.update {
            it.copy(
                tunnelUrl = url,
                tunnelConnectionSuccess = null,
                tunnelConnectionError = null
            )
        }
    }

    fun setTunnelAuthToken(token: String) {
        _uiState.update {
            it.copy(
                tunnelAuthToken = token,
                tunnelConnectionSuccess = null,
                tunnelConnectionError = null
            )
        }
    }

    // Backward compatibility aliases
    fun setEnterpriseHubUrl(url: String) = setTunnelUrl(url)
    fun setEnterpriseAuthToken(token: String) = setTunnelAuthToken(token)

    fun applyTunnelPreset(tunnelName: String, url: String) {
        _uiState.update {
            it.copy(
                tunnelUrl = url,
                tunnelConnectionSuccess = null,
                tunnelConnectionError = null
            )
        }
        viewModelScope.launch {
            _uiEvents.emit(BotCreatorUiEvent.ShowToast("Selected tunnel: $tunnelName"))
        }
    }

    fun applyEnterpriseTemplate(templateName: String, url: String, model: String) {
        _uiState.update {
            it.copy(
                tunnelUrl = url,
                modelIdentifier = model,
                tunnelConnectionSuccess = null,
                tunnelConnectionError = null
            )
        }
        viewModelScope.launch {
            _uiEvents.emit(BotCreatorUiEvent.ShowToast("Applied tunnel: $templateName"))
        }
    }

    fun parseAndApplyQrPayload(raw: String) {
        try {
            val trimmed = raw.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = JSONObject(trimmed)
                val url = json.optString("endpoint", json.optString("url", json.optString("mcp_url", "")))
                val token = json.optString("token", json.optString("auth_token", json.optString("api_key", "")))
                val model = json.optString("model", "")
                val name = json.optString("name", "")

                val isLocal = url.contains("localhost") || url.contains("127.0.0.1") || url.contains("192.168.")
                val detectedSource = if (isLocal) BrainSourceType.LOCAL_WIFI else BrainSourceType.REMOTE_TUNNEL

                _uiState.update { current ->
                    current.copy(
                        brainSource = detectedSource,
                        tunnelUrl = if (url.isNotBlank()) url else current.tunnelUrl,
                        endpointUrl = if (isLocal && url.isNotBlank()) url else current.endpointUrl,
                        tunnelAuthToken = if (token.isNotBlank()) token else current.tunnelAuthToken,
                        modelIdentifier = if (model.isNotBlank()) model else current.modelIdentifier,
                        agentName = if (name.isNotBlank() && current.agentName.isBlank()) name else current.agentName
                    )
                }
                testTunnelConnection()
                return
            }

            // URI format: baton://connect?url=...&token=... or https://...
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("wss://") || trimmed.startsWith("ws://") || trimmed.startsWith("baton://")) {
                val queryPart = trimmed.substringAfter("?", "")
                val queryParams = if (queryPart.isNotBlank()) {
                    queryPart.split("&").mapNotNull { param ->
                        val parts = param.split("=", limit = 2)
                        if (parts.size == 2) {
                            try {
                                val key = java.net.URLDecoder.decode(parts[0], "UTF-8")
                                val value = java.net.URLDecoder.decode(parts[1], "UTF-8")
                                key to value
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                    }.toMap()
                } else emptyMap()

                val urlParam = queryParams["url"] ?: queryParams["endpoint"]
                val tokenParam = queryParams["token"] ?: queryParams["auth"] ?: queryParams["api_key"]
                val modelParam = queryParams["model"]

                val effectiveUrl = if (!urlParam.isNullOrBlank()) {
                    urlParam
                } else {
                    if (trimmed.startsWith("http") || trimmed.startsWith("ws")) {
                        trimmed.substringBefore("?")
                    } else trimmed
                }

                val isLocal = effectiveUrl.contains("localhost") || effectiveUrl.contains("127.0.0.1") || effectiveUrl.contains("192.168.")
                val detectedSource = if (isLocal) BrainSourceType.LOCAL_WIFI else BrainSourceType.REMOTE_TUNNEL

                _uiState.update { current ->
                    current.copy(
                        brainSource = detectedSource,
                        tunnelUrl = effectiveUrl,
                        endpointUrl = if (isLocal) effectiveUrl else current.endpointUrl,
                        tunnelAuthToken = tokenParam ?: current.tunnelAuthToken,
                        modelIdentifier = modelParam ?: current.modelIdentifier
                    )
                }
                testTunnelConnection()
                return
            }

            // Fallback: direct string
            val isLocal = trimmed.contains("localhost") || trimmed.contains("127.0.0.1") || trimmed.contains("192.168.")
            val detectedSource = if (isLocal) BrainSourceType.LOCAL_WIFI else BrainSourceType.REMOTE_TUNNEL

            _uiState.update {
                it.copy(
                    brainSource = detectedSource,
                    tunnelUrl = trimmed,
                    endpointUrl = if (isLocal) trimmed else it.endpointUrl
                )
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                _uiEvents.emit(BotCreatorUiEvent.ShowToast("Invalid QR format: ${e.message}"))
            }
        }
    }

    fun testTunnelConnection() {
        val state = _uiState.value
        val url = if (state.brainSource == BrainSourceType.LOCAL_WIFI) state.endpointUrl.trim() else state.tunnelUrl.trim()
        if (url.isBlank()) {
            viewModelScope.launch {
                _uiEvents.emit(BotCreatorUiEvent.ShowToast("Please enter an endpoint or tunnel URL"))
            }
            return
        }

        _uiState.update {
            it.copy(
                isTestingTunnelConnection = true,
                tunnelConnectionSuccess = null,
                tunnelConnectionError = null
            )
        }

        viewModelScope.launch {
            try {
                val token = state.tunnelAuthToken.trim()
                val authHeader = if (token.isNotBlank()) "Bearer $token" else null
                val probeId = "probe_${UUID.randomUUID().toString().take(8)}"

                val result = mcpConnectionManager.getOrCreateSession(
                    agentId = probeId,
                    endpointUrl = url,
                    authHeader = authHeader
                )

                if (result.isSuccess) {
                    val session = result.getOrThrow()
                    val tools = session.availableTools
                    _uiState.update {
                        it.copy(
                            isTestingTunnelConnection = false,
                            tunnelConnectionSuccess = true,
                            tunnelConnectionError = null,
                            discoveredTunnelTools = tools,
                            endpointUrl = url
                        )
                    }
                    _uiEvents.emit(
                        BotCreatorUiEvent.ShowToast(
                            "Connection Handshake Verified! Discovered ${tools.size} tools."
                        )
                    )
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to reach endpoint"
                    _uiState.update {
                        it.copy(
                            isTestingTunnelConnection = false,
                            tunnelConnectionSuccess = false,
                            tunnelConnectionError = errorMsg
                        )
                    }
                    _uiEvents.emit(BotCreatorUiEvent.ShowToast("Connection test failed: $errorMsg"))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isTestingTunnelConnection = false,
                        tunnelConnectionSuccess = false,
                        tunnelConnectionError = e.message ?: "Unknown error occurred"
                    )
                }
                _uiEvents.emit(BotCreatorUiEvent.ShowToast("Connection error: ${e.message}"))
            }
        }
    }

    fun testEnterpriseConnection() = testTunnelConnection()

    fun toggleWifiDiscovery(enable: Boolean) {
        _uiState.update { it.copy(isWifiDiscovering = enable) }
        autoStopDiscoveryJob?.cancel()

        if (enable) {
            mdnsDiscoveryManager.startDiscovery()
            // Auto-off timer after 45 seconds to preserve battery
            autoStopDiscoveryJob = viewModelScope.launch {
                delay(45_000)
                mdnsDiscoveryManager.stopDiscovery()
                _uiState.update { it.copy(isWifiDiscovering = false) }
            }
        } else {
            mdnsDiscoveryManager.stopDiscovery()
        }
    }

    fun selectDiscoveredNode(node: DiscoveredAgent) {
        val resolvedUrl = if (node.url.isNotBlank()) node.url else "http://${node.ipAddress}:${node.port}/v1"
        _uiState.update {
            it.copy(
                endpointUrl = resolvedUrl
            )
        }
        viewModelScope.launch {
            _uiEvents.emit(BotCreatorUiEvent.ShowToast("Connected to Wi-Fi node: ${node.name}"))
        }
    }

    // Step 4: Minting
    fun mintSovereignAgent() {
        val state = _uiState.value
        if (state.isMinting) return

        _uiState.update { it.copy(isMinting = true) }

        viewModelScope.launch {
            try {
                val agentId = UUID.randomUUID().toString()
                val did = generateSovereignDid()
                val fingerprint = generateKeyFingerprint(did)
                val numericId = IdentityBridge.deriveNumericId(did)
                val tokenBytes = ByteArray(24)
                java.security.SecureRandom.getInstanceStrong().nextBytes(tokenBytes)
                val relayToken = "btn_" + tokenBytes.joinToString("") { "%02x".format(it) }
                val botToken = IdentityBridge.formatBotToken(numericId, relayToken)

                val selectedPreset = rolePresets.find { it.id == state.selectedRoleId }
                val roleName = if (state.selectedRoleId == "custom" && state.customRoleName.isNotBlank()) {
                    state.customRoleName
                } else {
                    selectedPreset?.title ?: "GENERAL_ASSISTANT"
                }

                val capabilitiesList = if (state.brainSource == BrainSourceType.REMOTE_TUNNEL && state.discoveredTunnelTools.isNotEmpty()) {
                    state.discoveredTunnelTools.map { it.name }
                } else {
                    state.selectedCapabilities.toList()
                }

                val initialSecurityConfig = JSONObject().apply {
                    put("privacy_mode", if (state.brainSource == BrainSourceType.BATON_BOT) "air_gapped" else "mention_only")
                    put("hardware_attestation", "android_keystore_backed")
                    put("keys_minted_at", System.currentTimeMillis())
                    put("capabilities", capabilitiesList)
                    put("model", state.modelIdentifier)
                    put("connection_mode", state.brainSource.name)
                    when (state.brainSource) {
                        BrainSourceType.REMOTE_TUNNEL -> {
                            put("is_remote_tunnel", true)
                            put("discovered_tools_count", state.discoveredTunnelTools.size)
                        }
                        BrainSourceType.BATON_BOT -> {
                            put("is_sovereign_on_device", true)
                        }
                        BrainSourceType.LOCAL_WIFI -> {
                            put("is_local_wifi", true)
                        }
                    }
                }.toString()

                val effectiveEndpointUrl = when (state.brainSource) {
                    BrainSourceType.LOCAL_WIFI -> state.endpointUrl.ifBlank { "http://localhost:11434/v1" }
                    BrainSourceType.REMOTE_TUNNEL -> state.tunnelUrl.ifBlank { "https://my-antigravity.trycloudflare.com/sse" }
                    BrainSourceType.BATON_BOT -> "local://baton-sovereign-core"
                }

                val effectiveProviderType = when (state.brainSource) {
                    BrainSourceType.LOCAL_WIFI -> "local_mcp"
                    BrainSourceType.REMOTE_TUNNEL -> "remote_tunnel"
                    BrainSourceType.BATON_BOT -> "sovereign_on_device"
                }

                val effectiveAuthConfig = when (state.brainSource) {
                    BrainSourceType.REMOTE_TUNNEL -> state.tunnelAuthToken
                    else -> ""
                }

                val effectiveAuthType = when (state.brainSource) {
                    BrainSourceType.REMOTE_TUNNEL -> if (state.tunnelAuthToken.isNotBlank()) "bearer" else "none"
                    BrainSourceType.BATON_BOT -> "hardware_keystore"
                    BrainSourceType.LOCAL_WIFI -> "none"
                }

                val defaultName = when (state.brainSource) {
                    BrainSourceType.REMOTE_TUNNEL -> "Remote Antigravity Bot"
                    BrainSourceType.BATON_BOT -> "Sovereign Baton Bot"
                    BrainSourceType.LOCAL_WIFI -> "Local Wi-Fi Agent"
                }

                val newAgent = Agent(
                    id = agentId,
                    name = state.agentName.ifBlank { defaultName },
                    description = state.description.ifBlank { selectedPreset?.description ?: "Autonomous AI Assistant" },
                    providerType = effectiveProviderType,
                    mcpEndpointUrl = effectiveEndpointUrl,
                    authType = effectiveAuthType,
                    authConfig = effectiveAuthConfig,
                    relayToken = relayToken,
                    isActive = true,
                    colorAccent = state.colorAccent,
                    securityMode = "sovereign",
                    securityConfig = initialSecurityConfig,
                    role = roleName,
                    cardDid = did,
                    cardFingerprint = fingerprint,
                    cardIssuedAt = System.currentTimeMillis(),
                    isAuthenticated = (state.tunnelConnectionSuccess == true)
                )

                // Persist to Room database
                agentRepository.upsertAgent(newAgent)

                // Turn off discovery if active
                if (state.isWifiDiscovering) {
                    toggleWifiDiscovery(false)
                }

                _uiState.update {
                    it.copy(
                        isMinting = false,
                        mintedAgent = newAgent,
                        numericAgentId = numericId,
                        botToken = botToken,
                        currentStep = BotCreatorStep.SUCCESS
                    )
                }

                _uiEvents.emit(BotCreatorUiEvent.AgentCreated(newAgent))
            } catch (e: Exception) {
                _uiState.update { it.copy(isMinting = false) }
                _uiEvents.emit(BotCreatorUiEvent.ShowToast("Failed to create bot: ${e.message}"))
            }
        }
    }

    fun resetWizard() {
        _uiState.value = BotCreatorUiState()
    }

    private fun generateSovereignDid(): String {
        val randomBytes = ByteArray(32)
        java.security.SecureRandom.getInstanceStrong().nextBytes(randomBytes)
        val hex = randomBytes.joinToString("") { "%02x".format(it) }
        return "did:key:z6M$hex"
    }

    private fun generateKeyFingerprint(did: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(did.toByteArray())
        return digest.take(8).joinToString(":") { "%02X".format(it) }
    }

    override fun onCleared() {
        super.onCleared()
        autoStopDiscoveryJob?.cancel()
        if (_uiState.value.isWifiDiscovering) {
            mdnsDiscoveryManager.stopDiscovery()
        }
    }
}
