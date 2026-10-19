package com.ekam.baton.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.dao.TokenLedgerDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

class ExecutionLedgerViewModel(
    private val auditDao: AuditDao,
    private val tokenLedgerDao: TokenLedgerDao,
    private val agentDao: AgentDao
) : ViewModel() {

    // Expose the raw cryptographic audit log as a live stream to the UI
    val auditLogs: StateFlow<List<AuditLogEntity>> = auditDao.getAllAuditLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val ledgerEntries: StateFlow<List<TokenLedgerEntity>> = tokenLedgerDao.getAllLedgerEntries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val grandTotalTokens: StateFlow<Int> = tokenLedgerDao.getGrandTotalTokens()
        .map { it ?: 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    val grandTotalCost: StateFlow<Double> = tokenLedgerDao.getGrandTotalCost()
        .map { it ?: 0.0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0.0
        )

    val agentsMap: StateFlow<Map<String, AgentEntity>> = agentDao.getAllAgents()
        .map { agents -> agents.associateBy { it.id } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    private val _selectedModelFilter = MutableStateFlow<String?>(null)
    val selectedModelFilter: StateFlow<String?> = _selectedModelFilter.asStateFlow()

    // Filtered ledger entries based on selected model
    val filteredLedgerEntries: StateFlow<List<TokenLedgerEntity>> = combine(
        ledgerEntries,
        _selectedModelFilter
    ) { entries, filter ->
        if (filter == null || filter == "ALL") {
            entries
        } else {
            entries.filter { it.modelName.equals(filter, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    fun setModelFilter(model: String?) {
        _selectedModelFilter.value = model
    }

    fun triggerRollingGenesisBlock() {
        viewModelScope.launch {
            try {
                // Prune to the last 1000 blocks to prevent massive SQLite bloat
                auditDao.pruneOldLogs(1000)
            } catch (e: Exception) {
                // Prevent crash on DB lock or disk I/O errors
            }
        }
    }

    fun pruneLedgerEntries(keepCount: Int = 1000) {
        viewModelScope.launch {
            try {
                tokenLedgerDao.pruneOldLedgerEntries(keepCount)
            } catch (e: Exception) {
                // Prevent crash on DB lock or disk I/O errors
            }
        }
    }

    fun generateExportReport(
        entries: List<TokenLedgerEntity> = ledgerEntries.value,
        audits: List<AuditLogEntity> = auditLogs.value
    ): String {
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("BATON V2 EXECUTION & CRYPTOGRAPHIC AUDIT REPORT")
        sb.appendLine("==================================================")
        sb.appendLine("Exported: ${Date()}")
        sb.appendLine("Total Transactions: ${entries.size}")
        sb.appendLine("Grand Total Tokens: ${entries.sumOf { it.totalTokens }}")
        sb.appendLine("Total Estimated Cost: $${String.format(Locale.US, "%.6f", entries.sumOf { it.estimatedCostUsd })}")
        sb.appendLine("\n--- TOKEN TRANSACTIONS ---")
        entries.forEach { entry ->
            sb.appendLine("[${Date(entry.timestamp)}] Agent: ${entry.agentId} | Swarm: ${entry.swarmId ?: "N/A"} | Model: ${entry.modelName} | Tokens: ${entry.totalTokens} (Prompt: ${entry.promptTokens}, Completion: ${entry.completionTokens}) | Cost: $${String.format(Locale.US, "%.6f", entry.estimatedCostUsd)} | Burn: ${entry.burnRateTks} tk/s | Context: ${entry.actionContext}")
        }
        sb.appendLine("\n--- IMMUTABLE CRYPTOGRAPHIC AUDIT TRAIL ---")
        audits.forEach { log ->
            sb.appendLine("[${Date(log.timestamp)}] Action: ${log.action} | Entity: ${log.entityName} (${log.entityId}) | PrevHash: ${log.previousHash} | Hash: ${log.hash} | Device: ${log.deviceId}")
        }
        sb.appendLine("==================================================")
        return sb.toString()
    }
}
