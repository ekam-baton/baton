package com.ekam.baton.feature.agents

import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.network.mcp.McpTool
import com.ekam.baton.core.network.mcp.ToolAuthorizationRequest
import com.ekam.baton.core.ui.components.glassCard
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.ekam.baton.feature.agents.qr.AgentPairingQrDialog
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailsScreen(
    agentId: String,
    onNavigateBack: () -> Unit,
    onEditClick: (String) -> Unit,
    onMemoryVaultClick: (String) -> Unit,
    onNavigateToBotManager: (String) -> Unit = {},
    viewModel: AgentDetailsViewModel = koinViewModel()
) {
    LaunchedEffect(agentId) {
        viewModel.loadAgent(agentId)
    }

    val agent by viewModel.agent.collectAsStateWithLifecycle()
    val tools by viewModel.tools.collectAsStateWithLifecycle()
    val isLoadingTools by viewModel.isLoadingTools.collectAsStateWithLifecycle()
    val toolsError by viewModel.toolsError.collectAsStateWithLifecycle()
    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()
    val testParamsInput by viewModel.testParamsInput.collectAsStateWithLifecycle()
    val isExecutingTool by viewModel.isExecutingTool.collectAsStateWithLifecycle()
    val testExecutionOutput by viewModel.testExecutionOutput.collectAsStateWithLifecycle()
    val testExecutionError by viewModel.testExecutionError.collectAsStateWithLifecycle()
    val pingLatencyMs by viewModel.pingLatencyMs.collectAsStateWithLifecycle()
    val isPinging by viewModel.isPinging.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var expandedToolName by remember { mutableStateOf<String?>(null) }
    var showDecommissionDialog by remember { mutableStateOf(false) }
    var showPairingQrDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Intercept tool authorization requests during diagnostic execution
    var activeAuthRequest by remember { mutableStateOf<ToolAuthorizationRequest?>(null) }
    LaunchedEffect(Unit) {
        viewModel.toolAuthRequests.collect { req ->
            activeAuthRequest = req
        }
    }

    if (activeAuthRequest != null) {
        val req = activeAuthRequest!!
        AlertDialog(
            onDismissRequest = {
                viewModel.resolveToolAuth(req, false)
                activeAuthRequest = null
            },
            containerColor = Color(0xFF131E31),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Authorize Destructive Tool", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "The tool \"${req.toolName}\" modifies external state or executes commands.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Arguments:",
                        color = Color(0xFF00D2FF),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF090E17))
                            .padding(10.dp)
                    ) {
                        Text(
                            req.arguments.toString(),
                            color = Color.White.copy(alpha = 0.9f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resolveToolAuth(req, true)
                        activeAuthRequest = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Authorize & Run", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.resolveToolAuth(req, false)
                        activeAuthRequest = null
                    }
                ) {
                    Text("Deny", color = Color(0xFFDC2626))
                }
            }
        )
    }

    if (agent == null) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D131F)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00D2FF))
        }
        return
    }

    val currentAgent = agent!!

    Scaffold(
        containerColor = Color(0xFF0D131F),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            currentAgent.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "AGENT DETAILS & TOOLS",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
                            color = Color(0xFF00D2FF)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showPairingQrDialog = true }) {
                        Icon(Icons.Default.QrCode2, contentDescription = "Show Pairing QR", tint = Color(0xFF00D2FF))
                    }
                    IconButton(onClick = { onNavigateToBotManager(agentId) }) {
                        Icon(Icons.Default.SmartToy, contentDescription = "Bot Manager", tint = Color.White)
                    }
                    IconButton(onClick = { onEditClick(agentId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Configuration", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D131F).copy(alpha = 0.85f)
                )
            )
        },
        bottomBar = {
            if (showDecommissionDialog) {
                AlertDialog(
                    onDismissRequest = { showDecommissionDialog = false },
                    containerColor = Color(0xFF131E31),
                    title = {
                        Text("Delete Agent", color = Color.White, fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Text(
                            "Are you sure you want to delete \"${currentAgent.name}\"? This will remove it from your agent directory and clear local sessions.",
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDecommissionDialog = false
                                viewModel.deleteAgent {
                                    onNavigateBack()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDecommissionDialog = false }) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                )
            }
            if (showReportDialog) {
                ReportAgentDialog(
                    agentName = currentAgent.name,
                    onDismiss = { showReportDialog = false },
                    onSubmit = { reason, details ->
                        viewModel.reportAgent(reason, details) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Agent reported. Thank you for your feedback.")
                            }
                        }
                        showReportDialog = false
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.toggleBlock {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (currentAgent.isBlocked) "Agent unblocked" else "Agent blocked"
                                    )
                                }
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (currentAgent.isBlocked) Color(0xFF00D2FF) else Color(0xFFF59E0B)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (currentAgent.isBlocked) Color(0xFF00D2FF).copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (currentAgent.isBlocked) "UNBLOCK" else "BLOCK",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                    }
                    OutlinedButton(
                        onClick = { showReportDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "REPORT",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                    }
                }
                Button(
                    onClick = { showDecommissionDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "DELETE AGENT",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Profile & Latency Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .glassCard(shape = RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val accentColor = try {
                            Color(android.graphics.Color.parseColor(currentAgent.colorAccent.takeIf { it.isNotBlank() } ?: "#306EE8"))
                        } catch (e: Exception) {
                            Color(0xFF306EE8)
                        }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(accentColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentAgent.name.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentAgent.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (pingLatencyMs != null && pingLatencyMs!! >= 0) Color(0xFF10B981) else Color(0xFFF59E0B))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (pingLatencyMs != null && pingLatencyMs!! >= 0) "ONLINE" else "STANDBY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                    color = if (pingLatencyMs != null && pingLatencyMs!! >= 0) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                            }
                        }

                        // Ping Probe Action
                        Surface(
                            onClick = { viewModel.pingEndpoint() },
                            color = Color(0xFF19253B),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00D2FF).copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isPinging) {
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color(0xFF00D2FF))
                                } else {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF00D2FF), modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = when {
                                        isPinging -> "PROBING..."
                                        pingLatencyMs == null -> "PING"
                                        pingLatencyMs!! < 0 -> "OFFLINE"
                                        else -> "${pingLatencyMs}ms"
                                    },
                                    color = when {
                                        pingLatencyMs != null && pingLatencyMs!! >= 0 -> Color(0xFF10B981)
                                        pingLatencyMs != null && pingLatencyMs!! < 0 -> Color(0xFFDC2626)
                                        else -> Color(0xFF00D2FF)
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Quick Profile Operations Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Bot Manager Jump
                        FilledTonalButton(
                            onClick = { onNavigateToBotManager(agentId) },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF3D8EFF).copy(alpha = 0.2f),
                                contentColor = Color(0xFF3D8EFF)
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bot Manager", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // 2. Pairing QR
                        FilledTonalButton(
                            onClick = { showPairingQrDialog = true },
                            modifier = Modifier.weight(1f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF00D2FF).copy(alpha = 0.2f),
                                contentColor = Color(0xFF00D2FF)
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pairing QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // 3. Memory Vault
                        FilledTonalButton(
                            onClick = { onMemoryVaultClick(agentId) },
                            modifier = Modifier.weight(0.9f).height(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                contentColor = Color(0xFF8B5CF6)
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Memory", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Tab Navigation Row
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
                divider = { HorizontalDivider(color = Color.White.copy(alpha = 0.08f)) }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("CAPABILITIES (${tools.size})", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("SECURITY & IDENTITY", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                )
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> {
                    // TAB 0: MCP Capabilities & Tool Testing Console
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Action row for refreshing capabilities
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "ADVERTISED MCP TOOLS",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            FilledTonalButton(
                                onClick = { viewModel.refreshTools() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF306EE8).copy(alpha = 0.2f),
                                    contentColor = Color(0xFF00D2FF)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoadingTools) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF00D2FF))
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("REFRESH TOOLS", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (toolsError != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFDC2626).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                                    Text(
                                        text = toolsError ?: "",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                                    )
                                }
                            }
                        }

                        if (tools.isEmpty() && !isLoadingTools && toolsError == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard(shape = RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Construction, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(36.dp))
                                    Text("No MCP tools discovered yet", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Tap 'REFRESH SCHEMA' to probe the agent's MCP endpoint for tools/list.",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // Tool Cards
                        tools.forEach { tool ->
                            val isDestructive = viewModel.isDestructiveTool(tool.name)
                            val isExpanded = expandedToolName == tool.name

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard(shape = RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tool.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = tool.description ?: "No description provided by MCP manifest.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Destructive vs Safe Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isDestructive) Color(0xFFDC2626).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isDestructive) "DESTRUCTIVE" else "SAFE",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDestructive) Color(0xFFF87171) else Color(0xFF34D399)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Schema Inspector Toggle & Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            expandedToolName = if (isExpanded) null else tool.name
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = Color(0xFF00D2FF),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (isExpanded) "Hide Parameter Schema" else "Inspect Parameter Schema",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = Color(0xFF00D2FF)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.selectTool(tool)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF306EE8)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("TEST RUN", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Expanded Schema View
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF0A0F1A))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "JSON SCHEMA PROPERTIES:",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )

                                        val properties = tool.inputSchema["properties"] as? JsonObject
                                        val requiredList = (tool.inputSchema["required"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

                                        if (properties == null || properties.isEmpty()) {
                                            Text(
                                                "No input arguments required (Void schema).",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.7f)
                                            )
                                        } else {
                                            properties.forEach { (propName, propDef) ->
                                                val propObj = propDef as? JsonObject
                                                val type = propObj?.get("type")?.jsonPrimitive?.content ?: "any"
                                                val desc = propObj?.get("description")?.jsonPrimitive?.content
                                                val isReq = requiredList.contains(propName)

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                propName,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF00D2FF),
                                                                fontSize = 12.sp
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                ": $type",
                                                                fontFamily = FontFamily.Monospace,
                                                                color = Color.White.copy(alpha = 0.6f),
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                        if (desc != null) {
                                                            Text(
                                                                desc,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = Color.White.copy(alpha = 0.6f),
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    if (isReq) {
                                                        Text(
                                                            "REQUIRED",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFF59E0B)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Interactive Test Execution Console (Shows when selectedTool != null)
                        if (selectedTool != null) {
                            val tool = selectedTool!!
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF090E17))
                                    .border(1.5.dp, Color(0xFF00D2FF).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF00D2FF), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                "TOOL RUNNER",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00D2FF)
                                            )
                                            Text(
                                                tool.name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                                color = Color.White
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.clearSelectedTool() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                                    }
                                }

                                Text(
                                    "ARGUMENTS (JSON):",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f)
                                )

                                OutlinedTextField(
                                    value = testParamsInput,
                                    onValueChange = { viewModel.updateTestParams(it) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp, max = 160.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00D2FF),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedContainerColor = Color(0xFF05080F),
                                        unfocusedContainerColor = Color(0xFF05080F)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { viewModel.selectTool(tool) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Reset Skeleton", color = Color.White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = { viewModel.executeSelectedTool() },
                                        enabled = !isExecutingTool,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        if (isExecutingTool) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("EXECUTING...", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        } else {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("RUN TOOL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }

                                if (testExecutionError != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFDC2626).copy(alpha = 0.2f))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            testExecutionError ?: "",
                                            color = Color(0xFFFCA5A5),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (testExecutionOutput != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF05080F))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "EXECUTION RESPONSE:",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(testExecutionOutput ?: ""))
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Text(
                                            text = testExecutionOutput ?: "",
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }

                1 -> {
                    // TAB 1: Node Topology, Posture & Storage
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Objective Box
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text("INSTRUCTIONS / SYSTEM PROMPT", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentAgent.description.takeIf { it.isNotBlank() } ?: "No system instructions provided for this agent.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }

                        // Diagnostics Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Networking Provider
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .glassCard(shape = RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF00D2FF), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("PROVIDER", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentAgent.providerType.uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Security Posture
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .glassCard(shape = RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (currentAgent.securityMode.equals("sovereign", ignoreCase = true)) Color(0xFFDC2626) else Color(0xFF10B981),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("SECURITY", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = Color.White.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentAgent.securityMode.uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Endpoint & Relay Details
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("CONNECTION ENDPOINT", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.tertiary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentAgent.mcpEndpointUrl.takeIf { it.isNotBlank() } ?: "Local In-Process / Air-Gap Only",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                if (currentAgent.mcpEndpointUrl.isNotBlank()) {
                                    IconButton(
                                        onClick = { clipboardManager.setText(AnnotatedString(currentAgent.mcpEndpointUrl)) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF00D2FF), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (!currentAgent.relayUrl.isNullOrBlank()) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                Text("RELAY ROUTE", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.tertiary)
                                Text(
                                    text = currentAgent.relayUrl ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }

                            if (!currentAgent.ownerName.isNullOrBlank()) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                Text("OPERATOR IDENTITY", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.tertiary)
                                Text(
                                    text = currentAgent.ownerName ?: "",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                            }
                        }

                        // Memory Access Button
                        OutlinedButton(
                            onClick = { onMemoryVaultClick(agentId) },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00D2FF).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF00D2FF))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("VIEW MEMORY VAULT", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                                }
                                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.5f))
                            }
                        }

                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }

    if (showPairingQrDialog && agent != null) {
        AgentPairingQrDialog(
            agent = currentAgent,
            onDismiss = { showPairingQrDialog = false },
            onNavigateToManager = { onNavigateToBotManager(agentId) }
        )
    }
}
