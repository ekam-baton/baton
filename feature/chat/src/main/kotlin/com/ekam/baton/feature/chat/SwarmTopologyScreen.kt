package com.ekam.baton.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.AgentRole
import com.ekam.baton.core.data.model.WorldRoom
import com.ekam.baton.core.data.model.WorldRoomProp
import com.ekam.baton.core.ui.components.glassCard
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwarmTopologyScreen(
    swarmId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAgentDetails: (String) -> Unit = {},
    viewModel: SwarmTopologyViewModel = koinViewModel()
) {
    val swarm by viewModel.swarm.collectAsStateWithLifecycle()
    val memberAgents by viewModel.memberAgents.collectAsStateWithLifecycle()
    val totalTokens by viewModel.totalTokens.collectAsStateWithLifecycle()
    val totalCostUsd by viewModel.totalCostUsd.collectAsStateWithLifecycle()
    val tokenLedgerEntries by viewModel.tokenLedgerEntries.collectAsStateWithLifecycle()
    val selectedAgentId by viewModel.selectedAgentId.collectAsStateWithLifecycle()
    val selectedAgent by viewModel.selectedAgent.collectAsStateWithLifecycle()
    val currentRoom by viewModel.currentRoom.collectAsStateWithLifecycle()
    val currentProps by viewModel.currentProps.collectAsStateWithLifecycle()

    var showDisbandDialog by remember { mutableStateOf(false) }

    if (showDisbandDialog) {
        AlertDialog(
            onDismissRequest = { showDisbandDialog = false },
            title = { Text("Disband Swarm Cluster", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to halt this cluster? All P2P tunnels will be disconnected.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDisbandDialog = false
                        viewModel.disbandSwarm(onDisbanded = onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Halt Cluster", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisbandDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = Color(0xFF0D131F),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = Color(0xFF00D2FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = swarm?.name ?: "Swarm Cluster",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                        Text(
                            text = if (swarm?.isActive == true) "ORCHESTRATION ACTIVE" else "DISBANDED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            ),
                            color = if (swarm?.isActive == true) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (swarm?.isActive == true) {
                        IconButton(onClick = { showDisbandDialog = true }) {
                            Icon(Icons.Default.StopCircle, contentDescription = "Halt Swarm", tint = Color(0xFFEF4444))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D131F).copy(alpha = 0.95f)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Section 1: Telemetry Banner ───────────────────────────────────────────
            item {
                SwarmTelemetryBanner(
                    topologyType = swarm?.topologyType ?: "P2P",
                    totalTokens = totalTokens,
                    totalCostUsd = totalCostUsd,
                    nodeCount = memberAgents.size,
                    onToggleTopology = { newType -> viewModel.updateTopology(newType) }
                )
            }

            // ── Section 2: Interactive Topology Node Graph ────────────────────────────
            item {
                Text(
                    text = "NETWORK TOPOLOGY GRAPH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF00D2FF)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TopologyGraphCard(
                    leaderId = swarm?.leaderAgentId ?: "",
                    memberAgents = memberAgents,
                    selectedAgentId = selectedAgentId,
                    onSelectAgent = { viewModel.selectAgent(it) }
                )
            }

            // ── Section 3: 2.5D World Room Visualizer ─────────────────────────────────
            item {
                val agent = selectedAgent
                if (agent != null) {
                    Text(
                        text = "2.5D WORLD ROOM • ${agent.name.uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WorldRoomVisualizerCard(
                        agent = agent,
                        room = currentRoom,
                        props = currentProps,
                        onViewDetails = { onNavigateToAgentDetails(agent.id) }
                    )
                }
            }

            // ── Section 4: Intent Context & Token Ledger ─────────────────────────────
            item {
                Text(
                    text = "ORCHESTRATION INTENT & LEDGER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                IntentAndLedgerCard(
                    intentContext = swarm?.intentContext ?: "Autonomous consensus & task execution",
                    ledgerEntries = tokenLedgerEntries
                )
            }
        }
    }
}

@Composable
private fun SwarmTelemetryBanner(
    topologyType: String,
    totalTokens: Int,
    totalCostUsd: Double,
    nodeCount: Int,
    onToggleTopology: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF306EE8).copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = Color(0xFF00D2FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Cluster Telemetry",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "$nodeCount Active Nodes Linked",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                // Topology Switcher Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D131F))
                        .padding(2.dp)
                ) {
                    val isP2P = topologyType.equals("P2P", ignoreCase = true)
                    Text(
                        text = "P2P",
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isP2P) Color(0xFF306EE8) else Color.Transparent)
                            .clickable { onToggleTopology("P2P") }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (isP2P) Color.White else Color.Gray
                    )
                    Text(
                        text = "MESH",
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isP2P) Color(0xFF306EE8) else Color.Transparent)
                            .clickable { onToggleTopology("MESH") }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (!isP2P) Color.White else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF233048))
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("TOTAL TOKENS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        text = "%,d tk".format(totalTokens),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFF00D2FF)
                    )
                }
                Column {
                    Text("ESTIMATED COST", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        text = "$%.4f USD".format(totalCostUsd),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFF10B981)
                    )
                }
                Column {
                    Text("STATUS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        text = "E2EE Verified",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFFFFA040)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopologyGraphCard(
    leaderId: String,
    memberAgents: List<Agent>,
    selectedAgentId: String?,
    onSelectAgent: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121B2B))
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulsePhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulsePhase"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val workerAgents = memberAgents.filter { it.id != leaderId }

                // Draw connecting orbital paths
                if (workerAgents.isNotEmpty()) {
                    val radius = (size.minDimension / 2f) * 0.65f

                    workerAgents.forEachIndexed { index, _ ->
                        val angle = (2 * Math.PI * index / workerAgents.size) - (Math.PI / 2)
                        val wx = cx + (radius * cos(angle)).toFloat()
                        val wy = cy + (radius * sin(angle)).toFloat()

                        // Line from leader to worker
                        drawLine(
                            color = Color(0xFF306EE8).copy(alpha = 0.5f),
                            start = Offset(cx, cy),
                            end = Offset(wx, wy),
                            strokeWidth = 2.dp.toPx()
                        )

                        // Data packet pulse dot
                        val packetProgress = (pulsePhase + (index.toFloat() / workerAgents.size)) % 1f
                        val px = cx + (wx - cx) * packetProgress
                        val py = cy + (wy - cy) * packetProgress
                        drawCircle(
                            color = Color(0xFF00D2FF),
                            radius = 4.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // Interactive Node Buttons placed in Box
            val leader = memberAgents.find { it.id == leaderId } ?: memberAgents.firstOrNull()
            val workerAgents = memberAgents.filter { it.id != leaderId }

            // Leader in center
            if (leader != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    NodeChip(
                        agent = leader,
                        isLeader = true,
                        isSelected = leader.id == selectedAgentId,
                        onClick = { onSelectAgent(leader.id) }
                    )
                }
            }

            // Workers arranged radially
            if (workerAgents.isNotEmpty()) {
                val radiusDp = 75.dp
                workerAgents.forEachIndexed { index, worker ->
                    val angle = (2 * Math.PI * index / workerAgents.size) - (Math.PI / 2)
                    val offsetX = (radiusDp.value * cos(angle)).dp
                    val offsetY = (radiusDp.value * sin(angle)).dp

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .offset(x = offsetX, y = offsetY)
                    ) {
                        NodeChip(
                            agent = worker,
                            isLeader = false,
                            isSelected = worker.id == selectedAgentId,
                            onClick = { onSelectAgent(worker.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeChip(
    agent: Agent,
    isLeader: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(agent.colorAccent))
    } catch (_: Exception) {
        if (isLeader) Color(0xFF306EE8) else Color(0xFF00D2FF)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF0D131F),
            border = androidx.compose.foundation.BorderStroke(
                width = if (isSelected) 3.dp else 1.5.dp,
                color = if (isSelected) Color(0xFF00D2FF) else accentColor
            ),
            modifier = Modifier.size(if (isLeader) 46.dp else 38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = agent.name.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isLeader) "★ ${agent.name.take(8)}" else agent.name.take(8),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 10.sp
            ),
            color = if (isSelected) Color(0xFF00D2FF) else Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun WorldRoomVisualizerCard(
    agent: Agent,
    room: WorldRoom?,
    props: List<WorldRoomProp>,
    onViewDetails: () -> Unit
) {
    val roleColor = try {
        Color(android.graphics.Color.parseColor(agent.colorAccent))
    } catch (_: Exception) {
        Color(0xFF306EE8)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141E2F))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Room Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = room?.displayName ?: agent.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = roleColor.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = room?.role?.displayName ?: agent.role,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = roleColor
                            )
                        }
                    }
                    Text(
                        text = "Iso Space: Grid (${room?.worldX ?: 0}, ${room?.worldY ?: 0}) • ${props.size} Active Props",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                IconButton(onClick = onViewDetails) {
                    Icon(Icons.Default.Info, contentDescription = "Agent Details", tint = Color(0xFF00D2FF))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2.5D Isometric Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0B101A))
            ) {
                IsometricCanvas(room = room, props = props, accentColor = roleColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Props Roster
            Text(
                text = "ROOM FURNITURE & MCP ARTIFACTS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(props) { prop ->
                    PropChip(prop = prop)
                }
            }
        }
    }
}

@Composable
private fun IsometricCanvas(
    room: WorldRoom?,
    props: List<WorldRoomProp>,
    accentColor: Color
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.45f
        val tileW = 28f
        val tileH = 14f

        val gridW = room?.widthTiles ?: 6
        val gridH = room?.heightTiles ?: 6

        // Draw Isometric Grid Floor
        for (gx in 0 until gridW) {
            for (gy in 0 until gridH) {
                val isoX = cx + (gx - gy) * tileW
                val isoY = cy + (gx + gy) * tileH

                val path = Path().apply {
                    moveTo(isoX, isoY)
                    lineTo(isoX + tileW, isoY + tileH)
                    lineTo(isoX, isoY + tileH * 2)
                    lineTo(isoX - tileW, isoY + tileH)
                    close()
                }

                drawPath(
                    path = path,
                    color = Color(0xFF141F33),
                    style = Stroke(width = 1f)
                )
            }
        }

        // Draw Ambient Glow at Room Origin
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(cx, cy + (gridW * tileH)),
                radius = 120f
            ),
            radius = 120f,
            center = Offset(cx, cy + (gridW * tileH))
        )

        // Draw 2.5D Props projected into Iso Space
        props.forEach { prop ->
            val px = cx + (prop.gridX - prop.gridY) * tileW
            val py = cy + (prop.gridX + prop.gridY) * tileH - (prop.gridZ * 18f)

            // Prop Base Shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.4f),
                topLeft = Offset(px - 14f, py + 8f),
                size = androidx.compose.ui.geometry.Size(28f, 12f)
            )

            // Prop Isometric Isometric Cube / Artifact
            val propPath = Path().apply {
                moveTo(px, py - 18f)
                lineTo(px + 12f, py - 10f)
                lineTo(px + 12f, py + 4f)
                lineTo(px, py - 4f)
                close()
            }
            drawPath(path = propPath, color = accentColor.copy(alpha = 0.85f))

            val propLeftPath = Path().apply {
                moveTo(px, py - 18f)
                lineTo(px - 12f, py - 10f)
                lineTo(px - 12f, py + 4f)
                lineTo(px, py - 4f)
                close()
            }
            drawPath(path = propLeftPath, color = accentColor.copy(alpha = 0.6f))

            val propTopPath = Path().apply {
                moveTo(px, py - 18f)
                lineTo(px + 12f, py - 10f)
                lineTo(px, py - 2f)
                lineTo(px - 12f, py - 10f)
                close()
            }
            drawPath(path = propTopPath, color = accentColor)
        }
    }
}

@Composable
private fun PropChip(prop: WorldRoomProp) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0D131F),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF233048))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Category,
                contentDescription = null,
                tint = Color(0xFF00D2FF),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = prop.propType.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "(${prop.gridX.toInt()}, ${prop.gridY.toInt()}, ${prop.gridZ.toInt()})",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun IntentAndLedgerCard(
    intentContext: String,
    ledgerEntries: List<TokenLedgerEntity>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141E2F))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFFFFA040),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Swarm Intent Pipeline",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = intentContext,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )

            if (ledgerEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF233048))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "RECENT IN-FLIGHT TRANSACTIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                ledgerEntries.take(4).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = entry.actionContext,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${entry.modelName} • ${entry.totalTokens} tokens",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = "$%.5f".format(entry.estimatedCostUsd),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
        }
    }
}
