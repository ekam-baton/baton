package com.ekam.baton.feature.agents.tunnel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.network.tunnel.Status
import com.ekam.baton.core.ui.components.glassCard
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelSetupGuideScreen(
    onNavigateBack: () -> Unit,
    viewModel: TunnelSetupViewModel = koinViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Live Inspector", "Tunnel Setup", "Security Modes", "Key Revocation")

    var testUrl by remember { mutableStateOf("") }
    val validationResult by viewModel.validationResult.collectAsStateWithLifecycle()
    val isValidating by viewModel.isValidating.collectAsStateWithLifecycle()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val tunnelHealthMap by viewModel.tunnelHealthMap.collectAsStateWithLifecycle()
    val isProbingAll by viewModel.isProbingAll.collectAsStateWithLifecycle()

    val tunnelAgents = remember(agents) {
        agents.filter { it.mcpEndpointUrl.isNotBlank() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tunnel & Connection Inspector",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "WebRTC Signaling & MCP Health",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (selectedTab == 0 && tunnelAgents.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.probeAllTunnelEndpoints() },
                            enabled = !isProbingAll
                        ) {
                            if (isProbingAll) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF00D2FF),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Probe All",
                                    tint = Color(0xFF00D2FF)
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D131F))
            )
        },
        containerColor = Color(0xFF0D131F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Header with horizontal scroll
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0D131F),
                contentColor = Color(0xFF00D2FF),
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF00D2FF),
                        height = 3.dp
                    )
                },
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (selectedTab == index) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // TAB 0: LIVE INSPECTOR
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Telemetry Summary Card
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard(shape = RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "CONNECTION POOL",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${viewModel.getActiveConnectionCount()} / 15 Active",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = Color(0xFF00D2FF)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "ENDPOINTS",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${tunnelAgents.size} Registered",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        }

                        // Section Title
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "REGISTERED AGENT TUNNELS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Button(
                                    onClick = { viewModel.probeAllTunnelEndpoints() },
                                    enabled = !isProbingAll && tunnelAgents.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF306EE8).copy(alpha = 0.3f),
                                        contentColor = Color(0xFF00D2FF)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        "PROBE ALL",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        if (tunnelAgents.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .glassCard(shape = RoundedCornerShape(16.dp))
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Sensors,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.3f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "NO TUNNEL AGENTS FOUND",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Add or configure agents with Cloudflare, ngrok, or VPS endpoints to stream live reachability & MCP telemetry.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(tunnelAgents, key = { it.id }) { agent ->
                                val health = tunnelHealthMap[agent.id]
                                val accentColor = remember(agent.colorAccent) {
                                    try {
                                        Color(android.graphics.Color.parseColor(agent.colorAccent))
                                    } catch (e: Exception) {
                                        Color(0xFF306EE8)
                                    }
                                }

                                AgentTunnelCard(
                                    agent = agent,
                                    agentAccent = accentColor,
                                    health = health,
                                    onProbeClick = {
                                        viewModel.probeSingleEndpoint(agent.id, agent.mcpEndpointUrl)
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: TUNNEL SETUP (GUIDE & TESTER)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Expose your local AI agents securely",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Since Baton is a local-first mobile client, your agent running on localhost must be exposed via a tunnel to be reachable from your device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        StepCard(
                            stepNumber = "A",
                            title = "Option 1: Cloudflare Tunnels (Recommended)",
                            description = "Cloudflare Tunnels create a secure, encrypted link between your agent and the Cloudflare Edge network without opening router ports. Expose your port 8080:"
                        ) {
                            TerminalCodeBlock(
                                command = "cloudflared tunnel --url http://localhost:8080"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Copy the generated public address (e.g., https://xxx.trycloudflare.com).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        StepCard(
                            stepNumber = "B",
                            title = "Option 2: Ngrok Tunnels",
                            description = "Ngrok is another quick alternative for exposing local environments. Run this in your terminal:"
                        ) {
                            TerminalCodeBlock(
                                command = "ngrok http 8080"
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Copy the forwarding address (e.g., https://xxx.ngrok-free.app).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        StepCard(
                            stepNumber = "C",
                            title = "Option 3: Direct Cloud Hosting",
                            description = "If your agent is hosted on a public VPS (AWS, GCP, Heroku, etc.), ensure it supports standard Model Context Protocol (MCP) HTTP Server-Sent Events (SSE) transport wrapper, and connect directly using its public secure HTTPS endpoint."
                        ) {}

                        // Test Connection Tool
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(shape = RoundedCornerShape(20.dp))
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Test Connection",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00D2FF)
                            )
                            Text(
                                text = "Paste your tunnel URL below to check if it's reachable and responds to MCP handshakes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedTextField(
                                value = testUrl,
                                onValueChange = { testUrl = it },
                                placeholder = { Text("https://xxx.trycloudflare.com") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00D2FF),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Button(
                                onClick = { viewModel.validateUrl(testUrl) },
                                enabled = testUrl.isNotBlank() && !isValidating,
                                modifier = Modifier.fillMaxWidth(),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (isValidating) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Testing Connection...")
                                } else {
                                    Text("Test Connection", fontWeight = FontWeight.Bold)
                                }
                            }

                            validationResult?.let { result ->
                                val (color, text) = when (result.status) {
                                    Status.VALID -> Color(0xFF10B981) to "✓ Connected — found ${result.serverName ?: "agent"}"
                                    Status.REACHABLE_NO_MCP -> Color(0xFFF59E0B) to "⚠ Reachable but no MCP detected. Check agent is running."
                                    Status.UNREACHABLE -> Color(0xFFEF4444) to "✗ Unreachable. Check URL and ensure tunnel is running."
                                    Status.INVALID_URL -> Color(0xFFEF4444) to "✗ Invalid URL format or internal host blocked."
                                }
                                Surface(
                                    color = color.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (result.error != null && result.status != Status.INVALID_URL) {
                                            Text(
                                                result.error!!,
                                                color = color.copy(alpha = 0.8f),
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: SECURITY MODES
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Authentication Protocols",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Baton supports multiple security protocols to adapt to your environment and authorization needs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        SecurityModeCard(
                            mode = "Standard (None)",
                            description = "No authentication headers are attached. Best suited for testing locally or when your tunnel is already secured behind IP whitelist rules/mTLS at the network layer.",
                            useCase = "Local hosting on your own home Wi-Fi."
                        )

                        SecurityModeCard(
                            mode = "API Key",
                            description = "Attaches a secret token in the authorization headers of every request. Your agent server verifies this key against its configurations.",
                            useCase = "Cloud-hosted agent endpoint shared within small groups."
                        )

                        SecurityModeCard(
                            mode = "OAuth 2.1",
                            description = "Allows secure integration with enterprise identity providers. Baton will launch a secure browser window for authenticating, fetching, and refreshing dynamic access tokens (JWTs).",
                            useCase = "Enterprise environments with centralized identity control (Auth0, Keycloak, etc.)."
                        )

                        SecurityModeCard(
                            mode = "Sovereign / Cryptographic Keys",
                            description = "Maximum decentralized privacy. Baton generates a unique X25519 public/private key pair inside the device's hardware enclave. The client signs each request, and the gateway validates it against its trusted key list.",
                            useCase = "Sovereign peer-to-peer setup where you don't trust static API passwords."
                        )
                    }
                }

                3 -> {
                    // TAB 3: KEY REVOCATION
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Revoking access for individual users",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "To support multiple clients securely without using a single master password, Baton's gateway engine supports user-specific key revocation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        StepCard(
                            stepNumber = "1",
                            title = "The Gateway Database",
                            description = "The gateway server references 'trusted_clients.json' containing public keys. The administrator can revoke a user instantly by flipping their status to false:"
                        ) {
                            TerminalCodeBlock(
                                command = """
                                [
                                  {
                                    "user_email": "user@example.com",
                                    "client_public_key": "8085a812b184e907de385ec5...",
                                    "is_active": false
                                  }
                                ]
                                """.trimIndent()
                            )
                        }

                        StepCard(
                            stepNumber = "2",
                            title = "Hot Reloading",
                            description = "The Rust Gateway Engine checks this database dynamically on every incoming connection. You do not need to restart the server for revocation to take effect."
                        ) {}

                        StepCard(
                            stepNumber = "3",
                            title = "Client Rejection",
                            description = "Once a key is marked is_active: false, the gateway aborts the cryptographic handshake and responds with HTTP 403 Forbidden. The app blocks access, safeguarding your agent ecosystem."
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
fun AgentTunnelCard(
    agent: Agent,
    agentAccent: Color,
    health: TunnelHealth?,
    onProbeClick: () -> Unit
) {
    val statusColor = when (health?.status) {
        Status.VALID -> Color(0xFF10B981) // Emerald
        Status.REACHABLE_NO_MCP -> Color(0xFFF59E0B) // Amber
        Status.UNREACHABLE, Status.INVALID_URL -> Color(0xFFEF4444) // Red
        null -> Color.Gray
    }

    val statusLabel = when (health?.status) {
        Status.VALID -> "ONLINE (MCP 2.0)"
        Status.REACHABLE_NO_MCP -> "HTTP ONLY"
        Status.UNREACHABLE -> "OFFLINE"
        Status.INVALID_URL -> "INVALID URL"
        null -> "UNPROBED"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(agentAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = statusColor
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Endpoint URL
        Text(
            text = agent.mcpEndpointUrl,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Footer: Latency & Probe Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (health != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${health.latencyMs} ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF00D2FF)
                    )
                }
            } else {
                Text(
                    text = "Tap test to ping",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onProbeClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    "PING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun StepCard(
    stepNumber: String,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF306EE8).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stepNumber,
                    color = Color(0xFF00D2FF),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
fun TerminalCodeBlock(
    command: String
) {
    val clipboardManager = LocalClipboardManager.current
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF00D2FF),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(command)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SecurityModeCard(
    mode: String,
    description: String,
    useCase: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = mode,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "BEST FOR:",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF00D2FF)
            )
            Text(
                text = useCase,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
