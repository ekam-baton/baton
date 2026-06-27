package com.ekam.baton

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.data.preferences.SubscriptionManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val appPreferences: AppPreferences,
    private val subscriptionManager: SubscriptionManager
) : ViewModel() {

    val themeMode: StateFlow<String> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val accentColor: StateFlow<Long> = appPreferences.accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFFECEFF4)

    val isAccessGranted: StateFlow<Boolean> = combine(
        appPreferences.trialStartTime,
        appPreferences.isPremiumUnlocked
    ) { trialStart, isPremium ->
        subscriptionManager.isAccessGranted(trialStart, isPremium)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun unlockPremium() {
        viewModelScope.launch {
            appPreferences.setPremiumUnlocked(true)
        }
    }
}
