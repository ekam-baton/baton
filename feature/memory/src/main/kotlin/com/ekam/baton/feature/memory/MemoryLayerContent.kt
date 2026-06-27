package com.ekam.baton.feature.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ekam.baton.core.data.model.Memory

@Composable
fun MemoryLayerContent(
    layer: String,
    memories: List<Memory>,
    onToggleActive: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    if (memories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val emptyMessage = when (layer) {
                "working" -> "No active session context"
                "episodic" -> "Conversations will be summarized here automatically"
                "semantic" -> "Add facts about yourself, your projects, or preferences"
                else -> "No memories found"
            }
            Text(
                text = emptyMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(memories, key = { it.id }) { memory ->
                MemoryCard(
                    memory = memory,
                    modifier = Modifier.animateItem(),
                    onToggleActive = { isActive -> onToggleActive(memory.id, isActive) },
                    onDelete = { onDelete(memory.id) }
                )
            }
        }
    }
}
