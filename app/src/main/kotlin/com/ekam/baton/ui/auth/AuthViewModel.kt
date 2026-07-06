package com.ekam.baton.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.preferences.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AuthState {
    object Unregistered : AuthState
    object LoggedOut : AuthState
    object LoggedIn : AuthState
}

class AuthViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    val authState: StateFlow<AuthState> = combine(
        sessionManager.isRegistered,
        sessionManager.isLoggedIn
    ) { isRegistered, isLoggedIn ->
        when {
            !isRegistered -> AuthState.Unregistered
            !isLoggedIn -> AuthState.LoggedOut
            else -> AuthState.LoggedIn
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthState.LoggedOut
    )

    fun register(email: String, phone: String) {
        viewModelScope.launch {
            try {
                sessionManager.register(email, phone)
                sessionManager.setLoggedIn(true)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback or error handling
            }
        }
    }

    fun login() {
        viewModelScope.launch {
            sessionManager.setLoggedIn(true)
        }
    }

    fun logout() {
        sessionManager.setLoggedIn(false)
    }
}
