package com.ekam.baton.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.db.entity.AgentEntity

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val agentRepository: AgentRepository
) : ViewModel() {

    val agents: StateFlow<List<AgentEntity>> = agentRepository.getAllAgents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val tunnelStatusMap: StateFlow<Map<String, String>> = appPreferences.tunnelStatusMap
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val themeMode: StateFlow<String> = appPreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "system"
        )

    val accentColor: StateFlow<Long> = appPreferences.accentColor
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0xFF9D65FF
        )

    val fontSize: StateFlow<String> = appPreferences.fontSize
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "medium"
        )

    val defaultTokenLimit: StateFlow<Int> = appPreferences.defaultTokenLimit
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 4096
        )

    val enableHapticFeedback: StateFlow<Boolean> = appPreferences.enableHapticFeedback
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val appLockEnabled: StateFlow<Boolean> = appPreferences.appLockEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val autoExtractFacts: StateFlow<Boolean> = appPreferences.autoExtractFacts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val autoGenerateEpisodes: StateFlow<Boolean> = appPreferences.autoGenerateEpisodes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val memoryRetentionDays: StateFlow<Int> = appPreferences.memoryRetentionDays
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 30
        )

    fun setThemeMode(mode: String) = viewModelScope.launch { appPreferences.setThemeMode(mode) }
    fun setAccentColor(color: Long) = viewModelScope.launch { appPreferences.setAccentColor(color) }
    fun setFontSize(size: String) = viewModelScope.launch { appPreferences.setFontSize(size) }
    fun setDefaultTokenLimit(limit: Int) = viewModelScope.launch { appPreferences.setDefaultTokenLimit(limit) }
    fun setEnableHapticFeedback(enable: Boolean) = viewModelScope.launch { appPreferences.setEnableHapticFeedback(enable) }
    fun setAppLockEnabled(enable: Boolean) = viewModelScope.launch { appPreferences.setAppLockEnabled(enable) }
    fun setAutoExtractFacts(enable: Boolean) = viewModelScope.launch { appPreferences.setAutoExtractFacts(enable) }
    fun setAutoGenerateEpisodes(enable: Boolean) = viewModelScope.launch { appPreferences.setAutoGenerateEpisodes(enable) }
    fun setMemoryRetentionDays(days: Int) = viewModelScope.launch { appPreferences.setMemoryRetentionDays(days) }
}
