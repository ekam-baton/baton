package com.ekam.baton.feature.agents.tunnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.network.security.LocalNetworkPolicyProvider
import com.ekam.baton.core.network.tunnel.ConnectionPoolManager
import com.ekam.baton.core.network.tunnel.Status
import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator
import com.ekam.baton.core.network.tunnel.TunnelValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URL

data class TunnelHealth(
    val agentId: String,
    val endpointUrl: String,
    val status: Status,
    val latencyMs: Long,
    val checkedAt: Long = System.currentTimeMillis(),
    val serverName: String? = null,
    val error: String? = null
)

class TunnelSetupViewModel(
    private val endpointValidator: TunnelEndpointValidator,
    private val localNetworkPolicyProvider: LocalNetworkPolicyProvider,
    private val agentRepository: AgentRepository,
    private val connectionPoolManager: ConnectionPoolManager
) : ViewModel() {

    private val _validationResult = MutableStateFlow<TunnelValidationResult?>(null)
    val validationResult: StateFlow<TunnelValidationResult?> = _validationResult.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    val agents: StateFlow<List<Agent>> = agentRepository.getAllAgents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _tunnelHealthMap = MutableStateFlow<Map<String, TunnelHealth>>(emptyMap())
    val tunnelHealthMap: StateFlow<Map<String, TunnelHealth>> = _tunnelHealthMap.asStateFlow()

    private val _isProbingAll = MutableStateFlow(false)
    val isProbingAll: StateFlow<Boolean> = _isProbingAll.asStateFlow()

    fun getActiveConnectionCount(): Int = connectionPoolManager.getActiveConnectionCount()

    fun validateUrl(url: String) {
        viewModelScope.launch {
            _isValidating.value = true
            val host = try { URL(url).host } catch (e: Exception) { null }
            if (host != null) localNetworkPolicyProvider.allowDuringPairing(host)
            try {
                _validationResult.value = endpointValidator.validateEndpoint(url)
            } finally {
                if (host != null) localNetworkPolicyProvider.removePairingHost(host)
                _isValidating.value = false
            }
        }
    }

    fun probeSingleEndpoint(agentId: String, url: String) {
        viewModelScope.launch {
            val host = try { URL(url).host } catch (e: Exception) { null }
            if (host != null) localNetworkPolicyProvider.allowDuringPairing(host)

            try {
                val startTime = System.currentTimeMillis()
                val result = endpointValidator.validateEndpoint(url)
                val latency = System.currentTimeMillis() - startTime

                val health = TunnelHealth(
                    agentId = agentId,
                    endpointUrl = url,
                    status = result.status,
                    latencyMs = latency,
                    serverName = result.serverName,
                    error = result.error
                )
                _tunnelHealthMap.value = _tunnelHealthMap.value + (agentId to health)
            } finally {
                if (host != null) localNetworkPolicyProvider.removePairingHost(host)
            }
        }
    }

    fun probeAllTunnelEndpoints() {
        viewModelScope.launch {
            _isProbingAll.value = true
            try {
                val currentAgents = agents.value
                val newMap = _tunnelHealthMap.value.toMutableMap()

                for (agent in currentAgents) {
                    if (agent.mcpEndpointUrl.isNotBlank()) {
                        val host = try { URL(agent.mcpEndpointUrl).host } catch (e: Exception) { null }
                        if (host != null) localNetworkPolicyProvider.allowDuringPairing(host)

                        try {
                            val startTime = System.currentTimeMillis()
                            val result = endpointValidator.validateEndpoint(agent.mcpEndpointUrl)
                            val latency = System.currentTimeMillis() - startTime

                            newMap[agent.id] = TunnelHealth(
                                agentId = agent.id,
                                endpointUrl = agent.mcpEndpointUrl,
                                status = result.status,
                                latencyMs = latency,
                                serverName = result.serverName,
                                error = result.error
                            )
                        } finally {
                            if (host != null) localNetworkPolicyProvider.removePairingHost(host)
                        }
                    }
                }

                _tunnelHealthMap.value = newMap
            } finally {
                _isProbingAll.value = false
            }
        }
    }
}
