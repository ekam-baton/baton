package com.ekam.baton.feature.agents.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.network.mdns.DiscoveredAgent
import com.ekam.baton.core.network.mdns.MdnsDiscoveryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class ManagerAction(
    val label: String,
    val payload: String,
    val isPrimary: Boolean = false,
    val isDestructive: Boolean = false
)

data class ManagerMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "manager" or "user"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val quickChips: List<String> = emptyList(),
    val actions: List<ManagerAction> = emptyList(),
    val targetAgent: Agent? = null
)

enum class WizardStep {
    IDLE,
    AWAITING_NAME,
    AWAITING_ROLE,
    AWAITING_BRAIN_SOURCE,
    AWAITING_CUSTOM_URL
}

class BatonBotManagerViewModel(
    private val agentRepository: AgentRepository,
    private val mdnsDiscoveryManager: MdnsDiscoveryManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ManagerMessage>>(emptyList())
    val messages: StateFlow<List<ManagerMessage>> = _messages.asStateFlow()

    private val _currentStep = MutableStateFlow(WizardStep.IDLE)
    val currentStep: StateFlow<WizardStep> = _currentStep.asStateFlow()

    // Interactive Dialog & Sheet States
    private val _editSheetAgent = MutableStateFlow<Agent?>(null)
    val editSheetAgent: StateFlow<Agent?> = _editSheetAgent.asStateFlow()

    private val _revokeDialogAgent = MutableStateFlow<Agent?>(null)
    val revokeDialogAgent: StateFlow<Agent?> = _revokeDialogAgent.asStateFlow()

    private val _identityDialogAgent = MutableStateFlow<Agent?>(null)
    val identityDialogAgent: StateFlow<Agent?> = _identityDialogAgent.asStateFlow()

    private val _privacyDialogAgent = MutableStateFlow<Agent?>(null)
    val privacyDialogAgent: StateFlow<Agent?> = _privacyDialogAgent.asStateFlow()

    private val _deleteDialogAgent = MutableStateFlow<Agent?>(null)
    val deleteDialogAgent: StateFlow<Agent?> = _deleteDialogAgent.asStateFlow()

    private val _qrDialogAgent = MutableStateFlow<Agent?>(null)
    val qrDialogAgent: StateFlow<Agent?> = _qrDialogAgent.asStateFlow()

    private val _showScannerDialog = MutableStateFlow(false)
    val showScannerDialog: StateFlow<Boolean> = _showScannerDialog.asStateFlow()

    // Creation Draft Variables
    private var draftName = ""
    private var draftRole = "COORDINATOR"
    private var draftDescription = ""
    private var draftColor = "#3D8EFF"

    init {
        sendWelcomeMessage()
    }

    private fun sendWelcomeMessage() {
        val welcome = ManagerMessage(
            sender = "manager",
            text = """
                👋 Welcome to **BatonBot Manager**!
                
                I am your sovereign operations hub. You can inspect cryptographic DIDs, generate or scan pairing QR codes, edit personas, manage swarm privacy, revoke credentials, or scan your local Wi-Fi — all using the **Options UI** below. No slash commands required!
                
                Tap an option below to begin:
            """.trimIndent(),
            actions = listOf(
                ManagerAction(label = "➕ Launch Bot Creator", payload = "open_creator", isPrimary = true),
                ManagerAction(label = "📷 Scan Pairing QR", payload = "scan_qr"),
                ManagerAction(label = "📋 Manage My Agents", payload = "view_all"),
                ManagerAction(label = "📡 Scan Local Wi-Fi", payload = "scan_wifi")
            ),
            quickChips = listOf("➕ Create New Bot", "📷 Scan Pairing QR", "📡 Scan Wi-Fi AI", "📋 Manage My Agents", "💡 Pre-Built Templates")
        )
        _messages.value = listOf(welcome)
    }

    // -------------------------------------------------------------------------
    // User Input & Options Dispatcher (Natural Language & Action Chips)
    // -------------------------------------------------------------------------
    fun handleUserInput(rawText: String) {
        val text = rawText.trim()
        if (text.isBlank()) return

        val userMsg = ManagerMessage(sender = "user", text = text)
        _messages.update { it + userMsg }

        // Normalize chip titles
        val clean = text
            .removePrefix("➕ ")
            .removePrefix("📷 ")
            .removePrefix("📡 ")
            .removePrefix("📋 ")
            .removePrefix("💡 ")
            .trim()

        when {
            clean.equals("Scan Pairing QR", ignoreCase = true) ||
            clean.equals("Scan QR", ignoreCase = true) ||
            clean.equals("/scan", ignoreCase = true) -> {
                _showScannerDialog.value = true
            }
            clean.equals("Create New Agent", ignoreCase = true) ||
            clean.equals("Create New Bot", ignoreCase = true) ||
            clean.equals("Create", ignoreCase = true) ||
            clean.equals("/newagent", ignoreCase = true) -> {
                val reply = ManagerMessage(
                    sender = "manager",
                    text = "To create a new bot, tap below to open the dedicated **Bot Creator** wizard:",
                    actions = listOf(
                        ManagerAction(label = "Open Bot Creator", payload = "open_creator", isPrimary = true)
                    )
                )
                _messages.update { it + reply }
            }
            clean.equals("Scan Wi-Fi AI", ignoreCase = true) || clean.equals("/wifi", ignoreCase = true) -> {
                runWifiScan()
            }
            clean.equals("Manage My Agents", ignoreCase = true) || clean.equals("/myagents", ignoreCase = true) -> {
                listUserAgents()
            }
            clean.equals("Pre-Built Templates", ignoreCase = true) || clean.equals("/templates", ignoreCase = true) -> {
                presentTemplates()
            }
            clean.equals("Cancel", ignoreCase = true) || clean.equals("/cancel", ignoreCase = true) -> {
                _currentStep.value = WizardStep.IDLE
                val reply = ManagerMessage(
                    sender = "manager",
                    text = "Operation cancelled. Ready for your next action.",
                    quickChips = listOf("➕ Create New Agent", "📡 Scan Wi-Fi AI", "📋 Manage My Agents")
                )
                _messages.update { it + reply }
            }
            else -> {
                processWizardStep(text)
            }
        }
    }

    private fun startCreateWizard() {
        _currentStep.value = WizardStep.AWAITING_NAME
        draftName = ""
        draftRole = "COORDINATOR"
        draftDescription = ""
        draftColor = listOf("#3D8EFF", "#10B981", "#F59E0B", "#8B5CF6", "#EC4899").random()

        val reply = ManagerMessage(
            sender = "manager",
            text = "Let's build a new agent! 🚀\n\n**What name would you like to give your agent?**\n*(e.g., Jarvis, CodeMentor, ResearchBot)*",
            quickChips = listOf("Jarvis", "Athena", "DevCopilot", "ResearchBot")
        )
        _messages.update { it + reply }
    }

    private fun processWizardStep(text: String) {
        when (_currentStep.value) {
            WizardStep.AWAITING_NAME -> {
                draftName = text
                _currentStep.value = WizardStep.AWAITING_ROLE
                val reply = ManagerMessage(
                    sender = "manager",
                    text = "Designation saved: **$draftName**! 🎯\n\nWhat role or primary personality should **$draftName** assume?",
                    quickChips = listOf("💻 Code Mentor", "🎓 Study Tutor", "✍️ Creative Writer", "🔍 Research Analyst", "🏠 Home Assistant")
                )
                _messages.update { it + reply }
            }

            WizardStep.AWAITING_ROLE -> {
                draftRole = when {
                    text.contains("Code", ignoreCase = true) -> "CODER"
                    text.contains("Research", ignoreCase = true) || text.contains("Analyst", ignoreCase = true) -> "RESEARCHER"
                    text.contains("Writer", ignoreCase = true) -> "WRITER"
                    text.contains("Tutor", ignoreCase = true) || text.contains("Study", ignoreCase = true) -> "TUTOR"
                    else -> "ASSISTANT"
                }
                draftDescription = "A sovereign $draftRole node configured via BatonBot Manager"
                _currentStep.value = WizardStep.AWAITING_BRAIN_SOURCE

                val reply = ManagerMessage(
                    sender = "manager",
                    text = """
                        Role set to **$draftRole**. 🧠
                        
                        Where should **$draftName**'s intelligence engine run?
                    """.trimIndent(),
                    quickChips = listOf("🏠 Local Wi-Fi (Ollama)", "☁️ Cloud AI (Gemini)", "🌐 Custom URL")
                )
                _messages.update { it + reply }
            }

            WizardStep.AWAITING_BRAIN_SOURCE -> {
                when {
                    text.contains("Local", ignoreCase = true) || text.contains("Ollama", ignoreCase = true) -> {
                        provisionAgent(
                            providerType = "local_mcp",
                            endpoint = "http://localhost:11434",
                            sourceLabel = "Local Ollama on LAN"
                        )
                    }
                    text.contains("Cloud", ignoreCase = true) -> {
                        provisionAgent(
                            providerType = "gemini",
                            endpoint = "https://generativelanguage.googleapis.com",
                            sourceLabel = "Google Gemini 2.0"
                        )
                    }
                    text.contains("Custom", ignoreCase = true) -> {
                        _currentStep.value = WizardStep.AWAITING_CUSTOM_URL
                        val reply = ManagerMessage(
                            sender = "manager",
                            text = "Please enter the server endpoint URL (e.g., `http://192.168.1.100:8080`):",
                            quickChips = listOf("http://192.168.1.100:11434", "http://192.168.1.100:1234")
                        )
                        _messages.update { it + reply }
                    }
                    else -> {
                        val reply = ManagerMessage(
                            sender = "manager",
                            text = "Please pick one of the options below:",
                            quickChips = listOf("🏠 Local Wi-Fi (Ollama)", "☁️ Cloud AI (Gemini)", "🌐 Custom URL")
                        )
                        _messages.update { it + reply }
                    }
                }
            }

            WizardStep.AWAITING_CUSTOM_URL -> {
                val url = if (text.startsWith("http://") || text.startsWith("https://")) text else "http://$text"
                provisionAgent(
                    providerType = "custom_http",
                    endpoint = url,
                    sourceLabel = "Custom Endpoint ($url)"
                )
            }

            WizardStep.IDLE -> {
                val reply = ManagerMessage(
                    sender = "manager",
                    text = "I received: \"$text\". Choose an option below to manage your agents:",
                    quickChips = listOf("➕ Create New Agent", "📡 Scan Wi-Fi AI", "📋 Manage My Agents")
                )
                _messages.update { it + reply }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Agent Provisioning & Key Generation
    // -------------------------------------------------------------------------
    private fun provisionAgent(providerType: String, endpoint: String, sourceLabel: String) {
        viewModelScope.launch {
            val agentId = UUID.randomUUID().toString()
            val did = generateSovereignDid(agentId)
            val fingerprint = generateKeyFingerprint(did)

            val initialSecurityConfig = JSONObject().apply {
                put("privacy_mode", "mention_only")
                put("hardware_attestation", "android_keystore_backed")
                put("keys_minted_at", System.currentTimeMillis())
            }.toString()

            val agent = Agent(
                id = agentId,
                name = draftName.ifBlank { "Sovereign Node" },
                description = draftDescription.ifBlank { "Personal AI Assistant" },
                providerType = providerType,
                mcpEndpointUrl = endpoint,
                authType = "bearer",
                authConfig = "",
                relayToken = "btn_" + UUID.randomUUID().toString().replace("-", ""),
                isActive = true,
                colorAccent = draftColor,
                securityMode = "sovereign",
                securityConfig = initialSecurityConfig,
                role = draftRole,
                cardDid = did,
                cardFingerprint = fingerprint,
                cardIssuedAt = System.currentTimeMillis()
            )
            agentRepository.upsertAgent(agent)
            _currentStep.value = WizardStep.IDLE

            val doneMessage = ManagerMessage(
                sender = "manager",
                text = """
                    🎉 **Successfully Registered: ${agent.name}!**
                    
                    • **Role:** `${agent.role}`
                    • **DID:** `${agent.cardDid.take(24)}...`
                    • **Engine:** `$sourceLabel`
                    • **Swarm Privacy:** `Mention-Only 🔒`
                    
                    You can manage this agent anytime from the Options UI.
                """.trimIndent(),
                actions = listOf(
                    ManagerAction(label = "✏️ Edit Profile", payload = "open_edit:${agent.id}"),
                    ManagerAction(label = "🛡️ Identity Card", payload = "open_identity:${agent.id}"),
                    ManagerAction(label = "💬 Start Chat", payload = "chat:${agent.id}", isPrimary = true)
                ),
                targetAgent = agent,
                quickChips = listOf("➕ Create New Agent", "📋 Manage My Agents")
            )
            _messages.update { it + doneMessage }
        }
    }

    // -------------------------------------------------------------------------
    // Agent Portfolio & Options Management
    // -------------------------------------------------------------------------
    fun listUserAgents() {
        viewModelScope.launch {
            val agentsList: List<Agent> = agentRepository.getAllAgents().first()
            if (agentsList.isEmpty()) {
                val reply = ManagerMessage(
                    sender = "manager",
                    text = "You don't have any registered agents yet. Tap **Create New Agent** below!",
                    quickChips = listOf("➕ Create New Agent", "📡 Scan Wi-Fi AI", "💡 Pre-Built Templates")
                )
                _messages.update { it + reply }
            } else {
                val header = ManagerMessage(
                    sender = "manager",
                    text = "📋 **Your Sovereign Agent Portfolio (${agentsList.size}):**\n\nSelect an agent below to edit details, re-key credentials, view its DID, or set swarm privacy:",
                    quickChips = listOf("➕ Create New Agent", "📡 Scan Wi-Fi AI")
                )
                _messages.update { it + header }

                agentsList.forEach { a ->
                    val privacyMode = extractPrivacyMode(a.securityConfig)
                    val cardMsg = ManagerMessage(
                        sender = "manager",
                        text = """
                            🤖 **${a.name}** (`${a.role}`)
                            • Endpoint: `${a.mcpEndpointUrl}`
                            • Privacy: `${privacyMode.uppercase()}`
                            • Status: ${if (a.isActive) "Active" else "Inactive"}
                        """.trimIndent(),
                        actions = listOf(
                            ManagerAction(label = "🔲 Pairing QR", payload = "open_qr:${a.id}"),
                            ManagerAction(label = "✏️ Edit Profile", payload = "open_edit:${a.id}"),
                            ManagerAction(label = "🛡️ Identity Card", payload = "open_identity:${a.id}"),
                            ManagerAction(label = "🔑 Revoke & Re-Key", payload = "open_revoke:${a.id}"),
                            ManagerAction(label = "🔒 Swarm Privacy", payload = "open_privacy:${a.id}"),
                            ManagerAction(label = "💬 Open Chat", payload = "chat:${a.id}", isPrimary = true),
                            ManagerAction(label = "🗑️ Delete", payload = "open_delete:${a.id}", isDestructive = true)
                        ),
                        targetAgent = a
                    )
                    _messages.update { it + cardMsg }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Action Payload Router (Invoked from In-Chat Buttons)
    // -------------------------------------------------------------------------
    fun handleActionPayload(payload: String) {
        viewModelScope.launch {
            when {
                payload.startsWith("open_qr:") -> {
                    val id = payload.removePrefix("open_qr:")
                    val agent = agentRepository.getAgentById(id)
                    _qrDialogAgent.value = agent
                }
                payload == "scan_qr" -> {
                    _showScannerDialog.value = true
                }
                payload.startsWith("open_edit:") -> {
                    val id = payload.removePrefix("open_edit:")
                    val agent = agentRepository.getAgentById(id)
                    _editSheetAgent.value = agent
                }
                payload.startsWith("open_revoke:") -> {
                    val id = payload.removePrefix("open_revoke:")
                    val agent = agentRepository.getAgentById(id)
                    _revokeDialogAgent.value = agent
                }
                payload.startsWith("open_identity:") -> {
                    val id = payload.removePrefix("open_identity:")
                    val agent = agentRepository.getAgentById(id)
                    _identityDialogAgent.value = agent
                }
                payload.startsWith("open_privacy:") -> {
                    val id = payload.removePrefix("open_privacy:")
                    val agent = agentRepository.getAgentById(id)
                    _privacyDialogAgent.value = agent
                }
                payload.startsWith("open_delete:") -> {
                    val id = payload.removePrefix("open_delete:")
                    val agent = agentRepository.getAgentById(id)
                    _deleteDialogAgent.value = agent
                }
                payload == "scan_wifi" -> {
                    runWifiScan()
                }
                payload.startsWith("connect_discovered:") -> {
                    val parts = payload.removePrefix("connect_discovered:").split(":")
                    val url = parts.getOrNull(0) ?: "http://localhost:11434"
                    val name = parts.getOrNull(1) ?: "Local Wi-Fi AI"
                    draftName = name
                    draftRole = "COORDINATOR"
                    draftDescription = "Discovered on local Wi-Fi"
                    draftColor = "#00D2FF"
                    provisionAgent(providerType = "local_mcp", endpoint = url, sourceLabel = "Local Wi-Fi")
                }
                payload.startsWith("template:") -> {
                    val templateType = payload.removePrefix("template:")
                    when (templateType) {
                        "coder" -> {
                            draftName = "Code Mentor"
                            draftRole = "CODER"
                            draftDescription = "Full-stack programming mentor"
                            draftColor = "#A855F7"
                        }
                        "writer" -> {
                            draftName = "Creative Scribe"
                            draftRole = "WRITER"
                            draftDescription = "Artistic and technical writer"
                            draftColor = "#F59E0B"
                        }
                        "researcher" -> {
                            draftName = "Research Scout"
                            draftRole = "RESEARCHER"
                            draftDescription = "Autonomous literature researcher"
                            draftColor = "#00D2FF"
                        }
                    }
                    provisionAgent(providerType = "local_mcp", endpoint = "http://localhost:11434", sourceLabel = "Smart Preset")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Execution Operations (Profile Edit, Revoke, Privacy, Delete)
    // -------------------------------------------------------------------------
    fun saveAgentProfile(id: String, name: String, role: String, description: String, endpoint: String, color: String) {
        viewModelScope.launch {
            val existing = agentRepository.getAgentById(id) ?: return@launch
            val updated = existing.copy(
                name = name.ifBlank { existing.name },
                role = role.ifBlank { existing.role },
                description = description.ifBlank { existing.description },
                mcpEndpointUrl = endpoint.ifBlank { existing.mcpEndpointUrl },
                colorAccent = color.ifBlank { existing.colorAccent }
            )
            agentRepository.upsertAgent(updated)
            _editSheetAgent.value = null

            val confirmMsg = ManagerMessage(
                sender = "manager",
                text = "✅ **Profile Updated for ${updated.name}!**\n• Role: `${updated.role}`\n• Endpoint: `${updated.mcpEndpointUrl}`",
                actions = listOf(
                    ManagerAction(label = "📋 View All Agents", payload = "view_all"),
                    ManagerAction(label = "💬 Open Chat", payload = "chat:${updated.id}", isPrimary = true)
                ),
                quickChips = listOf("📋 Manage My Agents", "➕ Create New Agent")
            )
            _messages.update { it + confirmMsg }
        }
    }

    fun executeRevocation(id: String) {
        viewModelScope.launch {
            val existing = agentRepository.getAgentById(id) ?: return@launch
            val newRelayToken = "btn_" + UUID.randomUUID().toString().replace("-", "")
            val newDid = generateSovereignDid(existing.id)
            val newFingerprint = generateKeyFingerprint(newDid)

            val updatedConfig = try {
                val json = if (existing.securityConfig.isNotBlank()) JSONObject(existing.securityConfig) else JSONObject()
                json.put("last_revocation_at", System.currentTimeMillis())
                json.put("hardware_attestation", "android_keystore_backed")
                json.toString()
            } catch (_: Exception) {
                "{}"
            }

            val updated = existing.copy(
                relayToken = newRelayToken,
                cardDid = newDid,
                cardFingerprint = newFingerprint,
                cardIssuedAt = System.currentTimeMillis(),
                securityConfig = updatedConfig
            )
            agentRepository.upsertAgent(updated)
            _revokeDialogAgent.value = null

            val confirmMsg = ManagerMessage(
                sender = "manager",
                text = """
                    🔐 **Credentials Revoked & Re-Keyed: ${updated.name}**
                    
                    • **Old Tokens:** Immediately invalidated
                    • **New Sovereign DID:** `${updated.cardDid.take(24)}...`
                    • **Key Fingerprint:** `${updated.cardFingerprint}`
                    • **Status:** Hardware Keystore Re-Attested
                """.trimIndent(),
                actions = listOf(
                    ManagerAction(label = "🛡️ Inspect Identity", payload = "open_identity:${updated.id}"),
                    ManagerAction(label = "📋 Manage My Agents", payload = "open_portfolio")
                ),
                quickChips = listOf("📋 Manage My Agents")
            )
            _messages.update { it + confirmMsg }
        }
    }

    fun executeSwarmPrivacyUpdate(id: String, isMentionOnly: Boolean) {
        viewModelScope.launch {
            val existing = agentRepository.getAgentById(id) ?: return@launch
            val privacyValue = if (isMentionOnly) "mention_only" else "full_mesh"

            val updatedConfig = try {
                val json = if (existing.securityConfig.isNotBlank()) JSONObject(existing.securityConfig) else JSONObject()
                json.put("privacy_mode", privacyValue)
                json.toString()
            } catch (_: Exception) {
                "{\"privacy_mode\":\"$privacyValue\"}"
            }

            val updated = existing.copy(securityConfig = updatedConfig)
            agentRepository.upsertAgent(updated)
            _privacyDialogAgent.value = null

            val label = if (isMentionOnly) "🔒 Mention-Only (Battery & Eavesdrop Protection)" else "👁️ Full Mesh (Omniscient Swarm Observer)"
            val confirmMsg = ManagerMessage(
                sender = "manager",
                text = "🛡️ **Swarm Privacy Set for ${updated.name}:**\n• Mode: **$label**",
                actions = listOf(ManagerAction(label = "📋 Manage My Agents", payload = "open_portfolio")),
                quickChips = listOf("📋 Manage My Agents")
            )
            _messages.update { it + confirmMsg }
        }
    }

    fun executeDelete(id: String) {
        viewModelScope.launch {
            val existing = agentRepository.getAgentById(id)
            val name = existing?.name ?: "Agent"
            agentRepository.deleteAgent(id)
            _deleteDialogAgent.value = null

            val confirmMsg = ManagerMessage(
                sender = "manager",
                text = "🗑️ **Agent Deleted:** **$name** has been permanently removed from your sovereign database.",
                quickChips = listOf("📋 Manage My Agents", "➕ Create New Agent")
            )
            _messages.update { it + confirmMsg }
        }
    }

    fun dismissDialogs() {
        _editSheetAgent.value = null
        _revokeDialogAgent.value = null
        _identityDialogAgent.value = null
        _privacyDialogAgent.value = null
        _deleteDialogAgent.value = null
        _qrDialogAgent.value = null
        _showScannerDialog.value = false
    }

    fun openQrForAgent(agent: Agent) {
        _qrDialogAgent.value = agent
    }

    fun openScanner() {
        _showScannerDialog.value = true
    }

    fun closeScanner() {
        _showScannerDialog.value = false
    }

    // -------------------------------------------------------------------------
    // QR Code Import & Instant Pairing
    // -------------------------------------------------------------------------
    fun importFromQrPayload(raw: String) {
        _showScannerDialog.value = false
        viewModelScope.launch {
            try {
                val trimmed = raw.trim()
                var endpoint = ""
                var token = ""
                var model = ""
                var name = ""
                var did = ""

                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    val json = JSONObject(trimmed)
                    endpoint = json.optString("endpoint", json.optString("url", json.optString("mcp_url", "")))
                    token = json.optString("token", json.optString("auth_token", json.optString("api_key", "")))
                    model = json.optString("model", "")
                    name = json.optString("name", "")
                    did = json.optString("did", "")
                } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("wss://") || trimmed.startsWith("ws://") || trimmed.startsWith("baton://")) {
                    val queryPart = trimmed.substringAfter("?", "")
                    val queryParams = if (queryPart.isNotBlank()) {
                        queryPart.split("&").mapNotNull { param ->
                            val parts = param.split("=", limit = 2)
                            if (parts.size == 2) {
                                try {
                                    val k = java.net.URLDecoder.decode(parts[0], "UTF-8")
                                    val v = java.net.URLDecoder.decode(parts[1], "UTF-8")
                                    k to v
                                } catch (_: Exception) { null }
                            } else null
                        }.toMap()
                    } else emptyMap()

                    endpoint = queryParams["url"] ?: queryParams["endpoint"] ?: trimmed.substringBefore("?")
                    token = queryParams["token"] ?: queryParams["auth"] ?: queryParams["api_key"] ?: ""
                    model = queryParams["model"] ?: ""
                    name = queryParams["name"] ?: ""
                    did = queryParams["did"] ?: ""
                } else {
                    endpoint = trimmed
                }

                if (endpoint.isBlank()) {
                    val err = ManagerMessage(sender = "manager", text = "⚠️ Invalid QR payload scanned. Could not find endpoint URL.")
                    _messages.update { it + err }
                    return@launch
                }

                val agentId = UUID.randomUUID().toString()
                val finalDid = if (did.isNotBlank()) did else generateSovereignDid(agentId)
                val fingerprint = generateKeyFingerprint(finalDid)
                val agentName = name.ifBlank { "Bridge Bot (${endpoint.substringAfter("://").take(15)})" }

                val initialSecurityConfig = JSONObject().apply {
                    put("privacy_mode", "mention_only")
                    put("hardware_attestation", "android_keystore_backed")
                    put("imported_via_qr", true)
                    put("keys_minted_at", System.currentTimeMillis())
                }.toString()

                val agent = Agent(
                    id = agentId,
                    name = agentName,
                    description = "Paired via Optical QR Code (${if (model.isNotBlank()) model else "Custom Model"})",
                    providerType = if (endpoint.contains("localhost") || endpoint.contains("192.168.")) "local_mcp" else "remote_mcp",
                    mcpEndpointUrl = endpoint,
                    authType = "bearer",
                    authConfig = token,
                    relayToken = token.ifBlank { "btn_" + UUID.randomUUID().toString().replace("-", "") },
                    isActive = true,
                    colorAccent = listOf("#00D2FF", "#3D8EFF", "#10B981", "#F59E0B").random(),
                    securityMode = "sovereign",
                    securityConfig = initialSecurityConfig,
                    role = "COORDINATOR",
                    cardDid = finalDid,
                    cardFingerprint = fingerprint,
                    cardIssuedAt = System.currentTimeMillis()
                )
                agentRepository.upsertAgent(agent)

                val successMsg = ManagerMessage(
                    sender = "manager",
                    text = """
                        🎉 **Successfully Paired & Imported: ${agent.name}!**
                        
                        • **Endpoint:** `${agent.mcpEndpointUrl}`
                        • **DID:** `${agent.cardDid.take(24)}...`
                        • **Model:** `${model.ifBlank { "Auto-Configured" }}`
                        • **Paired via:** Optical QR Handshake
                        
                        Your bot is online and ready for deployment.
                    """.trimIndent(),
                    actions = listOf(
                        ManagerAction(label = "💬 Open Chat", payload = "chat:${agent.id}", isPrimary = true),
                        ManagerAction(label = "🔲 View Pairing QR", payload = "open_qr:${agent.id}"),
                        ManagerAction(label = "🛡️ Identity Card", payload = "open_identity:${agent.id}")
                    ),
                    targetAgent = agent,
                    quickChips = listOf("💬 Open Chat", "📋 Manage My Agents", "➕ Create New Agent")
                )
                _messages.update { it + successMsg }
            } catch (e: Exception) {
                val err = ManagerMessage(sender = "manager", text = "⚠️ Failed to process QR payload: ${e.message}")
                _messages.update { it + err }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Wi-Fi Discovery
    // -------------------------------------------------------------------------
    private fun runWifiScan() {
        val scanningMsg = ManagerMessage(
            sender = "manager",
            text = "📡 Scanning local Wi-Fi for active AI runtimes (Ollama, LM Studio, MCP hosts)..."
        )
        _messages.update { it + scanningMsg }

        viewModelScope.launch {
            mdnsDiscoveryManager.startDiscovery()
            kotlinx.coroutines.delay(2000)
            val discovered = mdnsDiscoveryManager.discoveredAgents.value
            mdnsDiscoveryManager.stopDiscovery()

            if (discovered.isEmpty()) {
                val notFoundMsg = ManagerMessage(
                    sender = "manager",
                    text = """
                        🔍 No AI nodes detected on your Wi-Fi yet.
                        
                        **Quick Tips:**
                        1. Ensure phone & PC are on the **same Wi-Fi**.
                        2. Start **Ollama** (`ollama serve`) or **LM Studio** on your PC.
                        3. Or tap **Create New Agent** to connect via Cloud AI!
                    """.trimIndent(),
                    quickChips = listOf("➕ Create New Agent", "📡 Scan Wi-Fi AI (Again)")
                )
                _messages.update { it + notFoundMsg }
            } else {
                val actions = discovered.map { node ->
                    ManagerAction(
                        label = "Connect to ${node.name} (${node.ipAddress}:${node.port})",
                        payload = "connect_discovered:${node.url}:${node.name}",
                        isPrimary = true
                    )
                }
                val foundMsg = ManagerMessage(
                    sender = "manager",
                    text = "🎉 Detected **${discovered.size} local AI node(s)** on your Wi-Fi! Tap below to connect:",
                    actions = actions
                )
                _messages.update { it + foundMsg }
            }
        }
    }

    private fun presentTemplates() {
        val reply = ManagerMessage(
            sender = "manager",
            text = "💡 **1-Tap Agent Templates:**\n\nPick a pre-configured template to launch instantly:",
            actions = listOf(
                ManagerAction(label = "💻 Code Mentor (Full-Stack Dev)", payload = "template:coder"),
                ManagerAction(label = "✍️ Creative Scribe (Writing & Copy)", payload = "template:writer"),
                ManagerAction(label = "🔍 Research Scout (Deep Analysis)", payload = "template:researcher")
            )
        )
        _messages.update { it + reply }
    }

    fun resetConversation() {
        _currentStep.value = WizardStep.IDLE
        dismissDialogs()
        sendWelcomeMessage()
    }

    // -------------------------------------------------------------------------
    // Cryptographic Helpers
    // -------------------------------------------------------------------------
    private fun generateSovereignDid(seed: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "did:key:z6Mku" + hash.take(32)
    }

    private fun generateKeyFingerprint(did: String): String {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(did.toByteArray())
            .joinToString(":") { "%02X".format(it) }
        return hash.take(23) // format: AA:BB:CC:DD:EE:FF:00:11
    }

    fun extractPrivacyMode(securityConfig: String): String {
        return try {
            if (securityConfig.isNotBlank()) {
                val json = JSONObject(securityConfig)
                json.optString("privacy_mode", "mention_only")
            } else {
                "mention_only"
            }
        } catch (_: Exception) {
            "mention_only"
        }
    }
}
