package com.ekam.baton.feature.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekam.baton.core.data.db.entity.MemoryEntity
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.repository.MemoryRepository
import com.ekam.baton.core.data.repository.AgentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

import androidx.lifecycle.SavedStateHandle

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val agentRepository: AgentRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    private val _agentFilter = MutableStateFlow<String?>(savedStateHandle["agentId"])
    val agentFilter = _agentFilter.asStateFlow()

    private val _memoriesByLayer = MutableStateFlow<Map<String, List<MemoryEntity>>>(emptyMap())
    val memoriesByLayer = _memoriesByLayer.asStateFlow()

    val agents: StateFlow<List<AgentEntity>> = agentRepository.getAllAgents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            combine(
                memoryRepository.getAllMemories(),
                _searchQuery,
                _agentFilter
            ) { allMemories, query, agentIdFilter ->
                var filtered = allMemories
                
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
                filtered.groupBy { it.layer }
            }.collect { map ->
                _memoriesByLayer.value = map
            }
        }
    }

    fun setAgentFilter(agentId: String?) {
        _agentFilter.value = agentId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addMemory(layer: String, agentId: String?, title: String, content: String, tags: List<String>) {
        viewModelScope.launch {
            val tagsJson = "[\"" + tags.joinToString("\",\"") + "\"]"
            val memory = MemoryEntity(
                id = UUID.randomUUID().toString(),
                layer = layer,
                agentId = agentId,
                conversationId = null,
                title = title,
                content = content,
                createdAt = System.currentTimeMillis(),
                lastAccessedAt = System.currentTimeMillis(),
                relevanceScore = 1.0f,
                tags = if (tags.isEmpty()) "[]" else tagsJson,
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
}
