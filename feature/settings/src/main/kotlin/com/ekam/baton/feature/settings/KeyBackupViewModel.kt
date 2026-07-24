package com.ekam.baton.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.network.repository.VaultRepository
import com.ekam.baton.core.network.security.ConnectionSecurityManager
import com.ekam.baton.core.network.security.IdentityKeyManager
import com.ekam.baton.core.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class KeyBackupViewModel(
    private val agentRepository: AgentRepository,
    private val vaultRepository: VaultRepository,
    private val connectionSecurityManager: ConnectionSecurityManager,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState

    fun exportVault(password: String) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            try {
                val agents = agentRepository.getAllAgents().first()
                val jsonArray = JSONArray()
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
                    jsonArray.put(obj)
                }

                val payloadBytes = jsonArray.toString().toByteArray(Charsets.UTF_8)
                val vaultDataString = connectionSecurityManager.encryptForBackup(payloadBytes, password.toCharArray())

                val endpointUrl = appPreferences.backendUrl.first()
                val secret = appPreferences.jwtSecret.first()
                val authHeader = if (secret.isNotBlank()) "Bearer $secret" else null
                val clientId = IdentityKeyManager.getPublicKeyHex()

                val result = vaultRepository.uploadVault(endpointUrl, authHeader, clientId, vaultDataString)
                if (result.isSuccess) {
                    _uiState.value = BackupUiState.Success("Vault exported successfully")
                } else {
                    _uiState.value = BackupUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(e.message ?: "Backup failed")
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
                    val jsonArray = JSONArray(String(decryptedBytes, Charsets.UTF_8))
                    
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
                    }
                    _uiState.value = BackupUiState.Success("Vault imported successfully")
                } else {
                    _uiState.value = BackupUiState.Error(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(e.message ?: "Import failed. Wrong password?")
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
