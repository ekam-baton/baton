package com.ekam.baton.feature.agents

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.ui.components.glassCard
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collectLatest
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    onAddAgentClick: (String?, String?, String?) -> Unit,
    onEditAgentClick: (String) -> Unit,
    viewModel: AgentsViewModel = koinViewModel()
) {
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel.uiEvents) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is AgentsUiEvent.ShowError -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is AgentsUiEvent.LaunchBrowser -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                    context.startActivity(intent)
                }
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D131F), // Deep Ocean Blue
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Agent Directory", 
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    ) 
                },
                actions = {
                    IconButton(onClick = { /* Open Scanner */ }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Node", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D131F).copy(alpha = 0.85f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAddAgentClick(null, null, null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp) // Squircle FAB
            ) {
                Icon(Icons.Default.Add, contentDescription = "Provision Agent")
            }
        }
    ) { innerPadding ->
        if (agents.isEmpty()) {
            // Soft Landing Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(24.dp))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "No Nodes Registered",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your swarm directory is empty. Provision a local MCP node or cloud inference engine to begin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(agents) { agent ->
                    AgentDirectoryCard(agent = agent, onClick = { onEditAgentClick(agent.id) })
                }
            }
        }
    }
}

@Composable
fun AgentDirectoryCard(agent: Agent, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Squircle Avatar
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(android.graphics.Color.parseColor(agent.colorAccent.takeIf { it.isNotBlank() } ?: "#306EE8"))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = agent.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (agent.securityMode == "sovereign") {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFFDC2626)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("AIR-GAPPED", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = agent.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ID: ${agent.id.take(8).uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
