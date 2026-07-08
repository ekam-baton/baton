package com.ekam.baton.feature.agents.a2a.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekam.baton.core.data.model.AgentRole

@Composable
fun CardFront(
    agentId: String,
    agentName: String,
    role: AgentRole,
    modifier: Modifier = Modifier
) {
    val did = CardCrypto.generateAgentDID(agentId)
    val serial = CardCrypto.generateSerialNumber(agentId)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111116)) // Deep dark terminal background
            .border(1.dp, Color(0xFF333344), RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Holographic security strip on the left
            HolographicStrip()
            
            // Main Content Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header: Republic/System designation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BATON SYSTEM SOVEREIGN IDENTITY",
                        color = Color.Gray,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.weight(1f))
                    // Visual cryptographic block
                    CryptoFingerprint(agentId = agentId, modifier = Modifier.size(24.dp))
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Agent Name & Role
                Text(
                    text = agentName.uppercase(),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                
                Text(
                    text = role.name,
                    color = getRoleColor(role),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Spacer(Modifier.weight(1f))
                
                // Cryptographic DID and Serial
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "DID IDENTIFIER",
                        color = Color.DarkGray,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = did,
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "SERIAL NO.",
                                color = Color.DarkGray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = serial,
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        
                        Spacer(Modifier.weight(1f))
                        
                        // "Authorized" seal or chip
                        Box(
                            modifier = Modifier
                                .size(32.dp, 24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFD4AF37)) // Gold chip color
                                .border(1.dp, Color(0xFFA67C00), RoundedCornerShape(4.dp))
                        ) {
                            // Simple chip contacts pattern
                            Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                Box(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color(0xFFA67C00)))
                                Spacer(Modifier.width(2.dp))
                                Box(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color(0xFFA67C00)))
                                Spacer(Modifier.width(2.dp))
                                Box(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color(0xFFA67C00)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CryptoFingerprint(agentId: String, modifier: Modifier = Modifier) {
    val fingerprint = CardCrypto.generateVisualFingerprint(agentId)
    
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cellSize = size.width / 8f
        for (i in 0 until 8) {
            for (j in 0 until 8) {
                if (fingerprint[i * 8 + j]) {
                    drawRect(
                        color = Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(j * cellSize, i * cellSize),
                        size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                    )
                }
            }
        }
    }
}

private fun getRoleColor(role: AgentRole): Color {
    return when (role) {
        AgentRole.CODER -> Color(0xFF00FF88)
        AgentRole.RESEARCHER -> Color(0xFF00BFFF)
        AgentRole.SECURITY -> Color(0xFFFF3366)
        AgentRole.CREATIVE -> Color(0xFFFF00FF)
        AgentRole.MEMORY -> Color(0xFF00FFFF)
        AgentRole.ANALYST -> Color(0xFFFFD700)
        AgentRole.COORDINATOR -> Color(0xFFFFFFFF)
    }
}
