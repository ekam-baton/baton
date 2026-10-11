package com.ekam.baton.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.SwarmDao
import com.ekam.baton.core.data.db.dao.TokenLedgerDao
import com.ekam.baton.core.data.db.entity.SwarmEntity
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.AgentRole
import com.ekam.baton.core.data.model.WorldRoom
import com.ekam.baton.core.data.model.WorldRoomProp
import com.ekam.baton.core.data.model.toDomainModel
import com.ekam.baton.core.data.repository.WorldRoomRepository
import android.content.Context
import com.ekam.baton.core.network.service.SwarmForegroundService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class SwarmTopologyViewModel(
    private val swarmDao: SwarmDao,
    private val agentDao: AgentDao,
    private val tokenLedgerDao: TokenLedgerDao,
    private val worldRoomRepository: WorldRoomRepository,
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

    val totalTokens: StateFlow<Int> = tokenLedgerDao.getTotalTokensForSwarm(swarmId)
        .map { it ?: 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0
        )

    val tokenLedgerEntries: StateFlow<List<TokenLedgerEntity>> = tokenLedgerDao.getLedgerForSwarm(swarmId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val totalCostUsd: StateFlow<Double> = tokenLedgerEntries
        .map { entries -> entries.sumOf { it.estimatedCostUsd } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = 0.0
        )

    private val _selectedAgentId = MutableStateFlow<String?>(null)
    val selectedAgentId: StateFlow<String?> = _selectedAgentId.asStateFlow()

    init {
        viewModelScope.launch {
            memberAgents.collect { members ->
                if (_selectedAgentId.value == null && members.isNotEmpty()) {
                    val leader = members.find { it.id == swarm.value?.leaderAgentId } ?: members.first()
                    _selectedAgentId.value = leader.id
                    ensureRoomForAgent(leader)
                }
            }
        }
    }

    val selectedAgent: StateFlow<Agent?> = combine(memberAgents, _selectedAgentId) { members, selectedId ->
        members.find { it.id == selectedId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    private val _currentRoom = MutableStateFlow<WorldRoom?>(null)
    val currentRoom: StateFlow<WorldRoom?> = _currentRoom.asStateFlow()

    private val _currentProps = MutableStateFlow<List<WorldRoomProp>>(emptyList())
    val currentProps: StateFlow<List<WorldRoomProp>> = _currentProps.asStateFlow()

    fun selectAgent(agentId: String) {
        _selectedAgentId.value = agentId
        val agent = memberAgents.value.find { it.id == agentId }
        if (agent != null) {
            viewModelScope.launch {
                ensureRoomForAgent(agent)
            }
        }
    }

    suspend fun ensureRoomForAgent(agent: Agent) {
        var room = worldRoomRepository.getRoomForAgent(agent.id)
        if (room == null) {
            val role = try {
                AgentRole.valueOf(agent.role.uppercase())
            } catch (_: Exception) {
                AgentRole.COORDINATOR
            }
            val newRoom = WorldRoom(
                id = UUID.randomUUID().toString(),
                agentId = agent.id,
                role = role,
                displayName = agent.name,
                colorHex = agent.colorAccent,
                worldX = Random.nextInt(1, 15),
                worldY = Random.nextInt(1, 15)
            )
            worldRoomRepository.upsertRoom(newRoom)
            worldRoomRepository.setDefaultPropsForRoom(newRoom)
            room = newRoom
        }
        _currentRoom.value = room
        val props = worldRoomRepository.getPropsForRoom(room.id)
        _currentProps.value = props
    }

    fun updateTopology(newTopology: String) {
        val current = swarm.value ?: return
        viewModelScope.launch {
            swarmDao.updateSwarm(current.copy(topologyType = newTopology))
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
