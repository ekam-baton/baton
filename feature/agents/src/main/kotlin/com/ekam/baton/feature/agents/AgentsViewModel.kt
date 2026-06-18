package com.ekam.baton.feature.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.repository.AgentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.ekam.baton.core.network.auth.OAuthFlowManager
import com.ekam.baton.core.network.auth.OAuthConfig

import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator

@HiltViewModel
class AgentsViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val oauthFlowManager: OAuthFlowManager,
    private val tunnelValidator: TunnelEndpointValidator,
    private val securityManager: com.ekam.baton.core.network.security.ConnectionSecurityManager
) : ViewModel() {

    fun generateClientKeys(): com.ekam.baton.core.network.security.ClientKeyDetails {
        return securityManager.generateClientKeys()
    }

    private val _isLoading = kotlinx.coroutines.flow.MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    val agents: StateFlow<List<AgentEntity>> = agentRepository.getAllAgents()
        .onEach { _isLoading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun getTunnelValidator() = tunnelValidator

    fun addAgent(agent: AgentEntity) {
        viewModelScope.launch {
            agentRepository.upsertAgent(agent)
        }
    }

    fun updateAgent(agent: AgentEntity) {
        viewModelScope.launch {
            agentRepository.upsertAgent(agent)
        }
    }

    fun deleteAgent(id: String) {
        viewModelScope.launch {
            agentRepository.deleteAgent(id)
        }
    }

    fun startOAuthFlow(agentId: String, config: OAuthConfig): String {
        return oauthFlowManager.buildAuthUrlAndStartFlow(agentId, config)
    }

    fun launchAuthBrowser(context: android.content.Context, authUrl: String) {
        oauthFlowManager.launchAuthBrowser(context, authUrl)
    }
}
