package com.ekam.baton.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.Memory
import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.repository.MemoryRepository
import com.ekam.baton.core.data.util.AuditCryptoUtils
import com.ekam.baton.core.network.repository.VaultRepository
import com.ekam.baton.core.network.security.ConnectionSecurityManager
import com.ekam.baton.core.network.security.IdentityKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class KeyBackupViewModel(
    private val agentRepository: AgentRepository,
    private val vaultRepository: VaultRepository,
    private val connectionSecurityManager: ConnectionSecurityManager,
    private val appPreferences: AppPreferences,
    private val memoryRepository: MemoryRepository,
    private val auditDao: AuditDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState

    val cloudSyncActive: StateFlow<Boolean> = appPreferences.cloudSyncActive.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    val cloudSyncMemories: StateFlow<Boolean> = appPreferences.cloudSyncMemories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true
    )

    val cloudSyncKeys: StateFlow<Boolean> = appPreferences.cloudSyncKeys.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    val lastSyncTimestamp: StateFlow<Long> = appPreferences.lastSyncTimestamp.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 0L
    )

    fun setCloudSyncActive(active: Boolean) {
        viewModelScope.launch {
            appPreferences.setCloudSyncActive(active)
        }
    }

    fun setCloudSyncMemories(sync: Boolean) {
        viewModelScope.launch {
            appPreferences.setCloudSyncMemories(sync)
        }
    }

    fun setCloudSyncKeys(sync: Boolean) {
        viewModelScope.launch {
            appPreferences.setCloudSyncKeys(sync)
        }
    }

    fun clearStatus() {
        _uiState.value = BackupUiState.Idle
    }

    fun exportVault(password: String) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            try {
                val agents = agentRepository.getAllAgents().first()
                val agentsArray = JSONArray()
                agents.forEach { agent ->
                    val obj = JSONObject().apply {
                        put("id", agent.id)
                        put("name", agent.name)
                        put("description", agent.description)
                        put("mcpEndpointUrl", agent.mcpEndpointUrl)
                        put("authType", agent.authType)
                        put("authConfig", agent.authConfig)
                        put("colorAccent", agent.colorAccent)
                        put("securityMode", agent.securityMode)
                        put("securityConfig", agent.securityConfig)
                        put("relayUrl", agent.relayUrl ?: "")
                        put("relayToken", agent.relayToken ?: "")
                        put("ownerId", agent.ownerId ?: "")
                    }
                    agentsArray.put(obj)
                }

                val memoriesArray = JSONArray()
                if (cloudSyncMemories.value) {
                    val memories = memoryRepository.getAllMemories().first()
                    memories.forEach { mem ->
                        val obj = JSONObject().apply {
                            put("id", mem.id)
                            put("layer", mem.layer)
                            put("agentId", mem.agentId ?: "")
                            put("conversationId", mem.conversationId ?: "")
                            put("title", mem.title)
                            put("content", mem.content)
                            put("createdAt", mem.createdAt)
                            put("lastAccessedAt", mem.lastAccessedAt)
                            put("relevanceScore", mem.relevanceScore.toDouble())
                            put("tags", mem.tags)
                            put("isActive", mem.isActive)
                        }
                        memoriesArray.put(obj)
                    }
                }

                val rootEnvelope = JSONObject().apply {
                    put("version", 2)
                    put("timestamp", System.currentTimeMillis())
                    put("agents", agentsArray)
                    put("memories", memoriesArray)
                    if (cloudSyncKeys.value) {
                        put("identityKeyHex", IdentityKeyManager.getPublicKeyHex())
                    }
                }

                val payloadBytes = rootEnvelope.toString().toByteArray(Charsets.UTF_8)
                val vaultDataString = connectionSecurityManager.encryptForBackup(payloadBytes, password.toCharArray())

                val endpointUrl = appPreferences.backendUrl.first()
                val secret = appPreferences.jwtSecret.first()
                val authHeader = if (secret.isNotBlank()) "Bearer $secret" else null
                val clientId = IdentityKeyManager.getPublicKeyHex()

                val result = vaultRepository.uploadVault(endpointUrl, authHeader, clientId, vaultDataString)
                if (result.isSuccess) {
                    val now = System.currentTimeMillis()
                    appPreferences.setLastSyncTimestamp(now)

                    // Record cryptographic audit log
                    val prevLog = auditDao.getLastAuditLog()
                    val prevHash = prevLog?.hash ?: "0".repeat(64)
                    val payload = """{"action":"EXPORT_VAULT","agents":${agents.size},"memories":${memoriesArray.length()},"timestamp":$now}"""
                    val hash = AuditCryptoUtils.generateHash(payload, prevHash)

                    auditDao.insertAuditLog(
                        AuditLogEntity(
                            entityName = "CryptographicVault",
                            entityId = clientId,
                            action = "EXPORT",
                            deviceId = "local_device",
                            payloadJson = payload,
                            previousHash = prevHash,
                            hash = hash
                        )
                    )

                    _uiState.value = BackupUiState.Success("Cryptographic Vault successfully exported & synchronized (${agents.size} agents, ${memoriesArray.length()} memories).")
                } else {
                    _uiState.value = BackupUiState.Error(result.exceptionOrNull()?.message ?: "Unknown vault upload error")
                }
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(e.message ?: "Vault export failed")
            }
        }
    }

    fun importVault(password: String) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            try {
                val endpointUrl = appPreferences.backendUrl.first()
                val secret = appPreferences.jwtSecret.first()
                val authHeader = if (secret.isNotBlank()) "Bearer $secret" else null
                val clientId = IdentityKeyManager.getPublicKeyHex()

                val result = vaultRepository.downloadVault(endpointUrl, authHeader, clientId)
                if (result.isSuccess) {
                    val vaultDataString = result.getOrNull()!!
                    val decryptedBytes = connectionSecurityManager.decryptFromBackup(vaultDataString, password.toCharArray())
                    val rawDecrypted = String(decryptedBytes, Charsets.UTF_8)

                    var restoredAgentCount = 0
                    var restoredMemoryCount = 0

                    if (rawDecrypted.trimStart().startsWith("{")) {
                        // Version 2 Envelope
                        val root = JSONObject(rawDecrypted)
                        val agentsArray = root.optJSONArray("agents") ?: JSONArray()
                        for (i in 0 until agentsArray.length()) {
                            val obj = agentsArray.getJSONObject(i)
                            val agent = Agent(
                                id = obj.getString("id"),
                                name = obj.getString("name"),
                                description = obj.getString("description"),
                                mcpEndpointUrl = obj.getString("mcpEndpointUrl"),
                                authType = obj.getString("authType"),
                                authConfig = obj.getString("authConfig"),
                                colorAccent = obj.getString("colorAccent"),
                                securityMode = obj.getString("securityMode"),
                                securityConfig = obj.getString("securityConfig"),
                                relayUrl = obj.optString("relayUrl", "").takeIf { it.isNotBlank() },
                                relayToken = obj.optString("relayToken", "").takeIf { it.isNotBlank() },
                                ownerId = obj.optString("ownerId", "").takeIf { it.isNotBlank() }
                            )
                            agentRepository.upsertAgent(agent)
                            restoredAgentCount++
                        }

                        val memoriesArray = root.optJSONArray("memories") ?: JSONArray()
                        for (i in 0 until memoriesArray.length()) {
                            val obj = memoriesArray.getJSONObject(i)
                            val memory = Memory(
                                id = obj.getString("id"),
                                layer = obj.optString("layer", "working"),
                                agentId = obj.optString("agentId", "").takeIf { it.isNotBlank() },
                                conversationId = obj.optString("conversationId", "").takeIf { it.isNotBlank() },
                                title = obj.getString("title"),
                                content = obj.getString("content"),
                                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                                lastAccessedAt = obj.optLong("lastAccessedAt", System.currentTimeMillis()),
                                relevanceScore = obj.optDouble("relevanceScore", 1.0).toFloat(),
                                tags = obj.optString("tags", "[]"),
                                isActive = obj.optBoolean("isActive", true)
                            )
                            memoryRepository.upsertMemory(memory)
                            restoredMemoryCount++
                        }
                    } else {
                        // Legacy Version 1 Array
                        val jsonArray = JSONArray(rawDecrypted)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val agent = Agent(
                                id = obj.getString("id"),
                                name = obj.getString("name"),
                                description = obj.getString("description"),
                                mcpEndpointUrl = obj.getString("mcpEndpointUrl"),
                                authType = obj.getString("authType"),
                                authConfig = obj.getString("authConfig"),
                                colorAccent = obj.getString("colorAccent"),
                                securityMode = obj.getString("securityMode"),
                                securityConfig = obj.getString("securityConfig"),
                                relayUrl = obj.optString("relayUrl", "").takeIf { it.isNotBlank() },
                                relayToken = obj.optString("relayToken", "").takeIf { it.isNotBlank() },
                                ownerId = obj.optString("ownerId", "").takeIf { it.isNotBlank() }
                            )
                            agentRepository.upsertAgent(agent)
                            restoredAgentCount++
                        }
                    }

                    val now = System.currentTimeMillis()
                    appPreferences.setLastSyncTimestamp(now)

                    // Record cryptographic audit log
                    val prevLog = auditDao.getLastAuditLog()
                    val prevHash = prevLog?.hash ?: "0".repeat(64)
                    val payload = """{"action":"IMPORT_VAULT","agents":$restoredAgentCount,"memories":$restoredMemoryCount,"timestamp":$now}"""
                    val hash = AuditCryptoUtils.generateHash(payload, prevHash)

                    auditDao.insertAuditLog(
                        AuditLogEntity(
                            entityName = "CryptographicVault",
                            entityId = clientId,
                            action = "IMPORT",
                            deviceId = "local_device",
                            payloadJson = payload,
                            previousHash = prevHash,
                            hash = hash
                        )
                    )

                    _uiState.value = BackupUiState.Success("Vault restored: $restoredAgentCount agent(s) and $restoredMemoryCount memory items successfully decrypted.")
                } else {
                    _uiState.value = BackupUiState.Error(result.exceptionOrNull()?.message ?: "Unknown vault download error")
                }
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(e.message ?: "Vault decryption failed. Please verify your passphrase.")
            }
        }
    }
}

sealed class BackupUiState {
    object Idle : BackupUiState()
    object Loading : BackupUiState()
    data class Success(val message: String) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}
