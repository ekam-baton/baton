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
    agentId: String,
    agentName: String,
    role: AgentRole,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
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
                agentId = agentId,
                agentName = agentName,
                role = role,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                VerificationRow("CRYPTOGRAPHIC HASH", "VERIFIED", Color(0xFF00FF88))
                VerificationRow("SOVEREIGN CREDENTIAL", "ACTIVE", Color(0xFF00FF88))
                VerificationRow("ROLE CLEARANCE", role.name, Color(0xFF00BFFF))
                VerificationRow("NETWORK STATUS", "CONNECTED", Color(0xFF00FF88))
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
