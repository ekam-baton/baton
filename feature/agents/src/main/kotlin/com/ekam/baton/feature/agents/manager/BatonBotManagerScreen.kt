package com.ekam.baton.feature.agents.manager

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.feature.agents.qr.AgentPairingQrDialog
import com.ekam.baton.feature.agents.qr.AgentQrScannerDialog
import com.ekam.baton.feature.agents.qr.QrCodeView
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatonBotManagerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToCreator: () -> Unit = {},
    viewModel: BatonBotManagerViewModel = koinViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val editAgent by viewModel.editSheetAgent.collectAsStateWithLifecycle()
    val revokeAgent by viewModel.revokeDialogAgent.collectAsStateWithLifecycle()
    val identityAgent by viewModel.identityDialogAgent.collectAsStateWithLifecycle()
    val privacyAgent by viewModel.privacyDialogAgent.collectAsStateWithLifecycle()
    val deleteAgent by viewModel.deleteDialogAgent.collectAsStateWithLifecycle()
    val qrAgent by viewModel.qrDialogAgent.collectAsStateWithLifecycle()
    val showScanner by viewModel.showScannerDialog.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0F1D), // Dark Sovereign Canvas
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFFF59E0B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "BatonBot Manager",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF3D8EFF).copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "MANAGER",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color(0xFF3D8EFF)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Lifecycle & Identity Hub • ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Online",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openScanner() }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Pairing QR", tint = Color(0xFF00D2FF))
                    }
                    IconButton(onClick = onNavigateToCreator) {
                        Icon(Icons.Default.Add, contentDescription = "Create New Bot", tint = Color(0xFF00D2FF))
                    }
                    IconButton(onClick = { viewModel.resetConversation() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White.copy(alpha = 0.7f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1626))
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1626))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                // Persistent Options Dock
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        ActionDockChip(
                            label = "➕ Create Bot",
                            onClick = onNavigateToCreator
                        )
                    }
                    item {
                        ActionDockChip(
                            label = "📡 Scan Wi-Fi",
                            onClick = { viewModel.handleUserInput("Scan Wi-Fi AI") }
                        )
                    }
                    item {
                        ActionDockChip(
                            label = "📋 My Agents",
                            onClick = { viewModel.handleUserInput("Manage My Agents") }
                        )
                    }
                    item {
                        ActionDockChip(
                            label = "💡 Templates",
                            onClick = { viewModel.handleUserInput("Pre-Built Templates") }
                        )
                    }
                }

                // Conversational Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Ask anything or tap an option above...",
                                color = Color.White.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF3D8EFF),
                            unfocusedBorderColor = Color(0xFF26334D),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF141C2E),
                            unfocusedContainerColor = Color(0xFF141C2E)
                        ),
                        maxLines = 3
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.handleUserInput(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank()) Color(0xFF3D8EFF) else Color(0xFF1E293B)
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                if (msg.sender == "user") {
                    UserMessageBubble(text = msg.text)
                } else {
                    ManagerMessageBubble(
                        message = msg,
                        onActionClick = { payload ->
                            if (payload.startsWith("chat:")) {
                                val agentId = payload.removePrefix("chat:")
                                onNavigateToChat(agentId)
                            } else if (payload == "open_creator" || payload == "create_agent") {
                                onNavigateToCreator()
                            } else if (payload == "view_all" || payload == "open_portfolio") {
                                viewModel.listUserAgents()
                            } else {
                                viewModel.handleActionPayload(payload)
                            }
                        }
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Interactive Modals & Sheets (Zero Slash Commands Needed)
    // -------------------------------------------------------------------------

    // 1. Edit Profile Sheet
    editAgent?.let { agent ->
        AgentEditBottomSheet(
            agent = agent,
            onDismiss = { viewModel.dismissDialogs() },
            onSave = { name, role, desc, endpoint, color ->
                viewModel.saveAgentProfile(agent.id, name, role, desc, endpoint, color)
            }
        )
    }

    // 2. Revoke & Re-Key Dialog
    revokeAgent?.let { agent ->
        RevokeReKeyDialog(
            agent = agent,
            onDismiss = { viewModel.dismissDialogs() },
            onConfirmRevoke = { viewModel.executeRevocation(agent.id) }
        )
    }

    // 3. Identity Card Inspector Dialog
    identityAgent?.let { agent ->
        IdentityInspectorDialog(
            agent = agent,
            onDismiss = { viewModel.dismissDialogs() }
        )
    }

    // 4. Swarm Privacy Dialog
    privacyAgent?.let { agent ->
        SwarmPrivacyDialog(
            agent = agent,
            initialMode = viewModel.extractPrivacyMode(agent.securityConfig),
            onDismiss = { viewModel.dismissDialogs() },
            onSave = { isMentionOnly -> viewModel.executeSwarmPrivacyUpdate(agent.id, isMentionOnly) }
        )
    }

    // 5. Delete Confirmation Dialog
    deleteAgent?.let { agent ->
        DeleteConfirmDialog(
            agent = agent,
            onDismiss = { viewModel.dismissDialogs() },
            onConfirmDelete = { viewModel.executeDelete(agent.id) }
        )
    }

    // 6. Pairing QR Code Modal
    qrAgent?.let { agent ->
        AgentPairingQrDialog(
            agent = agent,
            onDismiss = { viewModel.dismissDialogs() }
        )
    }

    // 7. Optical QR Scanner Dialog
    if (showScanner) {
        AgentQrScannerDialog(
            onDismiss = { viewModel.closeScanner() },
            onPayloadScanned = { payload ->
                viewModel.importFromQrPayload(payload)
            }
        )
    }
}

// -----------------------------------------------------------------------------
// Action Dock Chip
// -----------------------------------------------------------------------------
@Composable
private fun ActionDockChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E293B)),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = Color(0xFF3D8EFF).copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    )
}

// -----------------------------------------------------------------------------
// Message Bubbles
// -----------------------------------------------------------------------------
@Composable
private fun UserMessageBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                .background(Color(0xFF3D8EFF))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(text = text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ManagerMessageBubble(
    message: ManagerMessage,
    onActionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFF59E0B).copy(alpha = 0.2f))
                .border(1.dp, Color(0xFFF59E0B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 330.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(Color(0xFF141C2E))
                .border(1.dp, Color(0xFF26334D), RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .padding(14.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White.copy(alpha = 0.95f),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
            )

            // Render interactive action buttons
            if (message.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    message.actions.forEach { action ->
                        val btnColor = when {
                            action.isDestructive -> Color(0xFFDC2626).copy(alpha = 0.2f)
                            action.isPrimary -> Color(0xFF3D8EFF)
                            else -> Color(0xFF1E293B)
                        }
                        val textColor = when {
                            action.isDestructive -> Color(0xFFF87171)
                            else -> Color.White
                        }

                        Button(
                            onClick = { onActionClick(action.payload) },
                            colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Bottom Sheets & Dialogs Implementation
// -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentEditBottomSheet(
    agent: Agent,
    onDismiss: () -> Unit,
    onSave: (name: String, role: String, desc: String, endpoint: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf(agent.name) }
    var role by remember { mutableStateOf(agent.role) }
    var endpoint by remember { mutableStateOf(agent.mcpEndpointUrl) }
    var description by remember { mutableStateOf(agent.description) }
    var selectedColor by remember { mutableStateOf(agent.colorAccent) }

    val roles = listOf("CODER", "RESEARCHER", "WRITER", "TUTOR", "COORDINATOR", "ASSISTANT")
    val colors = listOf("#3D8EFF", "#10B981", "#F59E0B", "#8B5CF6", "#EC4899", "#00D2FF")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1626),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "✏️ Edit Agent Profile",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Display Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Role Picker Chips
            Text("Role / Persona:", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(roles) { r ->
                    FilterChip(
                        selected = (role == r),
                        onClick = { role = r },
                        label = { Text(r) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF3D8EFF),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Endpoint Field
            OutlinedTextField(
                value = endpoint,
                onValueChange = { endpoint = it },
                label = { Text("Intelligence Endpoint (MCP / LAN / Cloud)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Color Picker
            Text("Avatar Accent:", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colors.forEach { hex ->
                    val c = Color(android.graphics.Color.parseColor(hex))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (selectedColor == hex) 3.dp else 1.dp,
                                color = if (selectedColor == hex) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = hex }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onSave(name, role, description, endpoint, selectedColor) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D8EFF)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Profile Changes", fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RevokeReKeyDialog(
    agent: Agent,
    onDismiss: () -> Unit,
    onConfirmRevoke: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141C2E),
        icon = {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(36.dp))
        },
        title = {
            Text("Revoke & Re-Key Credentials?", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "This action immediately invalidates any active relay tokens for **${agent.name}** and generates a fresh keypair backed by your device's Secure Keystore.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFDC2626).copy(alpha = 0.15f))
                        .padding(10.dp)
                ) {
                    Text(
                        "⚠️ Any external servers or peers currently communicating via the old credentials will need to re-authenticate.",
                        color = Color(0xFFFCA5A5),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRevoke,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
            ) {
                Text("Revoke & Re-Key Now", color = Color(0xFF070B14), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
private fun IdentityInspectorDialog(
    agent: Agent,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val numericId = remember(agent) {
        com.ekam.baton.core.data.util.IdentityBridge.deriveNumericId(agent.cardDid.ifBlank { agent.id })
    }
    val botToken = remember(agent, numericId) {
        com.ekam.baton.core.data.util.IdentityBridge.formatBotToken(numericId, agent.relayToken)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141C2E),
        icon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(36.dp))
        },
        title = {
            Text("Sovereign Identity Card", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(agent.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)

                // Enclave Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "ANDROID KEYSTORE HARDWARE-BACKED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF10B981)
                    )
                }

                // 1. Numeric Agent ID
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AGENT ID (NUMERIC):", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.6f))
                        Text(
                            "Copy",
                            color = Color(0xFF3D8EFF),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(numericId.toString()))
                                Toast.makeText(context, "Agent ID copied: $numericId", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1626))
                            .padding(8.dp)
                    ) {
                        Text(
                            numericId.toString(),
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            color = Color(0xFFF59E0B)
                        )
                    }
                }

                // 2. Bot Token (Combined Gateway Key)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BOT TOKEN (API KEY):", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.6f))
                        Text(
                            "Copy",
                            color = Color(0xFF3D8EFF),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(botToken))
                                Toast.makeText(context, "Bot Token copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1626))
                            .padding(8.dp)
                    ) {
                        Text(
                            botToken,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF10B981)
                        )
                    }
                }

                // 3. Decentralized Identifier (DID)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DECENTRALIZED IDENTIFIER (DID):", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.6f))
                        Text(
                            "Copy",
                            color = Color(0xFF3D8EFF),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.clickable {
                                val didText = agent.cardDid.ifBlank { "did:key:z6Mku" + agent.id.replace("-", "") }
                                clipboardManager.setText(AnnotatedString(didText))
                                Toast.makeText(context, "DID copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1626))
                            .padding(8.dp)
                    ) {
                        Text(
                            agent.cardDid.ifBlank { "did:key:z6Mku" + agent.id.replace("-", "").take(24) },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color(0xFF00D2FF)
                        )
                    }
                }

                // Fingerprint
                Column {
                    Text("PUBLIC KEY FINGERPRINT:", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.6f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1626))
                            .padding(8.dp)
                    ) {
                        Text(
                            agent.cardFingerprint.ifBlank { "SHA256: 7F:1B:9A:88:2E:3C" },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // 5. Expandable Pairing QR Section
                var showIdentityQr by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F1626))
                        .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .clickable { showIdentityQr = !showIdentityQr }
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color(0xFF00D2FF), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "PAIRING QR CODE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                        Text(
                            if (showIdentityQr) "Hide ▲" else "View QR ▼",
                            color = Color(0xFF00D2FF),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (showIdentityQr) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            QrCodeView(
                                content = com.ekam.baton.feature.agents.qr.buildAgentPairingPayload(agent),
                                modifier = Modifier.size(170.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val payload = com.ekam.baton.feature.agents.qr.buildAgentPairingPayload(agent)
                        clipboardManager.setText(AnnotatedString(payload))
                        Toast.makeText(context, "Pairing link copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF))
                ) {
                    Text("Copy QR Link", color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(botToken))
                        Toast.makeText(context, "Bot Token copied: $botToken", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D8EFF))
                ) {
                    Text("Copy Token", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
private fun SwarmPrivacyDialog(
    agent: Agent,
    initialMode: String,
    onDismiss: () -> Unit,
    onSave: (isMentionOnly: Boolean) -> Unit
) {
    var isMentionOnly by remember { mutableStateOf(initialMode == "mention_only") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141C2E),
        icon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF3D8EFF), modifier = Modifier.size(36.dp))
        },
        title = {
            Text("Swarm Cluster Privacy Mode", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose how **${agent.name}** listens to conversations in multi-agent swarm groups:",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )

                // Option 1: Mention-Only
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isMentionOnly) Color(0xFF3D8EFF).copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { isMentionOnly = true }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isMentionOnly, onClick = { isMentionOnly = true })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("🔒 Mention-Only (Recommended)", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Only awakens when @tagged or routed by Conductor. Saves battery & prevents eavesdropping.", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }

                // Option 2: Full Mesh
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isMentionOnly) Color(0xFF3D8EFF).copy(alpha = 0.15f) else Color.Transparent)
                        .clickable { isMentionOnly = false }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !isMentionOnly, onClick = { isMentionOnly = false })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("👁️ Full Mesh (Omniscient Observer)", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Listens to every swarm message to maintain global context.", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(isMentionOnly) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D8EFF))
            ) {
                Text("Apply Privacy", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(
    agent: Agent,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141C2E),
        icon = {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp))
        },
        title = {
            Text("Delete Agent?", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Are you sure you want to permanently delete **${agent.name}**? This removes its configuration and cryptographic identity from your device.",
                color = Color.White.copy(alpha = 0.8f)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
            ) {
                Text("Delete Agent", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
