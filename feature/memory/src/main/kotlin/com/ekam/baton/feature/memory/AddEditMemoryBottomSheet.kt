package com.ekam.baton.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ekam.baton.core.data.db.entity.AgentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMemoryBottomSheet(
    agents: List<AgentEntity>,
    onSave: (layer: String, agentId: String?, title: String, content: String, tags: List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var selectedLayer by remember { mutableStateOf("semantic") }
    var selectedAgentId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    
    // Tag input state
    var currentTag by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }

    var expandedAgentDropdown by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Add Memory",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Layer Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val layers = listOf("working", "episodic", "semantic")
                layers.forEachIndexed { index, layer ->
                    SegmentedButton(
                        selected = selectedLayer == layer,
                        onClick = { selectedLayer = layer },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = layers.size)
                    ) {
                        Text(layer.replaceFirstChar { it.uppercase() })
                    }
                }
            }

            // Agent Scope
            ExposedDropdownMenuBox(
                expanded = expandedAgentDropdown,
                onExpandedChange = { expandedAgentDropdown = !expandedAgentDropdown },
                modifier = Modifier.fillMaxWidth()
            ) {
                val selectedAgentName = if (selectedAgentId == null) "All agents (Global)" else agents.find { it.id == selectedAgentId }?.name ?: "Unknown"
                
                OutlinedTextField(
                    value = selectedAgentName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Scope") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAgentDropdown) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = expandedAgentDropdown,
                    onDismissRequest = { expandedAgentDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All agents (Global)") },
                        onClick = { 
                            selectedAgentId = null
                            expandedAgentDropdown = false
                        }
                    )
                    agents.forEach { agent ->
                        DropdownMenuItem(
                            text = { Text(agent.name) },
                            onClick = { 
                                selectedAgentId = agent.id
                                expandedAgentDropdown = false
                            }
                        )
                    }
                }
            }

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Content
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                maxLines = 8
            )

            // Tags
            Column {
                Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                
                // Active tags
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tag) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { tags = tags.filter { it != tag } },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove tag")
                                }
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Tag input
                OutlinedTextField(
                    value = currentTag,
                    onValueChange = { input -> 
                        if (input.endsWith(",") || input.endsWith("\n")) {
                            val newTag = input.dropLast(1).trim()
                            if (newTag.isNotEmpty() && !tags.contains(newTag)) {
                                tags = tags + newTag
                            }
                            currentTag = ""
                        } else {
                            currentTag = input
                        }
                    },
                    placeholder = { Text("Type tag and press comma") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(selectedLayer, selectedAgentId, title, content, tags)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save Memory")
            }
        }
    }
}
