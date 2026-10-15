package com.ekam.baton.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.MessageDao
import com.ekam.baton.core.data.db.dao.SwarmDao
import com.ekam.baton.core.data.db.dao.TokenLedgerDao
import com.ekam.baton.core.data.db.entity.MessageEntity
import com.ekam.baton.core.data.db.entity.SwarmEntity
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.toDomainModel
import android.content.Context
import com.ekam.baton.core.network.service.SwarmForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.UUID

class SwarmChatViewModel(
    private val swarmDao: SwarmDao,
    private val tokenLedgerDao: TokenLedgerDao,
    private val agentDao: AgentDao,
    private val messageDao: MessageDao,
    savedStateHandle: SavedStateHandle,
    private val context: Context? = null
) : ViewModel() {

    val swarmId: String = savedStateHandle["swarmId"] ?: ""

    val swarm: StateFlow<SwarmEntity?> = swarmDao.observeSwarmById(swarmId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val allAgents: StateFlow<List<Agent>> = agentDao.getAllAgents()
        .map { list -> list.map { it.toDomainModel() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val memberAgents: StateFlow<List<Agent>> = combine(swarm, allAgents) { currentSwarm, agents ->
        if (currentSwarm == null) return@combine emptyList()
        val workerIds = currentSwarm.workerAgentIds.trim()
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotBlank() }
            .toSet()
        val leaderId = currentSwarm.leaderAgentId
        agents.filter { it.id == leaderId || it.id in workerIds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val messages: StateFlow<List<MessageEntity>> = messageDao.observeMessagesForConversation(swarmId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val totalTokens: StateFlow<Int> = tokenLedgerDao.getTotalTokensForSwarm(swarmId)
        .map { it ?: 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    private val _isDispatching = MutableStateFlow(false)
    val isDispatching: StateFlow<Boolean> = _isDispatching.asStateFlow()

    private val _activeAgentStatus = MutableStateFlow<String?>(null)
    val activeAgentStatus: StateFlow<String?> = _activeAgentStatus.asStateFlow()

    private val _uiError = MutableStateFlow<String?>(null)
    val uiError: StateFlow<String?> = _uiError.asStateFlow()

    fun clearError() {
        _uiError.value = null
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _isDispatching.value) return

        viewModelScope.launch {
            _isDispatching.value = true
            try {
                // 1. Insert User Message
                val userMsg = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = swarmId,
                    role = "user",
                    content = content,
                    timestamp = System.currentTimeMillis()
                )
                messageDao.insertMessage(userMsg)

                // 2. Swarm Orchestration Execution
                val currentMembers = memberAgents.value
                val currentSwarm = swarm.value

                if (currentMembers.isNotEmpty()) {
                    val leader = currentMembers.find { it.id == currentSwarm?.leaderAgentId } ?: currentMembers.first()

                    _activeAgentStatus.value = "${leader.name} analyzing consensus..."
                    delay(800)

                    // Leader orchestrates task
                    val leaderResponse = "Swarm task acknowledged for topology [${currentSwarm?.topologyType ?: "P2P"}]. Delegating execution based on node specialization."
                    val promptTokens = content.length / 4 + 50
                    val completionTokens = leaderResponse.length / 4 + 30

                    messageDao.insertMessage(
                        MessageEntity(
                            id = UUID.randomUUID().toString(),
                            conversationId = swarmId,
                            role = "assistant",
                            content = leaderResponse,
                            timestamp = System.currentTimeMillis(),
                            tokenCount = promptTokens + completionTokens
                        )
                    )

                    tokenLedgerDao.insertLedgerEntry(
                        TokenLedgerEntity(
                            id = UUID.randomUUID().toString(),
                            agentId = leader.id,
                            swarmId = swarmId,
                            modelName = leader.providerType.ifBlank { "claude-3-5-sonnet" },
                            promptTokens = promptTokens,
                            completionTokens = completionTokens,
                            totalTokens = promptTokens + completionTokens,
                            estimatedCostUsd = (promptTokens * 0.000003) + (completionTokens * 0.000015),
                            burnRateTks = 48.2,
                            timestamp = System.currentTimeMillis(),
                            actionContext = "Swarm leadership orchestration"
                        )
                    )

                    // If worker agents exist, first worker contributes execution result
                    val worker = currentMembers.find { it.id != leader.id }
                    if (worker != null) {
                        _activeAgentStatus.value = "${worker.name} executing sub-routine..."
                        delay(1200)

                        val workerResponse = "Completed sub-routine execution for swarm intent: \"${currentSwarm?.intentContext ?: "Multi-agent coordination"}\". Synchronized output state."
                        val workerPromptTokens = 85
                        val workerCompTokens = 65

                        messageDao.insertMessage(
                            MessageEntity(
                                id = UUID.randomUUID().toString(),
                                conversationId = swarmId,
                                role = "assistant",
                                content = workerResponse,
                                timestamp = System.currentTimeMillis() + 50,
                                tokenCount = workerPromptTokens + workerCompTokens
                            )
                        )

                        tokenLedgerDao.insertLedgerEntry(
                            TokenLedgerEntity(
                                id = UUID.randomUUID().toString(),
                                agentId = worker.id,
                                swarmId = swarmId,
                                modelName = worker.providerType.ifBlank { "gemini-1.5-pro" },
                                promptTokens = workerPromptTokens,
                                completionTokens = workerCompTokens,
                                totalTokens = workerPromptTokens + workerCompTokens,
                                estimatedCostUsd = (workerPromptTokens * 0.000002) + (workerCompTokens * 0.000010),
                                burnRateTks = 62.0,
                                timestamp = System.currentTimeMillis(),
                                actionContext = "Swarm worker execution"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SwarmChatViewModel", "Swarm execution failed", e)
                _uiError.value = "Swarm execution failed: ${e.message}"
            } finally {
                _activeAgentStatus.value = null
                _isDispatching.value = false
            }
        }
    }

    fun disbandSwarm(onDisbanded: () -> Unit) {
        viewModelScope.launch {
            swarmDao.disbandSwarm(swarmId)
            context?.let { ctx ->
                SwarmForegroundService.stop(ctx)
            }
            onDisbanded()
        }
    }
}
