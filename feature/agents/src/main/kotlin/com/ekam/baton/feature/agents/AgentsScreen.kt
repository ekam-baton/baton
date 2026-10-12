package com.ekam.baton.feature.agents

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
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
import com.ekam.baton.core.network.mdns.DiscoveredAgent
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
    onNavigateToBotFather: () -> Unit = {},
    onNavigateToCreator: () -> Unit = onNavigateToBotFather,
    onNavigateToManager: () -> Unit = onNavigateToCreator,
    onNavigateToAdvanced: () -> Unit = {},
    viewModel: AgentsViewModel = koinViewModel()
) {
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val discoveredAgents by viewModel.discoveredAgents.collectAsStateWithLifecycle()
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
                    IconButton(onClick = onNavigateToManager) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "BatonBot Manager", tint = Color(0xFFF59E0B))
                    }
                    IconButton(onClick = onNavigateToAdvanced) {
                        Icon(Icons.Default.Tune, contentDescription = "Advanced Features", tint = Color.White.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D131F).copy(alpha = 0.85f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCreator,
                containerColor = Color(0xFF3D8EFF),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create New Bot")
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
            // SECTION: Bot Creator & Manager
            item {
                Text(
                    "BOT LIFECYCLE & IDENTITY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF00D2FF),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }

            // HERO 1: Bot Creator Wizard
            item {
                BotCreatorHeroCard(onClick = onNavigateToCreator)
            }

            // HERO 2: BatonBot Manager Cockpit
            item {
                BatonBotManagerHeroCard(onClick = onNavigateToManager)
            }

            // HERO 3: Wi-Fi Discovery with Toggle Switch
            item {
                WifiDiscoveryControlCard(
                    isDiscovering = isDiscovering,
                    onToggle = { viewModel.toggleDiscovery(it) },
                    discoveredCount = discoveredAgents.size
                )
            }

            // Discovered Agents on Wi-Fi (if any)
            if (discoveredAgents.isNotEmpty()) {
                item {
                    Text(
                        "DISCOVERED ON WI-FI (${discoveredAgents.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF00D2FF),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                items(discoveredAgents) { discovered ->
                    DiscoveredAgentCard(
                        discovered = discovered,
                        onConnectClick = {
                            viewModel.quickProvisionDiscoveredAgent(discovered) { newId ->
                                onEditAgentClick(newId)
                            }
                        }
                    )
                }
            }

            // SECTION: Registered Agents
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ACTIVE SOVEREIGN NODES (${agents.size})",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    TextButton(onClick = onNavigateToCreator) {
                        Text("+ Create Bot", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3D8EFF), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (agents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(20.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Agents Configured Yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap Create Bot above to add your first bot in seconds, or toggle Wi-Fi discovery to connect local models.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(agents) { agent ->
                    AgentDirectoryCard(agent = agent, onClick = { onEditAgentClick(agent.id) })
                }
            }

            // FOOTER: Advanced Connection Features
            item {
                Spacer(modifier = Modifier.height(8.dp))
                AdvancedFeaturesFooterCard(onClick = onNavigateToAdvanced)
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun BotCreatorHeroCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF131D31))
            .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00D2FF).copy(alpha = 0.2f))
                    .border(1.5.dp, Color(0xFF00D2FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF00D2FF),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Bot Creator",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF00D2FF).copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "CREATOR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF00D2FF)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "Set up on-device identity, configure role preset & connect model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF00D2FF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BatonBotManagerHeroCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF131D31))
            .border(1.dp, Color(0xFF306EE8).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF306EE8).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF306EE8).copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF00D2FF),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "BatonBot Manager",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Lifecycle & Identity Hub",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Online",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun WifiDiscoveryControlCard(
    isDiscovering: Boolean,
    onToggle: (Boolean) -> Unit,
    discoveredCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141C2E))
            .border(
                1.dp,
                if (isDiscovering) Color(0xFF00D2FF).copy(alpha = 0.6f) else Color(0xFF26334D),
                RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDiscovering) Color(0xFF00D2FF).copy(alpha = 0.2f) else Color(0xFF1E293B)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDiscovering) Icons.Default.WifiFind else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (isDiscovering) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Wi-Fi Local Agent Finder",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        if (isDiscovering) "Scanning local subnet (auto-stops in 30s)..." else "Find Ollama & LM Studio on LAN",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDiscovering) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.6f)
                    )
                }

                // Explicit Battery-Saver Toggle Switch
                Switch(
                    checked = isDiscovering,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF00D2FF),
                        checkedTrackColor = Color(0xFF00D2FF).copy(alpha = 0.3f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }

            if (isDiscovering) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF00D2FF)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Battery Saver Active • Probing ports 11434, 1234, 8080",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFF00D2FF).copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredAgentCard(
    discovered: DiscoveredAgent,
    onConnectClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF16233B))
            .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF00D2FF).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFF00D2FF), modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                discovered.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                "${discovered.ipAddress}:${discovered.port}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        Button(
            onClick = onConnectClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Connect", color = Color(0xFF070B14), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun AdvancedFeaturesFooterCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141C2E))
            .border(1.dp, Color(0xFF26334D), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF3D8EFF), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Advanced Connection Features",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    "Tunnels, Optical Air-Gap QR, A2A Swarm Mesh, Keystore",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
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
        val avatarBg = try {
            Color(android.graphics.Color.parseColor(agent.colorAccent.takeIf { it.isNotBlank() } ?: "#306EE8"))
        } catch (_: Exception) {
            Color(0xFF306EE8)
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(avatarBg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = agent.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 22.sp,
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
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ROLE: ${agent.role}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ENGINE: ${agent.providerType.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
