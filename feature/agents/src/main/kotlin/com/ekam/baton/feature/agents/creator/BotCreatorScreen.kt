package com.ekam.baton.feature.agents.creator

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.network.mcp.McpTool
import com.ekam.baton.core.network.mdns.DiscoveredAgent
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel
import java.util.concurrent.Executors

private val PALETTE_COLORS = listOf(
    "#00D2FF", // Cyber Cyan
    "#3D8EFF", // Sovereign Blue
    "#10B981", // Emerald Green
    "#F59E0B", // Amber Gold
    "#8B5CF6", // Cyber Purple
    "#EC4899"  // Neon Pink
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotCreatorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToManager: () -> Unit = {},
    viewModel: BotCreatorViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(viewModel.uiEvents) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is BotCreatorUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is BotCreatorUiEvent.AgentCreated -> {
                    Toast.makeText(context, "Bot ${event.agent.name} created successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0A0F1D), // Dark Sovereign Canvas
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Bot Creator",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF00D2FF).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "WIZARD",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFF00D2FF)
                                )
                            }
                        }
                        Text(
                            when (state.currentStep) {
                                BotCreatorStep.PROFILE -> "Step 1 of 3: Identity & Name"
                                BotCreatorStep.ROLE_PRESET -> "Step 2 of 3: Role & Capabilities"
                                BotCreatorStep.BRAIN_CONNECTIVITY -> "Step 3 of 3: Model & Connection"
                                BotCreatorStep.SUCCESS -> "Creation Complete • Bot Ready"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.currentStep == BotCreatorStep.PROFILE || state.currentStep == BotCreatorStep.SUCCESS) {
                            onNavigateBack()
                        } else {
                            viewModel.previousStep()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F1626))
            )
        },
        bottomBar = {
            if (state.currentStep != BotCreatorStep.SUCCESS) {
                Surface(
                    color = Color(0xFF0F1626),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.currentStep != BotCreatorStep.PROFILE) {
                            OutlinedButton(
                                onClick = { viewModel.previousStep() },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Back", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Button(
                            onClick = { viewModel.nextStep() },
                            enabled = !state.isMinting,
                            modifier = Modifier
                                .weight(if (state.currentStep == BotCreatorStep.PROFILE) 2f else 1.5f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.currentStep == BotCreatorStep.BRAIN_CONNECTIVITY) Color(0xFF10B981) else Color(0xFF3D8EFF)
                            )
                        ) {
                            if (state.isMinting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Creating Bot...")
                            } else {
                                val buttonLabel = when (state.currentStep) {
                                    BotCreatorStep.PROFILE -> "Next: Role Preset"
                                    BotCreatorStep.ROLE_PRESET -> "Next: Connect Brain"
                                    BotCreatorStep.BRAIN_CONNECTIVITY -> "Launch Bot"
                                    BotCreatorStep.SUCCESS -> "Done"
                                }
                                Text(buttonLabel, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (state.currentStep == BotCreatorStep.BRAIN_CONNECTIVITY) Icons.Default.RocketLaunch else Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Step Progress Indicator
            StepProgressHeader(currentStep = state.currentStep)

            // Step Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (state.currentStep) {
                    BotCreatorStep.PROFILE -> {
                        ProfileStepContent(
                            state = state,
                            onNameChange = viewModel::setAgentName,
                            onDescChange = viewModel::setDescription,
                            onColorSelect = viewModel::setColorAccent,
                            onSuggestRandom = viewModel::suggestRandomProfile
                        )
                    }
                    BotCreatorStep.ROLE_PRESET -> {
                        RolePresetStepContent(
                            state = state,
                            presets = viewModel.rolePresets,
                            onSelectPreset = viewModel::selectRolePreset,
                            onToggleCapability = viewModel::toggleCapability
                        )
                    }
                    BotCreatorStep.BRAIN_CONNECTIVITY -> {
                        BrainConnectivityStepContent(
                            state = state,
                            onSourceChange = viewModel::setBrainSource,
                            onUrlChange = viewModel::setEndpointUrl,
                            onModelChange = viewModel::setModelIdentifier,
                            onToggleWifi = viewModel::toggleWifiDiscovery,
                            onSelectNode = viewModel::selectDiscoveredNode,
                            onTunnelUrlChange = viewModel::setTunnelUrl,
                            onTunnelTokenChange = viewModel::setTunnelAuthToken,
                            onTestTunnelConnection = viewModel::testTunnelConnection,
                            onApplyTunnelPreset = viewModel::applyTunnelPreset,
                            onQrPayloadScanned = viewModel::parseAndApplyQrPayload
                        )
                    }
                    BotCreatorStep.SUCCESS -> {
                        SuccessStepContent(
                            state = state,
                            onCopyNumericId = { id ->
                                clipboardManager.setText(AnnotatedString(id.toString()))
                                Toast.makeText(context, "Copied Numeric Agent ID: $id", Toast.LENGTH_SHORT).show()
                            },
                            onCopyToken = { token ->
                                clipboardManager.setText(AnnotatedString(token))
                                Toast.makeText(context, "Copied Bot Token (API Key)", Toast.LENGTH_SHORT).show()
                            },
                            onCopyDid = { did ->
                                clipboardManager.setText(AnnotatedString(did))
                                Toast.makeText(context, "Copied Sovereign DID", Toast.LENGTH_SHORT).show()
                            },
                            onStartChat = { agentId ->
                                onNavigateToChat(agentId)
                            },
                            onOpenManager = {
                                onNavigateToManager()
                            },
                            onCreateAnother = {
                                viewModel.resetWizard()
                            }
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Step 1: Profile & Identity
// -----------------------------------------------------------------------------
@Composable
private fun ProfileStepContent(
    state: BotCreatorUiState,
    onNameChange: (String) -> Unit,
    onDescChange: (String) -> Unit,
    onColorSelect: (String) -> Unit,
    onSuggestRandom: () -> Unit = {}
) {
    val suggestedNames = listOf("Jarvis", "Athena", "DevCopilot", "Sentinel", "Scribe", "Nexus")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Live Preview Avatar Card
        val accentColor = try {
            Color(android.graphics.Color.parseColor(state.colorAccent))
        } catch (e: Exception) {
            Color(0xFF3D8EFF)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF131D31))
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.agentName.trim().firstOrNull()?.uppercase() ?: "🤖",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.agentName.ifBlank { "Bot Name" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = state.description.ifBlank { "Bot description and mission" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "🔒 On-Device Keystore • Ed25519",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        color = Color(0xFF10B981)
                    )
                }
            }
        }

        // Codename input with Quick Suggest button
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "BOT NAME",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = Color(0xFF3D8EFF)
                )
                TextButton(
                    onClick = onSuggestRandom,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF00D2FF))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Suggest Identity", fontSize = 11.sp, color = Color(0xFF00D2FF), fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedTextField(
                value = state.agentName,
                onValueChange = onNameChange,
                placeholder = { Text("e.g. Jarvis, Dev Copilot", color = Color.White.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3D8EFF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color(0xFF131D31),
                    unfocusedContainerColor = Color(0xFF131D31)
                )
            )

            // Name suggestions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestedNames.take(4).forEach { suggestion ->
                    SuggestionChip(
                        onClick = { onNameChange(suggestion) },
                        label = { Text(suggestion, fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color(0xFF1B273E),
                            labelColor = Color(0xFF00D2FF)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00D2FF).copy(alpha = 0.3f))
                    )
                }
            }
        }

        // Persona Description input
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "BOT DESCRIPTION",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = Color(0xFF3D8EFF)
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescChange,
                placeholder = { Text("Describe what this agent excels at...", color = Color.White.copy(alpha = 0.4f)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF3D8EFF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color(0xFF131D31),
                    unfocusedContainerColor = Color(0xFF131D31)
                )
            )
        }

        // Accent Color Picker
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "COLOR ACCENT",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = Color(0xFF3D8EFF)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PALETTE_COLORS.forEach { hex ->
                    val color = Color(android.graphics.Color.parseColor(hex))
                    val isSelected = state.colorAccent.equals(hex, ignoreCase = true)

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onColorSelect(hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Step 2: Role & Capabilities Preset
// -----------------------------------------------------------------------------
@Composable
private fun RolePresetStepContent(
    state: BotCreatorUiState,
    presets: List<RolePreset>,
    onSelectPreset: (String) -> Unit,
    onToggleCapability: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "SELECT ROLE PRESET",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = Color(0xFF3D8EFF)
        )

        presets.forEach { preset ->
            val isSelected = state.selectedRoleId == preset.id

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Color(0xFF1A2744) else Color(0xFF131D31))
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectPreset(preset.id) }
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.iconEmoji, fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                preset.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "✓ SELECTED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = Color(0xFF00D2FF)
                                )
                            }
                        }
                        Text(
                            preset.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }

        // Capabilities Checklist
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "ATTACHED CAPABILITIES",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            color = Color(0xFF3D8EFF)
        )

        val allCapabilities = listOf(
            "Web Search", "Code Generation", "File Analysis", "Terminal Runner",
            "Contextual Memory", "Key Attestation", "Air-Gap Guard", "Swarm Coordinator"
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            allCapabilities.chunked(2).forEach { rowCaps ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCaps.forEach { cap ->
                        val isChecked = state.selectedCapabilities.contains(cap)
                        FilterChip(
                            selected = isChecked,
                            onClick = { onToggleCapability(cap) },
                            label = { Text(cap, fontSize = 12.sp) },
                            leadingIcon = {
                                if (isChecked) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00D2FF).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF00D2FF),
                                containerColor = Color(0xFF131D31),
                                labelColor = Color.White.copy(alpha = 0.8f)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isChecked,
                                borderColor = Color.White.copy(alpha = 0.15f),
                                selectedBorderColor = Color(0xFF00D2FF)
                            )
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Step 3: Brain & Connectivity
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// Step 3: Brain & Connectivity (3 Mastered Methods Only)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainConnectivityStepContent(
    state: BotCreatorUiState,
    onSourceChange: (BrainSourceType) -> Unit,
    onUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onToggleWifi: (Boolean) -> Unit,
    onSelectNode: (DiscoveredAgent) -> Unit,
    onTunnelUrlChange: (String) -> Unit,
    onTunnelTokenChange: (String) -> Unit,
    onTestTunnelConnection: () -> Unit,
    onApplyTunnelPreset: (String, String) -> Unit,
    onQrPayloadScanned: (String) -> Unit
) {
    var showQrDialog by remember { mutableStateOf(false) }
    var isTokenVisible by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    if (showQrDialog) {
        EnterpriseQrScannerDialog(
            onDismiss = { showQrDialog = false },
            onPayloadScanned = { payload ->
                showQrDialog = false
                onQrPayloadScanned(payload)
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Section Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "SELECT CONNECTION METHOD",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = Color(0xFF3D8EFF)
            )
            Text(
                "Choose how your bot connects. Mastered for Local Wi-Fi, Cloudflare / Ngrok Tunnels, or Sovereign On-Device.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }

        // 3-way Source Segmented Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF131D31))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            // 1. Local Wi-Fi
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (state.brainSource == BrainSourceType.LOCAL_WIFI) Color(0xFF3D8EFF) else Color.Transparent)
                    .clickable { onSourceChange(BrainSourceType.LOCAL_WIFI) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Local Wi-Fi", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                }
            }

            // 2. Cloudflare / Ngrok Tunnel
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (state.brainSource == BrainSourceType.REMOTE_TUNNEL) Color(0xFF3D8EFF) else Color.Transparent)
                    .clickable { onSourceChange(BrainSourceType.REMOTE_TUNNEL) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cloudflare / Ngrok", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                }
            }

            // 3. Baton Sovereign Bot
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (state.brainSource == BrainSourceType.BATON_BOT) Color(0xFF10B981) else Color.Transparent)
                    .clickable { onSourceChange(BrainSourceType.BATON_BOT) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Baton Bot", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                }
            }
        }

        when (state.brainSource) {
            BrainSourceType.LOCAL_WIFI -> {
                // Info Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF131D31))
                        .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF00D2FF), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "LOCAL WI-FI MESH / LAN",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = Color(0xFF00D2FF)
                            )
                        }
                        Text(
                            "Auto-detects Antigravity, Ollama, LM Studio, or Baton nodes on your local network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Wi-Fi Discovery Toggle Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF131D31))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wi-Fi AI Discovery", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "Scan Wi-Fi for AI nodes (stops after 45s to save battery).",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = state.isWifiDiscovering,
                            onCheckedChange = onToggleWifi,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            )
                        )
                    }
                }

                // Discovered Nodes
                if (state.discoveredNodes.isNotEmpty()) {
                    Text(
                        "FOUND ON NETWORK (${state.discoveredNodes.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color(0xFF00D2FF)
                    )

                    state.discoveredNodes.forEach { node ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A2744))
                                .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF00D2FF), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(node.name, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("${node.ipAddress}:${node.port}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
                                }
                                Button(
                                    onClick = { onSelectNode(node) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Connect", color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else if (state.isWifiDiscovering) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF00D2FF), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Listening for AI beacons on local Wi-Fi...", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                }

                // Manual LAN Endpoint Address
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "LAN ENDPOINT URL",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color(0xFF3D8EFF)
                    )
                    OutlinedTextField(
                        value = state.endpointUrl,
                        onValueChange = onUrlChange,
                        placeholder = { Text("http://192.168.1.50:11434/v1 or localhost", color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3D8EFF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF131D31),
                            unfocusedContainerColor = Color(0xFF131D31)
                        )
                    )
                }
            }

            BrainSourceType.REMOTE_TUNNEL -> {
                // Info Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF131D31))
                        .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = Color(0xFF00D2FF), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "REMOTE TUNNEL (CLOUDFLARE / NGROK)",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                color = Color(0xFF00D2FF)
                            )
                        }
                        Text(
                            "Connects to Antigravity on your PC from anywhere over cellular data without port forwarding.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Optical QR & Clipboard Quick Pair Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showQrDialog = true },
                        modifier = Modifier.weight(1.3f).height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D2FF))
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF0A0F1D), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Tunnel QR", color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                onQrPayloadScanned(clip)
                                Toast.makeText(context, "Imported tunnel configuration", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste URL", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Tunnel Presets (Cloudflare / Ngrok)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onApplyTunnelPreset("Cloudflare Tunnel", "https://antigravity.trycloudflare.com/sse") },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1A2744),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Cloudflare Tunnel", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF00D2FF))
                            Text("*.trycloudflare.com", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onApplyTunnelPreset("Ngrok Tunnel", "https://antigravity.ngrok-free.app/sse") },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1A2744),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Ngrok Tunnel", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF00D2FF))
                            Text("*.ngrok-free.app", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Tunnel URL Input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "TUNNEL URL",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color(0xFF3D8EFF)
                    )
                    OutlinedTextField(
                        value = state.tunnelUrl,
                        onValueChange = onTunnelUrlChange,
                        placeholder = { Text("https://xyz.trycloudflare.com/sse", color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3D8EFF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF131D31),
                            unfocusedContainerColor = Color(0xFF131D31)
                        )
                    )
                }

                // Tunnel Secret / Token Input (Optional)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "TUNNEL ACCESS TOKEN (OPTIONAL)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            color = Color(0xFF3D8EFF)
                        )
                        Text("Hardware Encrypted", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color(0xFF10B981))
                    }
                    OutlinedTextField(
                        value = state.tunnelAuthToken,
                        onValueChange = onTunnelTokenChange,
                        placeholder = { Text("Leave blank if no password set", color = Color.White.copy(alpha = 0.4f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isTokenVisible = !isTokenVisible }) {
                                Icon(
                                    imageVector = if (isTokenVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isTokenVisible) "Hide token" else "Show token",
                                    tint = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3D8EFF),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color(0xFF131D31),
                            unfocusedContainerColor = Color(0xFF131D31)
                        )
                    )
                }

                // Test Tunnel Handshake Button
                Button(
                    onClick = onTestTunnelConnection,
                    enabled = !state.isTestingTunnelConnection && state.tunnelUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.tunnelConnectionSuccess == true) Color(0xFF10B981) else Color(0xFF3D8EFF)
                    )
                ) {
                    if (state.isTestingTunnelConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Tunnel Handshake...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = if (state.tunnelConnectionSuccess == true) Icons.Default.Check else Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (state.tunnelConnectionSuccess == true) "Re-test Tunnel Handshake" else "Test Tunnel Handshake & Discover Tools",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Tunnel Connection Test Feedback Banner
                if (state.tunnelConnectionSuccess == true) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Tunnel Active (${state.discoveredTunnelTools.size} Remote Tools Ready)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }

                            if (state.discoveredTunnelTools.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    state.discoveredTunnelTools.take(6).forEach { tool ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF131D31))
                                                .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "✓ ${tool.name}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                ),
                                                color = Color(0xFF00D2FF)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (state.tunnelConnectionSuccess == false) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Tunnel Handshake Failed", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Text(
                                    state.tunnelConnectionError ?: "Unable to connect to tunnel. Check if cloudflared or ngrok is running on PC.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            BrainSourceType.BATON_BOT -> {
                // On-Device Sovereign Bot Hero Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF131D31))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "100% ON-DEVICE SOVEREIGN BOT",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    "Zero cloud or desktop dependency",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }

                        Text(
                            "Operates autonomously right on your smartphone. All cryptographic keys are minted and sealed inside Android Hardware Keystore (StrongBox).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f)
                        )

                        // Feature badges
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "🔒 Ed25519 Hardware Keystore Attestation",
                                "🛡️ Native Rust Policy Sandbox (policy.rs)",
                                "🌐 Air-Gapped Local Privacy Mode",
                                "⚡ Baton Peer-to-Peer Relay Mesh Ready"
                            ).forEach { feature ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(feature, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Model Identifier Input (Applicable to Wi-Fi and Tunnel)
        if (state.brainSource != BrainSourceType.BATON_BOT) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "MODEL IDENTIFIER",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = Color(0xFF3D8EFF)
                )
                OutlinedTextField(
                    value = state.modelIdentifier,
                    onValueChange = onModelChange,
                    placeholder = { Text("e.g. llama3.2:latest, qwen2.5-coder", color = Color.White.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3D8EFF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color(0xFF131D31),
                        unfocusedContainerColor = Color(0xFF131D31)
                    )
                )

                // Quick Model Suggestion Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("llama3.2:latest", "qwen2.5-coder", "mistral").forEach { model ->
                        SuggestionChip(
                            onClick = { onModelChange(model) },
                            label = { Text(model, fontSize = 10.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFF131D31),
                                labelColor = if (state.modelIdentifier == model) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.7f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (state.modelIdentifier == model) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// QR Scanner & Config Dialog for Enterprise Remote MCP Hub
// -----------------------------------------------------------------------------
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
private fun EnterpriseQrScannerDialog(
    onDismiss: () -> Unit,
    onPayloadScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var isTorchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (status == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1626),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Scan Tunnel QR Code", color = Color.White, fontWeight = FontWeight.Bold)
                if (hasCameraPermission && camera != null) {
                    IconButton(onClick = {
                        val next = !isTorchOn
                        camera?.cameraControl?.enableTorch(next)
                        isTorchOn = next
                    }) {
                        Icon(
                            if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Torch",
                            tint = if (isTorchOn) Color(0xFF00D2FF) else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Point your camera at the Cloudflare / Ngrok QR code generated on your PC, or paste below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF00D2FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val previewView = PreviewView(ctx)
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }

                                    val options = BarcodeScannerOptions.Builder()
                                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                        .build()
                                    val scanner = BarcodeScanning.getClient(options)
                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()

                                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                            scanner.process(image)
                                                .addOnSuccessListener { barcodes ->
                                                    for (barcode in barcodes) {
                                                        barcode.rawValue?.let { raw ->
                                                            if (raw.isNotBlank()) {
                                                                onPayloadScanned(raw)
                                                            }
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener { imageProxy.close() }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }

                                    try {
                                        cameraProvider.unbindAll()
                                        val cam = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                        camera = cam
                                    } catch (e: Exception) {
                                        // Ignore camera bind errors
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            }
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Camera permission required to scan optical QR code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Manual input fallback
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    placeholder = { Text("Paste baton://connect, https://... or JSON", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF3D8EFF),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color(0xFF131D31),
                        unfocusedContainerColor = Color(0xFF131D31)
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (manualText.isNotBlank()) {
                        onPayloadScanned(manualText.trim())
                    }
                },
                enabled = manualText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D8EFF))
            ) {
                Text("Apply Payload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}

// -----------------------------------------------------------------------------
// Step 4: Minting Success & Identity Credentials
// -----------------------------------------------------------------------------
@Composable
private fun SuccessStepContent(
    state: BotCreatorUiState,
    onCopyNumericId: (Long) -> Unit,
    onCopyToken: (String) -> Unit,
    onCopyDid: (String) -> Unit,
    onStartChat: (String) -> Unit,
    onOpenManager: () -> Unit,
    onCreateAnother: () -> Unit
) {
    val agent = state.mintedAgent ?: return
    val numericId = state.numericAgentId ?: 100000001L
    val botToken = state.botToken ?: ""

    val accentColor = try {
        Color(android.graphics.Color.parseColor(agent.colorAccent))
    } catch (e: Exception) {
        Color(0xFF3D8EFF)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Celebration Badge
        Surface(
            shape = CircleShape,
            color = Color(0xFF10B981).copy(alpha = 0.2f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(36.dp))
            }
        }

        Text(
            "Bot Created Successfully!",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Text(
            "Your bot is registered on this device and ready to connect.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Identity Credentials Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = agent.name.firstOrNull()?.uppercase() ?: "🤖",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(agent.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        Text(agent.role, style = MaterialTheme.typography.labelSmall, color = Color(0xFF00D2FF))
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // 1. Numeric Agent ID (with 1-tap copy)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "AGENT ID (NUMERIC)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color(0xFF00D2FF)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0A0F1D))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            numericId.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        IconButton(
                            onClick = { onCopyNumericId(numericId) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF00D2FF), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // 2. Bot Token / API Key (with 1-tap copy)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "BOT TOKEN (API KEY)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color(0xFFF59E0B)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0A0F1D))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            botToken,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = Color(0xFFF59E0B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onCopyToken(botToken) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // 3. Sovereign DID
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "SOVEREIGN DID",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0A0F1D))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            agent.cardDid.ifBlank { "did:key:z6M..." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onCopyDid(agent.cardDid) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onStartChat(agent.id) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D8EFF))
            ) {
                Icon(Icons.Default.ChatBubble, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Chatting", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onOpenManager,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B))
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage in BatonBot Manager", fontWeight = FontWeight.Bold)
            }

            TextButton(
                onClick = onCreateAnother,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Another Bot", color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Progress Indicator Header
// -----------------------------------------------------------------------------
@Composable
private fun StepProgressHeader(currentStep: BotCreatorStep) {
    val steps = listOf(
        "Profile" to BotCreatorStep.PROFILE,
        "Role" to BotCreatorStep.ROLE_PRESET,
        "Brain" to BotCreatorStep.BRAIN_CONNECTIVITY
    )

    if (currentStep == BotCreatorStep.SUCCESS) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1626))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (label, step) ->
            val isCurrent = currentStep == step
            val isPast = currentStep.ordinal > step.ordinal

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isPast -> Color(0xFF10B981)
                                isCurrent -> Color(0xFF3D8EFF)
                                else -> Color(0xFF1E293B)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPast) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Text(
                            "${index + 1}",
                            color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal),
                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.5f)
                )
            }

            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )
            }
        }
    }
}
