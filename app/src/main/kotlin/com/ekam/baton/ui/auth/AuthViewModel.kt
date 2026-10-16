package com.ekam.baton.ui.auth

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
    private val httpClient: OkHttpClient,
    private val securityManager: com.ekam.baton.core.network.security.ConnectionSecurityManager,
    private val wipeDataManager: com.ekam.baton.core.data.repository.WipeDataManager? = null,
    private val connectionPoolManager: com.ekam.baton.core.network.tunnel.ConnectionPoolManager? = null
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

    fun register(email: String, phone: String, password: String, consentTimestamp: Long, policyVersion: String, region: String) {
        viewModelScope.launch {
            try {
                val backendUrlStr = appPreferences.backendUrl.first()
                val registerUrl = if (backendUrlStr.endsWith("/")) {
                    "${backendUrlStr}auth/register"
                } else {
                    "$backendUrlStr/auth/register"
                }

                val jsonInput = JSONObject().apply {
                    put("email", email)
                    if (phone.isNotBlank()) put("phone_number", phone)
                    put("password", password)

                    val clientKeys = securityManager.generateClientKeys()
                    val privateKeyBytes = securityManager.decryptPrivateKey(
                        clientKeys.encryptedPrivateKeyBase64,
                        clientKeys.privateKeyIvBase64
                    )
                    val backupPayload = CryptoHelper.encryptBackupKey(password, privateKeyBytes)

                    put("backup_payload", backupPayload)
                    put("public_key", clientKeys.publicKeyHex)
                }.toString()

                val body = jsonInput.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(registerUrl)
                    .post(body)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseBody)
                    val token = jsonResponse.optString("token", "")
                    val refreshToken = jsonResponse.optString("refresh_token", "")

                    if (token.isNotBlank()) {
                        appPreferences.setJwtSecret(token)
                    }
                    if (refreshToken.isNotBlank()) {
                        appPreferences.setRefreshToken(refreshToken)
                    }

                    val subscriptionStatus = jsonResponse.optString("subscription_status", "free")
                    viewModelScope.launch {
                        appPreferences.setPremiumUnlocked(subscriptionStatus == "premium")
                    }

                    sessionManager.register(email, phone, consentTimestamp, policyVersion, region)
                    sessionManager.setLoggedIn(true)
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Registration failed with status ${response.code}: $errorBody")
                    _loginError.value = "Registration failed. Server error."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration exception", e)
                _loginError.value = "Network error. Please check your connection."
            }
        }
    }

    fun cloudLogin(email: String, password: String) {
        viewModelScope.launch {
            try {
                val backendUrlStr = appPreferences.backendUrl.first()
                val loginUrl = if (backendUrlStr.endsWith("/")) {
                    "${backendUrlStr}auth/login"
                } else {
                    "$backendUrlStr/auth/login"
                }

                val jsonInput = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }.toString()

                val body = jsonInput.toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(loginUrl)
                    .post(body)
                    .build()

                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseBody)
                    val token = jsonResponse.optString("token", "")
                    val refreshToken = jsonResponse.optString("refresh_token", "")

                    if (token.isNotBlank()) {
                        appPreferences.setJwtSecret(token)
                    }
                    if (refreshToken.isNotBlank()) {
                        appPreferences.setRefreshToken(refreshToken)
                    }

                    val subscriptionStatus = jsonResponse.optString("subscription_status", "free")
                    viewModelScope.launch {
                        appPreferences.setPremiumUnlocked(subscriptionStatus == "premium")
                    }

                    // Note: Region and Consent are not returned by login, so we use defaults or skip
                    sessionManager.register(email, "", 0L, "", "Global")
                    sessionManager.setLoggedIn(true)
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Login failed with status ${response.code}: $errorBody")
                    _loginError.value = "Login failed. Check your credentials."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login exception", e)
                _loginError.value = "Network error. Please check your connection."
            }
        }
    }

    fun login() {
        sessionManager.setLoggedIn(true)
    }

    fun verifyPin(
        pin: String,
        onSuccess: () -> Unit,
        onDuress: () -> Unit,
        onFailed: () -> Unit
    ) {
        viewModelScope.launch {
            val duress = appPreferences.duressPin.firstOrNull()
            if (!duress.isNullOrBlank() && pin == duress) {
                connectionPoolManager?.disconnectAll()
                wipeDataManager?.emergencyCryptographicShred()
                appPreferences.purgeAllSecurityPreferences()
                sessionManager.clearRegistration()
                sessionManager.setLoggedIn(true)
                onDuress()
                return@launch
            }

            val storedPin = appPreferences.appLockPin.firstOrNull()
            if (storedPin.isNullOrBlank() || pin == storedPin) {
                sessionManager.setLoggedIn(true)
                onSuccess()
            } else {
                onFailed()
            }
        }
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun logout() {
        sessionManager.setLoggedIn(false)
        viewModelScope.launch {
            appPreferences.setJwtSecret("")
        }
    }
}
