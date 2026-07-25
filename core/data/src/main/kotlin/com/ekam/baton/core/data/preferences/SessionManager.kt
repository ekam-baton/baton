package com.ekam.baton.core.data.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager constructor(
    private val appPreferences: AppPreferences
) {
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    val isRegistered: Flow<Boolean> = appPreferences.isRegistered

    fun setLoggedIn(loggedIn: Boolean) {
        _isLoggedIn.value = loggedIn
    }

    suspend fun register(email: String, phone: String, consentTimestamp: Long = 0L, policyVersion: String = "", region: String = "Global") {
        appPreferences.registerUser(email, phone, consentTimestamp, policyVersion, region)
        setLoggedIn(true)
    }

    suspend fun clearRegistration() {
        appPreferences.clearRegistration()
        setLoggedIn(false)
    }
}
