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

import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator

sealed class AgentsUiEvent {
    data class ShowError(val message: String) : AgentsUiEvent()
    data class LaunchBrowser(val url: String) : AgentsUiEvent()
}

class AgentsViewModel(
    private val agentRepository: AgentRepository,
    private val tunnelValidator: TunnelEndpointValidator,
    private val securityManager: com.ekam.baton.core.network.security.ConnectionSecurityManager,
    private val mdnsDiscoveryManager: com.ekam.baton.core.network.mdns.MdnsDiscoveryManager
) : ViewModel() {

    private val _uiEvents = Channel<AgentsUiEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    fun generateClientKeys(): com.ekam.baton.core.network.security.ClientKeyDetails {
        return securityManager.generateClientKeys()
    }

    val discoveredAgents: StateFlow<List<com.ekam.baton.core.network.mdns.DiscoveredAgent>> = mdnsDiscoveryManager.discoveredAgents

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
                agentRepository.upsertAgent(agent)
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
