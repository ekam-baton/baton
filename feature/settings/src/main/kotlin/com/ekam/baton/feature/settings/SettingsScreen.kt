package com.ekam.baton.feature.settings

import org.koin.compose.viewmodel.koinViewModel

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Sync
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekam.baton.core.data.model.Agent
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToTunnelSetup: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val autoExtractFacts by viewModel.autoExtractFacts.collectAsStateWithLifecycle()
    val autoGenerateEpisodes by viewModel.autoGenerateEpisodes.collectAsStateWithLifecycle()
    val memoryRetentionDays by viewModel.memoryRetentionDays.collectAsStateWithLifecycle()
    val enableHapticFeedback by viewModel.enableHapticFeedback.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val pipelineMode by viewModel.pipelineMode.collectAsStateWithLifecycle()
    val jwtSecret by viewModel.jwtSecret.collectAsStateWithLifecycle()
    
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val tunnelStatusMap by viewModel.tunnelStatusMap.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userPhone by viewModel.userPhone.collectAsStateWithLifecycle()
    
    val products by viewModel.billingManager.products.collectAsStateWithLifecycle()

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = LocalContext.current as? android.app.Activity

    var activeLegalTitle by remember { mutableStateOf<String?>(null) }
    var activeLegalContent by remember { mutableStateOf<String?>(null) }

    fun showLegal(title: String, fileName: String) {
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            activeLegalTitle = "Error"
            activeLegalContent = "Security Violation: Invalid file path."
            return
        }
        try {
            val content = context.assets.open(fileName).bufferedReader().use { it.readText() }
            activeLegalTitle = title
            activeLegalContent = content
        } catch (e: Exception) {
            activeLegalTitle = title
            activeLegalContent = "Failed to load document: ${e.localizedMessage}"
        }
    }


    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showMemoryRetentionDialog by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showClearMemoriesDialog by remember { mutableStateOf(false) }
    var showBackendUrlDialog by remember { mutableStateOf(false) }
    var backendUrlInput by remember { mutableStateOf("") }
    var showPipelineModeDialog by remember { mutableStateOf(false) }
    var showJwtSecretDialog by remember { mutableStateOf(false) }
    var jwtSecretInput by remember { mutableStateOf("") }
    var wipeConfirmationText by remember { mutableStateOf("") }
    
    // Feedback State
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }

    val colors = listOf(
        0xFFECEFF4, // Cool White (Default)
        0xFF9D65FF, // Purple
        0xFF0A84FF, // Blue
        0xFF30D158, // Green
        0xFFFF9F0A, // Orange
        0xFFFF453A, // Red
        0xFFFF375F, // Pink
        0xFFBF5AF2, // Indigo
        0xFF64D2FF  // Cyan
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // SECTION: Profile
            item {
                SettingsSectionHeader("Profile Details")
                ListItem(
                    headlineContent = { Text("Email Address") },
                    supportingContent = { Text(userEmail.ifBlank { "Not set" }) },
                    leadingContent = { Icon(Icons.Default.Email, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Phone Number") },
                    supportingContent = { Text(userPhone.ifBlank { "Not set" }) },
                    leadingContent = { Icon(Icons.Default.Phone, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // SECTION: Subscription
            item {
                SettingsSectionHeader("Subscription")
                val isPremium by viewModel.isPremiumUnlocked.collectAsStateWithLifecycle()
                val trialStart by viewModel.trialStartTime.collectAsStateWithLifecycle()
                
                if (isPremium) {
                    ListItem(
                        headlineContent = { Text("Premium Status") },
                        supportingContent = { Text("Premium Active — Unlimited Access") },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                } else {
                    val daysLeft = viewModel.getTrialDaysRemaining(trialStart)
                    ListItem(
                        headlineContent = { Text("Subscription Plan") },
                        supportingContent = { Text("Trial Active — $daysLeft days remaining") },
                        leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                        trailingContent = {
                            Button(
                                onClick = { 
                                    if (activity != null) {
                                        val product = products.firstOrNull()
                                        if (product != null) {
                                            viewModel.billingManager.launchBillingFlow(activity, product)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Upgrade", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // SECTION: Appearance
            item {
                SettingsSectionHeader("Appearance")

                ListItem(
                    headlineContent = { Text("Accent color") },
                    supportingContent = {
                        Box(modifier = Modifier.height(40.dp)) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                                items(colors) { colorLong ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorLong))
                                            .clickable { 
                                                if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.setAccentColor(colorLong) 
                                            }
                                    ) {
                                        if (accentColor == colorLong) {
                                            Icon(
                                                Icons.Default.Check, 
                                                contentDescription = "Selected", 
                                                tint = Color.White, 
                                                modifier = Modifier.align(Alignment.Center).size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Message font size") },
                    supportingContent = { Text(fontSize.replaceFirstChar { it.uppercase() }) },
                    leadingContent = { Icon(Icons.Default.FormatSize, contentDescription = null) },
                    modifier = Modifier.clickable { showFontSizeDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Haptic Feedback") },
                    supportingContent = { Text("Vibrate on interactions") },
                    leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = enableHapticFeedback,
                            onCheckedChange = { 
                                if (it) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.setEnableHapticFeedback(it) 
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // SECTION: Privacy & Security
            item {
                SettingsSectionHeader("Privacy & Security")
                ListItem(
                    headlineContent = { Text("App lock") },
                    supportingContent = { Text("Requires biometric authentication to resume app") },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { viewModel.setAppLockEnabled(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Log out") },
                    supportingContent = { Text("Require biometric login to access app") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                    modifier = Modifier.clickable {
                        if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.logout()
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Database encryption") },
                    supportingContent = { Text("Encrypted with SQLCipher") },
                    leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                    trailingContent = {
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(16.dp)) {
                            Text("Active", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Clear all data", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showWipeDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // SECTION: Network Configuration
            item {
                SettingsSectionHeader("Network Configuration")
                val allowLocalNetworkAgents by viewModel.allowLocalNetworkAgents.collectAsStateWithLifecycle()
                ListItem(
                    headlineContent = { Text("Allow local network agents") },
                    supportingContent = { Text("Enable connections to agents on your local network (e.g. 192.168.x.x, localhost). Keep disabled for security.") },
                    leadingContent = { Icon(Icons.Default.Wifi, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = allowLocalNetworkAgents,
                            onCheckedChange = { viewModel.setAllowLocalNetworkAgents(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Pipeline Connection Mode") },
                    supportingContent = { Text(if (pipelineMode == "MANAGED") "Managed (EKAM Cloud)" else "Custom Server (BYOS)") },
                    leadingContent = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    modifier = Modifier.clickable { showPipelineModeDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                
                if (pipelineMode == "BYOS") {
                    ListItem(
                        headlineContent = { Text("Backend API URL") },
                        supportingContent = { Text(backendUrl) },
                        leadingContent = { Icon(Icons.Default.Link, contentDescription = null) },
                        modifier = Modifier.clickable { 
                            backendUrlInput = backendUrl
                            showBackendUrlDialog = true 
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text("Server Secret Key") },
                        supportingContent = { Text(if (jwtSecret.isNotBlank()) "••••••••••••" else "Not set") },
                        leadingContent = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        modifier = Modifier.clickable { 
                            jwtSecretInput = jwtSecret
                            showJwtSecretDialog = true 
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // SECTION: Local Agents
            item {
                SettingsSectionHeader("Local Agents")
                ListItem(
                    headlineContent = { Text("Setup Guide") },
                    supportingContent = { Text("How to expose local models via Cloudflare") },
                    leadingContent = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    modifier = Modifier.clickable { onNavigateToTunnelSetup() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                val tunnelAgents = agents.filter {
                    val host = try { java.net.URL(it.mcpEndpointUrl).host } catch (e: Exception) { "" }
                    host.endsWith(".trycloudflare.com") || host.endsWith(".ngrok.io") || 
                    host.endsWith(".ngrok-free.app") || host.endsWith(".bore.pub") || host == "localhost"
                }
                ListItem(
                    headlineContent = { Text("Active tunnels") },
                    supportingContent = { Text("${tunnelAgents.size} local agents connected") },
                    leadingContent = { Icon(Icons.Default.Sensors, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // SECTION: Memory
            item {
                SettingsSectionHeader("Memory")
                ListItem(
                    headlineContent = { Text("Agent Memory Viewer") },
                    supportingContent = { Text("Browse episodic and semantic memories") },
                    leadingContent = { Icon(Icons.Default.Psychology, contentDescription = null) },
                    modifier = Modifier.clickable { onNavigateToMemory() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Auto-extract facts") },
                    supportingContent = { Text("Automatically learn key facts from your messages") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = autoExtractFacts,
                            onCheckedChange = { viewModel.setAutoExtractFacts(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                SettingsSectionHeader("E-Discovery & Legal")
                ListItem(
                    headlineContent = { Text("Export Cryptographic Ledger") },
                    supportingContent = { Text("Export tamper-proof audit logs (JSON)") },
                    leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingContent = {
                        OutlinedButton(
                            onClick = {
                                viewModel.exportAuditLogs(context)
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Export")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                SettingsSectionHeader("Account & Data")
                ListItem(
                    headlineContent = { Text("Auto-generate episodes") },
                    supportingContent = { Text("Periodically summarize conversation history") },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = autoGenerateEpisodes,
                            onCheckedChange = { viewModel.setAutoGenerateEpisodes(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Memory retention") },
                    supportingContent = { Text(if (memoryRetentionDays == -1) "Forever" else "$memoryRetentionDays days") },
                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                    modifier = Modifier.clickable { showMemoryRetentionDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Clear all memories", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { showClearMemoriesDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // SECTION: Help & Feedback
            item {
                SettingsSectionHeader("Help & Feedback")
                ListItem(
                    headlineContent = { Text("Contact Support") },
                    supportingContent = { Text("Report a bug or suggest a feature") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showFeedbackDialog = true 
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // SECTION: About
            item {
                SettingsSectionHeader("About")
                ListItem(
                    headlineContent = { Text("App version") },
                    supportingContent = { Text("1.0.0-alpha") }, // Would come from BuildConfig in real app
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Privacy policy") },
                    supportingContent = { Text("How we protect your local data") },
                    leadingContent = { Icon(Icons.Default.Policy, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showLegal("Privacy Policy", "privacy_policy.txt")
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Terms of Service") },
                    supportingContent = { Text("Rules for using BATON") },
                    leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showLegal("Terms of Service", "terms_of_service.txt")
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Biometric Disclosure") },
                    supportingContent = { Text("How native authentication is secured") },
                    leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showLegal("Biometric Disclosure & Consent", "biometric_disclosure.txt")
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("GitHub") },
                    supportingContent = { Text("github.com/ekam-ai-labs/baton") },
                    leadingContent = { Icon(Icons.Default.Code, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ekam-ai-labs/baton"))
                        context.startActivity(i)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    // DIALOGS



    if (showFontSizeDialog) {
        AlertDialog(
            onDismissRequest = { showFontSizeDialog = false },
            title = { Text("Message Font Size") },
            text = {
                Column {
                    listOf("small", "medium", "large").forEach { size ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.setFontSize(size)
                                    showFontSizeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = fontSize == size, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(size.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showMemoryRetentionDialog) {
        AlertDialog(
            onDismissRequest = { showMemoryRetentionDialog = false },
            title = { Text("Memory Retention") },
            text = {
                Column {
                    listOf(7, 30, 90, -1).forEach { days ->
                        val label = if (days == -1) "Forever" else "$days days"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.setMemoryRetentionDays(days)
                                    showMemoryRetentionDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = memoryRetentionDays == days, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { 
                showWipeDialog = false 
                wipeConfirmationText = ""
            },
            title = { Text("Clear all data", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text("This will permanently delete all agents, conversations, and memories. This action cannot be undone.")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = wipeConfirmationText,
                        onValueChange = { wipeConfirmationText = it },
                        label = { Text("Type DELETE to confirm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { 
                            showWipeDialog = false 
                            wipeConfirmationText = ""
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (wipeConfirmationText == "DELETE") {
                                viewModel.clearAllData()
                                showWipeDialog = false
                                wipeConfirmationText = ""
                            }
                        },
                        enabled = wipeConfirmationText == "DELETE",
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Clear Data")
                    }
                }
            },
            dismissButton = null
        )
    }

    if (showClearMemoriesDialog) {
        AlertDialog(
            onDismissRequest = { showClearMemoriesDialog = false },
            title = { Text("Clear all memories", color = MaterialTheme.colorScheme.error) },
            text = { Text("This will permanently delete all semantic, episodic, and working memories across all agents.") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showClearMemoriesDialog = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            viewModel.clearAllMemories()
                            showClearMemoriesDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Clear")
                    }
                }
            },
            dismissButton = null
        )
    }

    if (showBackendUrlDialog) {
        AlertDialog(
            onDismissRequest = { showBackendUrlDialog = false },
            title = { Text("Backend URL") },
            text = {
                Column {
                    Text(
                        "Set the API URL for Baton to connect to your backend.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = backendUrlInput,
                        onValueChange = { backendUrlInput = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note: You must restart the app after changing the backend URL for it to take effect.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setBackendUrl(backendUrlInput)
                    showBackendUrlDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackendUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showPipelineModeDialog) {
        AlertDialog(
            onDismissRequest = { showPipelineModeDialog = false },
            title = { Text("Pipeline Connection Mode") },
            text = {
                Column {
                    listOf("MANAGED" to "Managed (EKAM Cloud)", "BYOS" to "Custom Server (BYOS)").forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.setPipelineMode(mode)
                                    showPipelineModeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = pipelineMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(label)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note: You must restart the app after changing the connection mode for it to take effect.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {}
        )
    }

    if (showJwtSecretDialog) {
        AlertDialog(
            onDismissRequest = { showJwtSecretDialog = false },
            title = { Text("Server Secret Key") },
            text = {
                Column {
                    Text(
                        "Enter your server's JWT secret to authenticate. Do not share this with anyone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = jwtSecretInput,
                        onValueChange = { jwtSecretInput = it },
                        label = { Text("Secret Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note: You must restart the app after changing the secret for it to take effect.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setJwtSecret(jwtSecretInput)
                    showJwtSecretDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJwtSecretDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (activeLegalTitle != null && activeLegalContent != null) {
        AlertDialog(
            onDismissRequest = {
                activeLegalTitle = null
                activeLegalContent = null
            },
            title = {
                Text(
                    text = activeLegalTitle!!,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = activeLegalContent!!,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFCFD8DC),
                            lineHeight = 20.sp
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeLegalTitle = null
                        activeLegalContent = null
                    }
                ) {
                    Text("Close", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            containerColor = Color(0xFF0F1623), // BatonSurface
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Send Feedback") },
            text = {
                Column {
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        label = { Text("Describe the issue or suggestion") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        maxLines = 6
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { includeDiagnostics = !includeDiagnostics }
                    ) {
                        Checkbox(
                            checked = includeDiagnostics,
                            onCheckedChange = { includeDiagnostics = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Include device diagnostics", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFeedbackDialog = false
                        val diagnostics = if (includeDiagnostics) DeviceInfoHelper.getDiagnosticInfo(context) else ""
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@baton.app"))
                            putExtra(Intent.EXTRA_SUBJECT, "Baton App Feedback")
                            putExtra(Intent.EXTRA_TEXT, feedbackText + "\n\n" + diagnostics)
                        }
                        try {
                            context.startActivity(emailIntent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "No email app found", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        feedbackText = ""
                    }
                ) {
                    Text("Send via Email")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}
