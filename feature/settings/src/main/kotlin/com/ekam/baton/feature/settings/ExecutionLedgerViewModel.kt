package com.ekam.baton.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ExecutionLedgerViewModel(
    private val auditDao: AuditDao
) : ViewModel() {

    // Expose the raw cryptographic audit log as a live stream to the UI
    val auditLogs: StateFlow<List<AuditLogEntity>> = auditDao.getAllAuditLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
