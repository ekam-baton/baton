package com.ekam.baton.feature.agents.a2a

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekam.baton.core.data.model.AgentRole

/**
 * Bottom sheet to manually assign a role to a connected agent.
 * This determines the visual appearance of their room and avatar in the world.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleAssignmentSheet(
    agentName: String,
    onRoleSelected: (AgentRole) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedRole by remember { mutableStateOf<AgentRole?>(null) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0D14)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "ASSIGN ROLE : $agentName",
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            
            Text(
                text = "Select a specialization profile to construct their virtual workspace.",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AgentRole.entries) { role ->
                    RoleSelectionItem(
                        role = role,
                        isSelected = role == selectedRole,
                        onClick = { selectedRole = role }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { selectedRole?.let { onRoleSelected(it) } },
                enabled = selectedRole != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FF88),
                    disabledContainerColor = Color(0xFF333344)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "INITIALIZE WORKSPACE",
                    color = if (selectedRole != null) Color.Black else Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RoleSelectionItem(
    role: AgentRole,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) getRoleColor(role) else Color(0xFF333344)
    val bgColor = if (isSelected) getRoleColor(role).copy(alpha = 0.1f) else Color.Transparent
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(end = 16.dp)
                .background(getRoleColor(role), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = role.name.take(3),
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Text(
            text = role.name,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
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
