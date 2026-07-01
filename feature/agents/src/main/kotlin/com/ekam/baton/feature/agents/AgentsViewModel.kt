package com.ekam.baton.feature.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.repository.AgentRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first

import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator

sealed class AgentsUiEvent {
    data class ShowError(val message: String) : AgentsUiEvent()
    data class LaunchBrowser(val url: String) : AgentsUiEvent()
}

class AgentsViewModel(
    private val agentRepository: AgentRepository,
    private val tunnelValidator: TunnelEndpointValidator,
    private val securityManager: com.ekam.baton.core.network.security.ConnectionSecurityManager,
    private val mdnsDiscoveryManager: com.ekam.baton.core.network.mdns.MdnsDiscoveryManager,
    private val mcpMessageSender: com.ekam.baton.core.network.mcp.McpMessageSender
) : ViewModel() {

    private val _uiEvents = Channel<AgentsUiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    fun generateClientKeys(): com.ekam.baton.core.network.security.ClientKeyDetails {
        return securityManager.generateClientKeys()
    }

    val discoveredAgents: StateFlow<List<com.ekam.baton.core.network.mdns.DiscoveredAgent>> = kotlinx.coroutines.flow.combine(
        mdnsDiscoveryManager.discoveredAgents,
        agentRepository.getAllAgents()
    ) { discovered, saved ->
        discovered.filter { d ->
            saved.none { s -> 
                val normalizedSaved = s.mcpEndpointUrl.trim().removeSuffix("/")
                val normalizedDiscovered = d.url.trim().removeSuffix("/")
                normalizedSaved.equals(normalizedDiscovered, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        mdnsDiscoveryManager.startDiscovery()
    }

    override fun onCleared() {
        super.onCleared()
        mdnsDiscoveryManager.stopDiscovery()
    }

    private val _isLoading = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val agents: StateFlow<List<Agent>> = agentRepository.getAllAgents()
        .onEach { _isLoading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getTunnelValidator() = tunnelValidator

    fun addAgent(agent: Agent) {
        viewModelScope.launch {
            try {
                val savedList = agentRepository.getAllAgents().first()
                val isDuplicate = savedList.any { 
                    it.mcpEndpointUrl.trim().removeSuffix("/").equals(
                        agent.mcpEndpointUrl.trim().removeSuffix("/"), 
                        ignoreCase = true
                    ) 
                }
                if (isDuplicate) {
                    _uiEvents.send(AgentsUiEvent.ShowError("Agent with this endpoint is already saved"))
                    return@launch
                }
                
                // Pair with the agent to exchange keys and get the relay_url
                val keys = securityManager.generateClientKeys()
                val pairResult = mcpMessageSender.pairWithAgent(agent.mcpEndpointUrl, keys.publicKeyHex)
                
                val finalAgent = if (pairResult.isSuccess) {
                    val pair = pairResult.getOrNull()
                    if (pair != null) {
                        agent.copy(relayUrl = pair.first, relayToken = pair.second)
                    } else {
                        agent
                    }
                } else {
                    // It failed to pair, probably not a Baton a2a-router or not reachable.
                    // We still save it, but without a relayUrl.
                    agent
                }

                agentRepository.upsertAgent(finalAgent)
            } catch (e: Exception) {
                _uiEvents.send(AgentsUiEvent.ShowError("Failed to add agent"))
            }
        }
    }

    fun updateAgent(agent: Agent) {
        viewModelScope.launch {
            try {
                agentRepository.upsertAgent(agent)
            } catch (e: Exception) {
                _uiEvents.send(AgentsUiEvent.ShowError("Failed to update agent"))
            }
        }
    }

    fun deleteAgent(id: String) {
        viewModelScope.launch {
            try {
                agentRepository.deleteAgent(id)
            } catch (e: Exception) {
                _uiEvents.send(AgentsUiEvent.ShowError("Failed to delete agent"))
            }
        }
    }

    fun launchAuthBrowser(authUrl: String) {
        viewModelScope.launch {
            _uiEvents.send(AgentsUiEvent.LaunchBrowser(authUrl))
        }
    }
}
