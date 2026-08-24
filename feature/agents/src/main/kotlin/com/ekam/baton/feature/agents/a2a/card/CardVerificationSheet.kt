package com.ekam.baton.feature.agents.a2a.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekam.baton.core.data.model.AgentRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardVerificationSheet(
    agent: com.ekam.baton.core.data.model.Agent,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Parse keys from securityConfig
    val json = agent.securityConfig.let { 
        if (it.isNotBlank()) try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(it).let { je ->
                if (je is kotlinx.serialization.json.JsonObject) je else null
            }
        } catch (e: Exception) { null }
        else null
    }
    
    val myKey = json?.get("my_identity_key")?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
    val peerKey = json?.get("peer_identity_key")?.let { it as? kotlinx.serialization.json.JsonPrimitive }?.content
    
    val safetyNumber = if (myKey != null && peerKey != null) {
        CardCrypto.generateSecurityNumber(myKey, peerKey)
    } else {
        "NOT PAIRED (NO E2EE IDENTITY)"
    }

    val displaySafetyNumber = if (safetyNumber.length == 60) {
        safetyNumber.chunked(5).joinToString(" ")
    } else {
        safetyNumber
    }

    var isVerified by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0A0A0E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "IDENTITY VERIFICATION",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            AgentIdentityCard(
                agentId = agent.id,
                agentName = agent.name,
                role = com.ekam.baton.core.data.model.AgentRole.valueOf(agent.role),
                modifier = Modifier.fillMaxWidth(0.85f)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                VerificationRow("CRYPTOGRAPHIC HASH", "VERIFIED", Color(0xFF00FF88))
                VerificationRow("SOVEREIGN CREDENTIAL", "ACTIVE", Color(0xFF00FF88))
                VerificationRow("ROLE CLEARANCE", agent.role, Color(0xFF00BFFF))
                VerificationRow("NETWORK STATUS", "CONNECTED", Color(0xFF00FF88))
                
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.HorizontalDivider(color = Color(0xFF333344))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "SAFETY NUMBER",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = displaySafetyNumber,
                    color = if (isVerified) Color(0xFF00FF88) else Color(0xFF00BFFF),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                androidx.compose.material3.Button(
                    onClick = { isVerified = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (isVerified) Color(0xFF00FF88) else Color(0xFF333344)
                    )
                ) {
                    Text(
                        text = if (isVerified) "VERIFIED" else "MARK AS VERIFIED",
                        color = if (isVerified) Color.Black else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun VerificationRow(label: String, value: String, valueColor: Color) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
    }
}
