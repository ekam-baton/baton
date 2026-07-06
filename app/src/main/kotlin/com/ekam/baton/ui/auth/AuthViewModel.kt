package com.ekam.baton.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.preferences.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ekam.baton.core.data.preferences.AppPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject

sealed interface AuthState {
    object Unregistered : AuthState
    object LoggedOut : AuthState
    object LoggedIn : AuthState
}

class AuthViewModel(
    private val sessionManager: SessionManager,
    private val appPreferences: AppPreferences,
    private val httpClient: OkHttpClient
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
            val backendUrlStr = appPreferences.backendUrl.first()
            val jwtSecretStr = appPreferences.jwtSecret.first()
            
            if (jwtSecretStr.isNotBlank()) {
                val isValid = withContext(Dispatchers.IO) {
                    try {
                        val loginUrl = if (backendUrlStr.endsWith("/")) "${backendUrlStr}login" else "${backendUrlStr}/login"
                        val jsonInput = JSONObject().apply {
                            put("secret", jwtSecretStr)
                        }.toString()
                        
                        val body = RequestBody.create("application/json".toMediaTypeOrNull(), jsonInput)
                        val request = Request.Builder()
                            .url(loginUrl)
                            .post(body)
                            .build()
                        
                        val response = httpClient.newCall(request).execute()
                        response.isSuccessful
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                
                if (isValid) {
                    sessionManager.setLoggedIn(true)
                } else {
                    // Could expose a UI error state here, but for now just fail silently or throw
                }
            } else {
                // Local only fallback
                sessionManager.setLoggedIn(true)
            }
        }
    }

    fun logout() {
        sessionManager.setLoggedIn(false)
    }
}
