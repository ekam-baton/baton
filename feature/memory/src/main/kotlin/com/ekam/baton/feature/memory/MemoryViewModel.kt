package com.ekam.baton.feature.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.Memory
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.repository.MemoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONArray

class MemoryViewModel(
    private val memoryRepository: MemoryRepository,
    private val agentRepository: AgentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _agentFilter = MutableStateFlow<String?>(savedStateHandle["agentId"])
    val agentFilter: StateFlow<String?> = _agentFilter.asStateFlow()

    val agents: StateFlow<List<Agent>> = agentRepository.getAllAgents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val allMemories: StateFlow<List<Memory>> = memoryRepository.getAllMemories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val memoriesByLayer: StateFlow<Map<String, List<Memory>>> = combine(
        allMemories,
        _searchQuery,
        _agentFilter
    ) { memories, query, agentIdFilter ->
        var filtered = memories

        if (agentIdFilter != null) {
            filtered = filtered.filter { it.agentId == agentIdFilter || it.agentId == null }
        }

        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.content.contains(query, ignoreCase = true) ||
                it.tags.contains(query, ignoreCase = true)
            }
        }
        filtered.groupBy { it.layer.lowercase() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )

    val layerCounts: StateFlow<Map<String, Int>> = memoriesByLayer.map { map ->
        map.mapValues { it.value.size }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyMap()
    )

    fun setAgentFilter(agentId: String?) {
        _agentFilter.value = agentId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addMemory(layer: String, agentId: String?, title: String, content: String, tags: List<String>) {
        viewModelScope.launch {
            val tagsJson = JSONArray(tags).toString()
            val memory = Memory(
                id = UUID.randomUUID().toString(),
                layer = layer.lowercase(),
                agentId = agentId,
                conversationId = null,
                title = title,
                content = content,
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                relevanceScore = 1.0f,
                tags = tagsJson,
                isActive = true
            )
            memoryRepository.upsertMemory(memory)
        }
    }

    fun toggleActive(id: String, isActive: Boolean) {
        viewModelScope.launch {
            memoryRepository.toggleMemoryActive(id, isActive)
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            memoryRepository.deleteMemory(id)
        }
    }

    fun promoteToSemantic(memory: Memory) {
        viewModelScope.launch {
            val promoted = memory.copy(
                layer = "semantic",
                relevanceScore = 1.0f,
                lastAccessedAt = System.currentTimeMillis()
            )
            memoryRepository.upsertMemory(promoted)
        }
    }

    fun updateRelevanceScore(memory: Memory, newScore: Float) {
        viewModelScope.launch {
            val updated = memory.copy(
                relevanceScore = newScore.coerceIn(0.0f, 1.0f),
                lastAccessedAt = System.currentTimeMillis()
            )
            memoryRepository.upsertMemory(updated)
        }
    }

    fun consolidateWorkingMemories(workingMemories: List<Memory>, episodeTitle: String, episodeSummary: String) {
        viewModelScope.launch {
            val episode = Memory(
                id = UUID.randomUUID().toString(),
                layer = "episodic",
                agentId = _agentFilter.value,
                conversationId = null,
                title = episodeTitle,
                content = episodeSummary,
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                relevanceScore = 0.95f,
                tags = "[\"consolidated\",\"session-summary\"]",
                isActive = true
            )
            memoryRepository.upsertMemory(episode)

            workingMemories.forEach { working ->
                memoryRepository.toggleMemoryActive(working.id, false)
            }
        }
    }
}
