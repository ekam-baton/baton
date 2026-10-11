package com.ekam.baton.feature.agents.a2a

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekam.baton.core.ui.components.glassCard
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A2AScreen(
    onNavigateToQr: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToSwarm: (String) -> Unit = {},
    viewModel: A2AViewModel = koinViewModel()
) {
    val pendingRoleAgent by viewModel.pendingRoleAssignment.collectAsState()
    val activeSwarmId by viewModel.activeSwarmId.collectAsState()
    val swarmTopology by viewModel.swarmTopology.collectAsState()
    val dataChannelStatus by viewModel.dataChannelStatus.collectAsState()
    val burnRate by viewModel.burnRate.collectAsState()
    
    var devModeActive by remember { mutableStateOf(false) }
    
    if (pendingRoleAgent != null) {
        RoleAssignmentSheet(
            agentName = pendingRoleAgent!!.name,
            onRoleSelected = { role ->
                viewModel.assignRoleToPendingAgent(role)
            },
            onDismiss = {
                viewModel.dismissRoleAssignment()
            }
        )
    }

    // Swarm active animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val linkPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "linkPulse"
    )

    Scaffold(
        containerColor = Color(0xFF0D131F) // Deep Ocean Blue
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Telemetry Ribbon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "SWARM TELEMETRY",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Negotiation Active",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "BURN RATE",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = burnRate,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = Color(0xFFEF4444) // Warning Red for cost
                    )
                }
            }

            // Center Visualization Hub
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f),
                contentAlignment = Alignment.Center
            ) {
                // Background ambient ring
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(linkPulse)
                        .clip(CircleShape)
                        .background(Color(0xFF306EE8).copy(alpha = 0.1f)) // Electric Cobalt transparent
                )
                
                // Connecting lines / inner nodes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .glassCard(shape = RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Orchestrator",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(2.dp)
                            .background(Color(0xFF306EE8).copy(alpha = 0.5f))
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF306EE8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Hub, contentDescription = null, tint = Color.White)
                    }

                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(2.dp)
                            .background(Color(0xFF306EE8).copy(alpha = 0.5f))
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .glassCard(shape = RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Worker Node",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // WebRTC Architecture Dev Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { devModeActive = !devModeActive }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DeveloperMode, contentDescription = "Dev Mode", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WebRTC Architecture Diagnostics",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            // Dev Mode Panel
            if (devModeActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .glassCard(shape = RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    WebRtcMetricRow("Routing Topology", swarmTopology)
                    Spacer(modifier = Modifier.height(6.dp))
                    WebRtcMetricRow("Signaling Server", if(swarmTopology == "Detached") "Detached" else "Active")
                    Spacer(modifier = Modifier.height(6.dp))
                    WebRtcMetricRow("Data Channel (DC)", dataChannelStatus)
                    Spacer(modifier = Modifier.height(6.dp))
                    WebRtcMetricRow("Stream Channel", "Muted (Context Phase)")
                }
            } else {
                // Live Log Console (Only visible when Dev Mode is collapsed)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text("> Establishing sub-channel protocols...", color = Color.Gray, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        Text("> Worker Node bound to generic intent.", color = Color.Gray, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        Text("> Exchanging context vectors (42.1kb)", color = Color(0xFF306EE8), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                        Text("> Negotiating outcome parameters...", color = Color.White, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                    }
                }
            }

            // Swarm Console Navigation if active
            if (activeSwarmId != null) {
                Button(
                    onClick = { activeSwarmId?.let { onNavigateToSwarm(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF306EE8)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp)
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ENTER SWARM THREAD",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Air-Gapped Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToQr,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("SHOW QR OFFER", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace))
                }
                
                Button(
                    onClick = onNavigateToScanner,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("SCAN PEER", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
                }
            }

            // Massive Kill Switch
            Button(
                onClick = { viewModel.forceHaltSwarm() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626).copy(alpha = 0.8f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .height(64.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "HALT SWARM",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun WebRtcMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
