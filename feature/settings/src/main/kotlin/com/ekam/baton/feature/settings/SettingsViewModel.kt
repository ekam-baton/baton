package com.ekam.baton.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.preferences.AppPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.preferences.SessionManager
import com.ekam.baton.core.data.preferences.SubscriptionManager
import com.ekam.baton.core.data.repository.WipeDataManager
import com.ekam.baton.core.data.db.dao.AuditDao

class SettingsViewModel(
    private val appPreferences: AppPreferences,
    private val agentRepository: AgentRepository,
    private val wipeDataManager: WipeDataManager,
    private val sessionManager: SessionManager,
    private val subscriptionManager: SubscriptionManager,
    private val auditDao: AuditDao
) : ViewModel() {

    val agents: StateFlow<List<Agent>> = agentRepository.getAllAgents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userEmail: StateFlow<String> = appPreferences.userEmail
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val userPhone: StateFlow<String> = appPreferences.userPhone
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
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
            initialValue = 0xFFECEFF4
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

    val trialStartTime: StateFlow<Long> = appPreferences.trialStartTime
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    val isPremiumUnlocked: StateFlow<Boolean> = appPreferences.isPremiumUnlocked
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

    val backendUrl: StateFlow<String> = appPreferences.backendUrl
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "http://10.0.2.2:8080/"
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
    fun setPremiumUnlocked(unlocked: Boolean) = viewModelScope.launch { appPreferences.setPremiumUnlocked(unlocked) }
    fun setBackendUrl(url: String) = viewModelScope.launch { appPreferences.setBackendUrl(url) }

    fun getTrialDaysRemaining(startTime: Long): Long {
        return subscriptionManager.getTrialDaysRemaining(startTime)
    }

    fun isTrialActive(startTime: Long): Boolean {
        return subscriptionManager.isTrialActive(startTime)
    }

    fun logout() {
        sessionManager.setLoggedIn(false)
    }

    fun clearAllData() = viewModelScope.launch {
        wipeDataManager.wipeAllData()
        sessionManager.clearRegistration()
    }

    fun clearAllMemories() = viewModelScope.launch {
        wipeDataManager.clearAllMemories()
    }

    fun exportAuditLogs(context: android.content.Context) = viewModelScope.launch {
        val logs = auditDao.getAllAuditLogsSync()
        try {
            val file = java.io.File(context.cacheDir, "baton_audit_ledger.json")
            val jsonList = logs.map { 
                """{"id":"${it.id}","entity":"${it.entityName}","action":"${it.action}","timestamp":${it.timestamp},"payload":${it.payloadJson},"prevHash":"${it.previousHash}","hash":"${it.hash}"}""" 
            }.joinToString(",\n", "[\n", "\n]")
            
            file.writeText(jsonList)
            android.widget.Toast.makeText(context, "Audit Ledger exported to ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
