package com.ekam.baton.feature.agents.tunnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator
import com.ekam.baton.core.network.tunnel.TunnelValidationResult

class TunnelSetupViewModel(
    private val endpointValidator: TunnelEndpointValidator,
    private val localNetworkPolicyProvider: com.ekam.baton.core.network.security.LocalNetworkPolicyProvider
) : ViewModel() {

    private val _validationResult = MutableStateFlow<TunnelValidationResult?>(null)
    val validationResult: StateFlow<TunnelValidationResult?> = _validationResult

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating

    fun validateUrl(url: String) {
        viewModelScope.launch {
            _isValidating.value = true
            val host = try { java.net.URL(url).host } catch (e: Exception) { null }
            if (host != null) localNetworkPolicyProvider.allowDuringPairing(host)
            _validationResult.value = endpointValidator.validateEndpoint(url)
            if (host != null) localNetworkPolicyProvider.removePairingHost(host)
            _isValidating.value = false
        }
    }
}
