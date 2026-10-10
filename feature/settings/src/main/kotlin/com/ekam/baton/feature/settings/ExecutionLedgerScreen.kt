package com.ekam.baton.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.VerifiedUser
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
import com.ekam.baton.core.ui.components.glassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionLedgerScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = Color(0xFF0D131F), // Deep Ocean Blue
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Cryptographic Ledger",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Immutable E-Discovery Audit Trail",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D131F).copy(alpha = 0.85f)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Simulated Ledger Blocks
            item {
                LedgerBlockCard(
                    action = "INSERT",
                    entity = "MemoryEntity",
                    timestamp = "Oct 10, 2026 - 21:55:12",
                    hash = "0x8f3c...9a12",
                    isVerified = true
                )
            }
            item {
                LedgerBlockCard(
                    action = "UPDATE",
                    entity = "AgentEntity",
                    timestamp = "Oct 10, 2026 - 21:54:08",
                    hash = "0x2b11...e45f",
                    isVerified = true
                )
            }
            item {
                LedgerBlockCard(
                    action = "DELETE",
                    entity = "SwarmEntity",
                    timestamp = "Oct 10, 2026 - 21:35:40",
                    hash = "0x7c99...d33a",
                    isVerified = true
                )
            }
            item {
                LedgerBlockCard(
                    action = "INSERT",
                    entity = "TokenLedgerEntity",
                    timestamp = "Oct 10, 2026 - 21:34:02",
                    hash = "0x1a22...f88b",
                    isVerified = false // Simulating an unverified block
                )
            }
            
            // End of chain marker
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "GENESIS BLOCK REACHED",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerBlockCard(
    action: String,
    entity: String,
    timestamp: String,
    hash: String,
    isVerified: Boolean
) {
    val actionColor = when(action) {
        "INSERT" -> Color(0xFF10B981) // Emerald
        "UPDATE" -> Color(0xFF306EE8) // Cobalt
        "DELETE" -> Color(0xFFEF4444) // Red
        else -> Color.Gray
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline Dot & Line Segment
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(actionColor)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        // Ledger Data
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = action,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = actionColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entity,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Cryptographic Hash Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SHA256: $hash",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isVerified) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = "Verified", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                } else {
                    Text("UNVERIFIED", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = Color(0xFFF59E0B))
                }
            }
        }
    }
}
