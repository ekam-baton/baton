package com.ekam.baton.feature.agents.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekam.baton.core.ui.components.glassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedConnectionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTunnelSetup: () -> Unit,
    onNavigateToAirGapQr: () -> Unit,
    onNavigateToAirGapScanner: () -> Unit,
    onNavigateToA2A: () -> Unit,
    onNavigateToSwarmTopology: (String) -> Unit = {},
    onNavigateToKeyBackup: () -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF0A0F1D),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Advanced Connection Features",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            "Developer & Sovereign Protocols",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF3D8EFF)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1626))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Intro Callout
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF3D8EFF).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFF3D8EFF).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF3D8EFF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Enterprise & Power-User Controls",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "These advanced networking modes allow direct air-gap device pairing, cryptographic tunnel diagnostics, and decentralized swarm mesh routing without relying on central cloud intermediaries.",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // Section 1: Tunnel & Live WebRTC Inspector
            AdvancedFeatureCard(
                icon = Icons.Default.Sensors,
                iconColor = Color(0xFF00D2FF),
                title = "Tunnel & Signaling Inspector",
                badge = "MCP LIVE PROBE",
                description = "Configure Cloudflare / Ngrok tunnels for local models, test WebSocket pings, and monitor round-trip latency.",
                actions = listOf(
                    "Open Tunnel Inspector" to onNavigateToTunnelSetup
                )
            )

            // Section 2: Optical Air-Gap Handshake
            AdvancedFeatureCard(
                icon = Icons.Default.QrCodeScanner,
                iconColor = Color(0xFF10B981),
                title = "Optical Air-Gap Pairing",
                badge = "ZERO INTERNET",
                description = "Exchange WebRTC SDP offers and Ed25519 public keys via dense 2D QR matrix and laser camera reticle.",
                actions = listOf(
                    "Present QR Code" to onNavigateToAirGapQr,
                    "Scan Air-Gap QR" to onNavigateToAirGapScanner
                )
            )

            // Section 3: Autonomous A2A Swarm Mesh
            AdvancedFeatureCard(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                iconColor = Color(0xFFA855F7),
                title = "A2A Swarm Gossip Mesh",
                badge = "DECENTRALIZED",
                description = "Inter-agent cryptographic delegation, peer-to-peer gossip networking, and real-time 2.5D orbital topology.",
                actions = listOf(
                    "A2A Swarm Pairing" to onNavigateToA2A,
                    "View Swarm Topology" to { onNavigateToSwarmTopology("default_swarm") }
                )
            )

            // Section 4: Hardware Keystore & Key Revocation
            AdvancedFeatureCard(
                icon = Icons.Default.VpnKey,
                iconColor = Color(0xFFF59E0B),
                title = "Hardware Keystore & Seed Vault",
                badge = "AES-256-GCM",
                description = "Manage Android Keystore encrypted master keys, review audit logs, and trigger cryptographic token revocation.",
                actions = listOf(
                    "Open Key Backup Vault" to onNavigateToKeyBackup
                )
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AdvancedFeatureCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    badge: String,
    description: String,
    actions: List<Pair<String, () -> Unit>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f))
                    .border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = iconColor
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            description,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            actions.forEach { (label, onClick) ->
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, iconColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
