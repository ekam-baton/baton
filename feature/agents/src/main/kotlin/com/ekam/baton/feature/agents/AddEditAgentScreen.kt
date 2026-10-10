package com.ekam.baton.feature.agents

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekam.baton.core.ui.components.glassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAgentScreen(
    agentId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: AgentsViewModel = koinViewModel()
) {
    // Basic States
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf("local_mcp") }
    var endpointUrl by remember { mutableStateOf("") }
    var authToken by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var securityMode by remember { mutableStateOf("standard") }

    val providers = listOf("local_mcp" to "Local MCP", "anthropic" to "Anthropic API", "gemini" to "Google Gemini", "openai" to "OpenAI")
    val securityModes = listOf("standard" to "Standard", "secured" to "E2E Encrypted", "signed" to "Cryptographically Signed", "sovereign" to "Air-Gapped Sovereign")

    Scaffold(
        containerColor = Color(0xFF0D131F), // Deep Ocean Blue
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (agentId == null) "Provision Agent" else "Edit Agent Node",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Button(
                    onClick = { /* Implement Save Logic */ onNavigateBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("INITIALIZE NODE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: Identity
            Column {
                Text("NODE IDENTITY", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.height(12.dp))
                CustomTextField(value = name, onValueChange = { name = it }, label = "Agent Designation (e.g., Synthex Lead)")
                Spacer(modifier = Modifier.height(12.dp))
                CustomTextField(value = description, onValueChange = { description = it }, label = "System Prompt / Primary Objective", singleLine = false, lines = 3)
            }

            // Section: Provider & Networking
            Column {
                Text("PROVIDER & ROUTING", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.height(12.dp))
                
                // Provider Selection
                Column(modifier = Modifier.glassCard(shape = RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text("Inference Engine", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    providers.forEach { (key, title) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (providerType == key),
                                onClick = { providerType = key },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                CustomTextField(value = endpointUrl, onValueChange = { endpointUrl = it }, label = "MCP WebSocket URL / API Endpoint")
                
                Spacer(modifier = Modifier.height(16.dp))
                // Auth Token
                OutlinedTextField(
                    value = authToken,
                    onValueChange = { authToken = it },
                    label = { Text("Cryptographic Token / API Key", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Toggle Visibility")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
            }

            // Section: Security Posture
            Column {
                Text("SECURITY POSTURE", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(modifier = Modifier.glassCard(shape = RoundedCornerShape(16.dp)).padding(16.dp)) {
                    securityModes.forEach { (key, title) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (securityMode == key),
                                onClick = { securityMode = key },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = if (securityMode == key) FontWeight.Bold else FontWeight.Normal)
                                if (key == "sovereign") {
                                    Text("Disconnects completely from cloud routing.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
            
            // Bottom padding for nav bar clearance
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    lines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        singleLine = singleLine,
        minLines = lines
    )
}
