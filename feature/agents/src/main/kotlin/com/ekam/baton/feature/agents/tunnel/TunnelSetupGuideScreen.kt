package com.ekam.baton.feature.agents.tunnel

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import com.ekam.baton.core.network.tunnel.Status
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelSetupGuideScreen(
    onNavigateBack: () -> Unit,
    viewModel: TunnelSetupViewModel = koinViewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tunnel Setup", "Security Modes", "Key Revocation")

    var testUrl by remember { mutableStateOf("") }
    val validationResult by viewModel.validationResult.collectAsStateWithLifecycle()
    val isValidating by viewModel.isValidating.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Guide", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Header
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        selectedContentColor = MaterialTheme.colorScheme.tertiary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tab Body (Scrollable Content)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // TAB 1: TUNNEL SETUP
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Expose your local AI agents securely",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
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
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Test Connection",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
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
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                                )

                                Button(
                                    onClick = { viewModel.validateUrl(testUrl) },
                                    enabled = testUrl.isNotBlank() && !isValidating,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = CircleShape
                                ) {
                                    if (isValidating) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Testing Connection...")
                                    } else {
                                        Text("Test Connection")
                                    }
                                }

                                validationResult?.let { result ->
                                    val (color, text) = when (result.status) {
                                        Status.VALID -> Color(0xFF32D74B) to "✓ Connected — found ${result.serverName ?: "agent"}"
                                        Status.REACHABLE_NO_MCP -> Color(0xFFFF9F0A) to "⚠ Reachable but no MCP detected. Check agent is running."
                                        Status.UNREACHABLE -> Color(0xFFFF453A) to "✗ Unreachable. Check URL and ensure cloudflared is running."
                                        Status.INVALID_URL -> Color(0xFFFF453A) to "✗ Invalid URL format."
                                    }
                                    Surface(
                                        color = color.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            if (result.error != null && result.status != Status.INVALID_URL) {
                                                Text(result.error!!, color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 2: SECURITY MODES
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Choose your authentication style",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Baton supports multiple security protocols to adapt to your environment and authorization needs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

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

                    2 -> {
                        // TAB 3: KEY REVOCATION
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Revoking access for individual users",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "To support multiple clients securely without using a single master password, Baton's gateway engine supports user-specific key revocation.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

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

                Spacer(modifier = Modifier.height(32.dp))
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stepNumber, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
fun TerminalCodeBlock(
    command: String
) {
    val clipboardManager = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = command,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { clipboardManager.setText(AnnotatedString(command)) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
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
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = mode,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Best For:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = useCase,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
