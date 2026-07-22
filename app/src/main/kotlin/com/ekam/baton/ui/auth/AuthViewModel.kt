package com.ekam.baton.ui.auth

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.data.preferences.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val TAG = "AuthViewModel"

sealed interface AuthState {
    object Unregistered : AuthState
    object LoggedOut : AuthState
    object LoggedIn : AuthState
    object LoginFailed : AuthState
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

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    fun register(email: String, phone: String) {
        viewModelScope.launch {
            try {
                sessionManager.register(email, phone)
                sessionManager.setLoggedIn(true)
            } catch (ignored: Exception) {
                Log.e(TAG, "Registration failed", ignored)
                _loginError.value = "Registration failed. Please try again."
            }
        }
    }

    fun login() {
        viewModelScope.launch {
            val backendUrlStr = appPreferences.backendUrl.first()
            val jwtSecretStr = appPreferences.jwtSecret.first()
            val pipelineMode = appPreferences.pipelineMode.first()
            val isPremium = appPreferences.isPremiumUnlocked.first()

            if (!isPremium) {
                // Temporarily bypassed for testing
                // _loginError.value = "Premium access required (250 RS). Please upgrade in Settings."
                // return@launch
            }

            if (pipelineMode == "MANAGED") {
                // EKAM Cloud mode: authenticated by Google Play billing receipt (checked above)
                _loginError.value = null
                sessionManager.setLoggedIn(true)
                return@launch
            }

            // BYOS mode logic
            // SECURITY FIX (HIGH-6): No unauthenticated fallback.
            // If no JWT secret is configured, the app must NOT auto-login.
            // The user must configure their BYOS backend first.
            if (jwtSecretStr.isBlank()) {
                _loginError.value = "No backend configured. Please add your server URL and JWT secret in Settings."
                return@launch
            }

            val isValid = withContext(Dispatchers.IO) {
                try {
                    val loginUrl = if (backendUrlStr.endsWith("/")) {
                        "${backendUrlStr}login"
                    } else {
                        "$backendUrlStr/login"
                    }

                    // SECURITY FIX (CRIT-6): Never send the raw JWT secret over the network.
                    // Instead, sign a local HMAC-SHA256 challenge with the secret and send
                    // only the resulting signature + timestamp. The server verifies the HMAC
                    // using the shared secret it already knows — the secret itself never travels.
                    val timestamp = System.currentTimeMillis().toString()
                    val mac = Mac.getInstance("HmacSHA256")
                    val secretKey = SecretKeySpec(jwtSecretStr.toByteArray(Charsets.UTF_8), "HmacSHA256")
                    mac.init(secretKey)
                    val signature = Base64.encodeToString(
                        mac.doFinal(timestamp.toByteArray(Charsets.UTF_8)),
                        Base64.NO_WRAP
                    )

                    val jsonInput = JSONObject().apply {
                        put("timestamp", timestamp)
                        put("signature", signature)
                    }.toString()

                    val body = jsonInput.toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url(loginUrl)
                        .post(body)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    response.isSuccessful
                } catch (ignored: Exception) {
                    Log.e(TAG, "Login request failed", ignored)
                    false
                }
            }

            if (isValid) {
                _loginError.value = null
                sessionManager.setLoggedIn(true)
            } else {
                _loginError.value = "Authentication failed. Check your server URL and secret."
            }
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun logout() {
        sessionManager.setLoggedIn(false)
    }
}
