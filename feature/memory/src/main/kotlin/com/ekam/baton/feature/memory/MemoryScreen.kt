package com.ekam.baton.feature.memory

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.ui.components.glassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel = koinViewModel()
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Working", "Episodic", "Semantic")

    Scaffold(
        containerColor = Color(0xFF0D131F), // Deep Ocean Blue
        topBar = {
            Column(
                modifier = Modifier.background(Color(0xFF0D131F).copy(alpha = 0.85f))
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Memory Vault",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    text = title, 
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                ) 
                            }
                        )
                    }
                }
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
            // Mock Fact 1
            item {
                MemoryFactCard(
                    factText = "User prefers Apple iOS HIG 'squircle' shapes and strict mathematical padding over generic Material 3 components.",
                    confidenceScore = "98%",
                    agentSource = "Orchestrator Node",
                    timeAgo = "2h ago",
                    scoreColor = Color(0xFF10B981) // Emerald Green
                )
            }
            // Mock Fact 2
            item {
                MemoryFactCard(
                    factText = "Deployments strictly require human-in-the-loop permission if modifying external repositories.",
                    confidenceScore = "100%",
                    agentSource = "Synthex Lead",
                    timeAgo = "1d ago",
                    scoreColor = Color(0xFF306EE8) // Electric Cobalt
                )
            }
            // Mock Fact 3
            item {
                MemoryFactCard(
                    factText = "Default telemetry token burn warning threshold is set to 50 tk/s.",
                    confidenceScore = "74%",
                    agentSource = "System",
                    timeAgo = "1w ago",
                    scoreColor = Color(0xFFF59E0B) // Amber
                )
            }
        }
    }
}

@Composable
fun MemoryFactCard(
    factText: String,
    confidenceScore: String,
    agentSource: String,
    timeAgo: String,
    scoreColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        // Top Row: Source & Time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = agentSource,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = timeAgo,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // The Fact
        Text(
            text = factText,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
            // Intentionally no maxLines here to allow full fact reading, but it gracefully wraps.
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Bottom Row: Confidence & Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Confidence Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(scoreColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(scoreColor))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$confidenceScore Confident",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = scoreColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Actions
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Fact",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Revoke Fact",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
