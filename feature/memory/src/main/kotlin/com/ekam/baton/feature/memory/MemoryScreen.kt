package com.ekam.baton.feature.memory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memoriesByLayer by viewModel.memoriesByLayer.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }

    val tabs = listOf("working", "episodic", "semantic")

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Memory",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    },
                    actions = {
                        IconButton(onClick = { 
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) viewModel.updateSearchQuery("")
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        IconButton(onClick = { showAddSheet = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Memory", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                
                val currentFilter = viewModel.agentFilter.collectAsState().value
                if (currentFilter != null) {
                    val agentName = agents.find { it.id == currentFilter }?.name ?: "Agent"
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        onClick = { viewModel.setAgentFilter(null) }
                    ) {
                        Text(
                            text = "Filtered by: $agentName ✕",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.tertiary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    title.replaceFirstChar { it.uppercase() },
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            }
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = isSearchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search memories...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = MaterialTheme.shapes.large
                )
            }

            val currentLayer = tabs[selectedTabIndex]
            val layerMemories = memoriesByLayer[currentLayer] ?: emptyList()

            MemoryLayerContent(
                layer = currentLayer,
                memories = layerMemories,
                onToggleActive = { id, isActive -> viewModel.toggleActive(id, isActive) },
                onDelete = { id -> viewModel.deleteMemory(id) }
            )
        }
    }

    if (showAddSheet) {
        AddEditMemoryBottomSheet(
            agents = agents,
            onSave = { layer, agentId, title, content, tags ->
                viewModel.addMemory(layer, agentId, title, content, tags)
                showAddSheet = false
            },
            onDismissRequest = { showAddSheet = false }
        )
    }
}
