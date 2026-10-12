package com.ekam.baton.feature.memory

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.Memory
import com.ekam.baton.core.ui.components.glassCard
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: MemoryViewModel = koinViewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Working", "Episodic", "Semantic")

    val memoriesByLayer by viewModel.memoriesByLayer.collectAsStateWithLifecycle()
    val layerCounts by viewModel.layerCounts.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val agentFilter by viewModel.agentFilter.collectAsStateWithLifecycle()

    var isSearchActive by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var memoryPendingDelete by remember { mutableStateOf<Memory?>(null) }
    var memoryPendingPromote by remember { mutableStateOf<Memory?>(null) }
    var showConsolidateDialog by remember { mutableStateOf(false) }

    val currentLayer = when (selectedTabIndex) {
        0 -> "working"
        1 -> "episodic"
        else -> "semantic"
    }

    val currentMemories = memoriesByLayer[currentLayer] ?: emptyList()

    // Consolidate Working Memory Dialog
    if (showConsolidateDialog) {
        var episodeTitle by remember { mutableStateOf("Session Consolidation") }
        var episodeSummary by remember {
            mutableStateOf(
                currentMemories.joinToString("\n• ") { it.content.ifBlank { it.title } }
                    .let { if (it.isNotBlank()) "• $it" else "" }
            )
        }

        AlertDialog(
            onDismissRequest = { showConsolidateDialog = false },
            title = {
                Text(
                    text = "Consolidate Working Memories",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Synthesizes all current ephemeral working memories into an episodic conversation log and archives the raw scratchpad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = episodeTitle,
                        onValueChange = { episodeTitle = it },
                        label = { Text("Episode Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = episodeSummary,
                        onValueChange = { episodeSummary = it },
                        label = { Text("Synthesized Summary") },
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (episodeTitle.isNotBlank() && episodeSummary.isNotBlank()) {
                            viewModel.consolidateWorkingMemories(currentMemories, episodeTitle, episodeSummary)
                            showConsolidateDialog = false
                            selectedTabIndex = 1 // Switch to Episodic tab
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("CONSOLIDATE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsolidateDialog = false }) {
                    Text("CANCEL", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF161F30)
        )
    }

    // Promote to Semantic Dialog
    if (memoryPendingPromote != null) {
        val memoryToPromote = memoryPendingPromote!!
        AlertDialog(
            onDismissRequest = { memoryPendingPromote = null },
            title = {
                Text(
                    text = "Promote to Semantic Memory?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This fact will be elevated to the permanent sovereign knowledge graph, available across all future agent prompts and conversations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.promoteToSemantic(memoryToPromote)
                        memoryPendingPromote = null
                        selectedTabIndex = 2 // Switch to Semantic tab
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("PROMOTE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryPendingPromote = null }) {
                    Text("CANCEL", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF161F30)
        )
    }

    // Delete Dialog
    if (memoryPendingDelete != null) {
        val mem = memoryPendingDelete!!
        AlertDialog(
            onDismissRequest = { memoryPendingDelete = null },
            title = { Text("Delete Memory", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to delete this memory? This action cannot be undone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMemory(mem.id)
                        memoryPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memoryPendingDelete = null }) {
                    Text("CANCEL", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF161F30)
        )
    }

    Scaffold(
        containerColor = Color(0xFF0D131F), // Abyssal Deep Ocean Blue
        topBar = {
            Column(
                modifier = Modifier.background(Color(0xFF0D131F).copy(alpha = 0.95f))
            ) {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.updateSearchQuery(it) },
                                placeholder = {
                                    Text(
                                        "Search memories, tags, facts...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Column {
                                Text(
                                    text = "Memory Vault",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Working, Episodic & Semantic",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (selectedTabIndex == 0 && currentMemories.isNotEmpty()) {
                            IconButton(onClick = { showConsolidateDialog = true }) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Consolidate Working Memories",
                                    tint = Color(0xFF00D2FF)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) viewModel.updateSearchQuery("")
                            }
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isSearchActive) "Close Search" else "Search",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                // Agent Scope Filter Chips Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        val isAllSelected = agentFilter == null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isAllSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else Color(0xFF161F30).copy(alpha = 0.6f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isAllSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { viewModel.setAgentFilter(null) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🌐 All Agents (Global)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isAllSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    items(agents, key = { it.id }) { agent ->
                        val isSelected = agentFilter == agent.id
                        val accentColor = remember(agent.colorAccent) {
                            try {
                                Color(android.graphics.Color.parseColor(agent.colorAccent))
                            } catch (e: Exception) {
                                Color(0xFF306EE8)
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else Color(0xFF161F30).copy(alpha = 0.6f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    viewModel.setAgentFilter(if (isSelected) null else agent.id)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = agent.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Tab Row with Real-Time Counters
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF00D2FF),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = Color(0xFF00D2FF),
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        val count = when (index) {
                            0 -> layerCounts["working"] ?: 0
                            1 -> layerCounts["episodic"] ?: 0
                            else -> layerCounts["semantic"] ?: 0
                        }
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (selectedTabIndex == index) Color(0xFF00D2FF) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (selectedTabIndex == index) Color(0xFF00D2FF).copy(alpha = 0.2f)
                                                else Color.White.copy(alpha = 0.08f)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = if (selectedTabIndex == index) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Memory")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Layer Explainer Banner
            item {
                LayerExplainerBanner(layer = currentLayer)
            }

            if (currentMemories.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(16.dp))
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (currentLayer) {
                                    "working" -> Icons.Default.Memory
                                    "episodic" -> Icons.Default.History
                                    else -> Icons.Default.Psychology
                                },
                                contentDescription = null,
                                modifier = Modifier.size(54.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = when (currentLayer) {
                                    "working" -> "No active working session context."
                                    "episodic" -> "No conversation episodes recorded yet."
                                    else -> "No semantic facts stored in the vault."
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (currentLayer) {
                                    "working" -> "Ephemeral context vectors automatically populate during tool calls and active agent turns."
                                    "episodic" -> "High-value message summaries automatically synthesize into episodes, or consolidate working memories."
                                    else -> "Tap '+' to teach your agents facts, guidelines, user preferences, and permanent directives."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(currentMemories, key = { it.id }) { memory ->
                    val agent = agents.find { it.id == memory.agentId }
                    val agentName = agent?.name ?: if (memory.agentId != null) "Agent (${memory.agentId?.take(6)})" else "Global Knowledge"
                    val agentAccent = remember(agent?.colorAccent) {
                        try {
                            Color(android.graphics.Color.parseColor(agent?.colorAccent ?: "#306EE8"))
                        } catch (e: Exception) {
                            Color(0xFF306EE8)
                        }
                    }

                    val scoreColor = when {
                        memory.relevanceScore >= 0.9f -> Color(0xFF10B981) // Emerald Green
                        memory.relevanceScore >= 0.7f -> Color(0xFF306EE8) // Cobalt
                        else -> Color(0xFFF59E0B) // Amber
                    }

                    val timeAgo = try {
                        DateUtils.getRelativeTimeSpanString(memory.createdAt).toString()
                    } catch (_: Exception) {
                        "Recently"
                    }

                    val parsedTags = remember(memory.tags) {
                        if (memory.tags.isBlank() || memory.tags == "[]") emptyList()
                        else memory.tags
                            .removeSurrounding("[", "]")
                            .split(",")
                            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                            .filter { it.isNotBlank() }
                    }

                    MemoryFactCardV2(
                        memory = memory,
                        agentName = agentName,
                        agentAccent = agentAccent,
                        scoreColor = scoreColor,
                        timeAgo = timeAgo,
                        tags = parsedTags,
                        onToggleActive = { isActive -> viewModel.toggleActive(memory.id, isActive) },
                        onPromoteClick = { memoryPendingPromote = memory },
                        onDeleteClick = { memoryPendingDelete = memory }
                    )
                }
            }
        }

        // Add Memory Bottom Sheet
        if (showAddSheet) {
            AddEditMemoryBottomSheet(
                agents = agents,
                onSave = { layer, agentId, title, content, tags ->
                    viewModel.addMemory(layer, agentId ?: agentFilter, title, content, tags)
                    showAddSheet = false
                },
                onDismissRequest = { showAddSheet = false }
            )
        }
    }
}

@Composable
fun LayerExplainerBanner(layer: String) {
    val (title, description, accentColor) = when (layer) {
        "working" -> Triple(
            "EPHEMERAL WORKING CONTEXT",
            "Volatile scratchpad for tool outputs, session variables, and multi-step reasoning context.",
            Color(0xFF00D2FF)
        )
        "episodic" -> Triple(
            "CONVERSATIONAL EPISODES",
            "Chronological records of interaction histories, task milestones, and peer-to-peer dialogues.",
            Color(0xFF306EE8)
        )
        else -> Triple(
            "SOVEREIGN KNOWLEDGE GRAPH",
            "Permanent semantic facts, rules, user preferences, and foundational agent directives.",
            Color(0xFF10B981)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryFactCardV2(
    memory: Memory,
    agentName: String,
    agentAccent: Color,
    scoreColor: Color,
    timeAgo: String,
    tags: List<String>,
    onToggleActive: (Boolean) -> Unit,
    onPromoteClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        // Top Header: Scope + Active Switch + Timestamp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (memory.agentId != null) agentAccent else Color(0xFF00D2FF))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = agentName,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeAgo,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = memory.isActive,
                    onCheckedChange = onToggleActive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981),
                        uncheckedTrackColor = Color(0xFF161F30)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Title
        if (memory.title.isNotBlank()) {
            Text(
                text = memory.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = if (expanded) 10 else 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Content
        Text(
            text = memory.content.ifBlank { memory.title },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded }
        )

        // Tags Row
        if (tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            ),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Footer Row: Confidence Pill + Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Confidence Score
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scoreColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(scoreColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${(memory.relevanceScore * 100).toInt()}% Relevance",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = scoreColor
                )
            }

            // Actions Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (memory.layer.equals("episodic", ignoreCase = true)) {
                    TextButton(
                        onClick = onPromoteClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PROMOTE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            ),
                            color = Color(0xFF10B981)
                        )
                    }
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Revoke Memory",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
