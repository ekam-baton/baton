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

    suspend fun register(email: String, phone: String) {
        appPreferences.registerUser(email, phone)
        setLoggedIn(true)
    }

    suspend fun clearRegistration() {
        appPreferences.clearRegistration()
        setLoggedIn(false)
    }
}
