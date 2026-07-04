package com.ekam.baton.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.preferences.SessionManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    object Unregistered : AuthState
    object LoggedOut : AuthState
    object LoggedIn : AuthState
}

class AuthViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    val authState: StateFlow<AuthState> = combine(
        sessionManager.isRegistered,
        sessionManager.isLoggedIn
    ) { isRegistered, isLoggedIn ->
        when {
            !isRegistered -> AuthState.Unregistered
            !isLoggedIn -> AuthState.LoggedOut
            auth.currentUser == null -> AuthState.LoggedOut // Enforce Firebase Auth
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
                // Use anonymous sign-in to establish a real Firebase session
                auth.signInAnonymously().await()
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
            if (auth.currentUser == null) {
                try {
                    auth.signInAnonymously().await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            sessionManager.setLoggedIn(true)
        }
    }

    fun logout() {
        auth.signOut()
        sessionManager.setLoggedIn(false)
    }
}
