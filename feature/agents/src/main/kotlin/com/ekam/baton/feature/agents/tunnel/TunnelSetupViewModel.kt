package com.ekam.baton.feature.agents.tunnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ekam.baton.core.network.tunnel.TunnelEndpointValidator
import com.ekam.baton.core.network.tunnel.TunnelValidationResult

class TunnelSetupViewModel(
    private val endpointValidator: TunnelEndpointValidator
) : ViewModel() {

    private val _validationResult = MutableStateFlow<TunnelValidationResult?>(null)
    val validationResult: StateFlow<TunnelValidationResult?> = _validationResult

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating

    fun validateUrl(url: String) {
        viewModelScope.launch {
            _isValidating.value = true
            _validationResult.value = endpointValidator.validateEndpoint(url)
            _isValidating.value = false
        }
    }
}
