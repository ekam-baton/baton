package com.ekam.baton.feature.chat

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Security
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
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.ui.components.glassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSwarmBottomSheet(
    agents: List<Agent>,
    onDeploySwarm: (name: String, leaderAgentId: String, workerAgentIds: List<String>, topologyType: String, intentContext: String) -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var swarmName by remember { mutableStateOf("Autonomous Swarm Cluster") }
    var intentContext by remember { mutableStateOf("Collaborative multi-agent consensus and task execution") }
    var selectedTopology by remember { mutableStateOf("P2P") } // P2P or MESH
    var selectedLeaderId by remember { mutableStateOf(agents.firstOrNull()?.id ?: "") }
    val selectedWorkerIds = remember { mutableStateListOf<String>() }

    // Pre-select some workers if available
    LaunchedEffect(agents) {
        if (selectedWorkerIds.isEmpty() && agents.size > 1) {
            agents.drop(1).take(2).forEach { selectedWorkerIds.add(it.id) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF0D131F),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF306EE8)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF306EE8).copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Hub,
                            contentDescription = null,
                            tint = Color(0xFF00D2FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "DEPLOY AGENT SWARM",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "Orchestrate sovereign multi-agent mesh clusters",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }

            // Cluster Name
            OutlinedTextField(
                value = swarmName,
                onValueChange = { swarmName = it },
                label = { Text("Cluster Name", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00D2FF),
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Intent Context
            OutlinedTextField(
                value = intentContext,
                onValueChange = { intentContext = it },
                label = { Text("Swarm Intent & Objective", color = Color.Gray) },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00D2FF),
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Topology Selector
            Text(
                text = "TOPOLOGY ARCHITECTURE",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF00D2FF)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("P2P", "MESH", "SFU").forEach { topo ->
                    val isSelected = selectedTopology == topo
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTopology = topo },
                        label = { Text(topo, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF306EE8),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF131D31),
                            labelColor = Color.LightGray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Leader Selection
            Text(
                text = "SWARM LEADER (COORDINATOR)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF00D2FF)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(agents) { agent ->
                    val isLeader = selectedLeaderId == agent.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLeader) Color(0xFF306EE8).copy(alpha = 0.35f) else Color(0xFF131D31),
                        modifier = Modifier
                            .clickable {
                                selectedLeaderId = agent.id
                                selectedWorkerIds.remove(agent.id)
                            }
                            .border(
                                width = if (isLeader) 1.5.dp else 1.dp,
                                color = if (isLeader) Color(0xFF00D2FF) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(10.dp)
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF306EE8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = agent.name.firstOrNull()?.uppercase() ?: "?",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = agent.name,
                                color = if (isLeader) Color.White else Color.LightGray,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isLeader) FontWeight.Bold else FontWeight.Normal)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Worker Selection
            Text(
                text = "WORKER NODES (MULTI-SELECT)",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF00D2FF)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(agents.filter { it.id != selectedLeaderId }) { agent ->
                    val isWorker = selectedWorkerIds.contains(agent.id)
                    FilterChip(
                        selected = isWorker,
                        onClick = {
                            if (isWorker) selectedWorkerIds.remove(agent.id)
                            else selectedWorkerIds.add(agent.id)
                        },
                        label = { Text(agent.name) },
                        leadingIcon = if (isWorker) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00D2FF).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFF00D2FF),
                            containerColor = Color(0xFF131D31),
                            labelColor = Color.LightGray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Deploy Button
            Button(
                onClick = {
                    onDeploySwarm(
                        swarmName,
                        selectedLeaderId,
                        selectedWorkerIds.toList(),
                        selectedTopology,
                        intentContext
                    )
                },
                enabled = selectedLeaderId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF306EE8)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Hub, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "INITIALIZE SWARM CLUSTER",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
            }
        }
    }
}
