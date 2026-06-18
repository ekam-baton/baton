package com.ekam.baton.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekam.baton.core.data.db.entity.AgentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToTunnelSetup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val autoExtractFacts by viewModel.autoExtractFacts.collectAsState()
    val autoGenerateEpisodes by viewModel.autoGenerateEpisodes.collectAsState()
    val memoryRetentionDays by viewModel.memoryRetentionDays.collectAsState()
    val enableHapticFeedback by viewModel.enableHapticFeedback.collectAsState()
    
    val agents by viewModel.agents.collectAsState()
    val tunnelStatusMap by viewModel.tunnelStatusMap.collectAsState()

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showMemoryRetentionDialog by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showClearMemoriesDialog by remember { mutableStateOf(false) }
    var wipeConfirmationText by remember { mutableStateOf("") }

    val colors = listOf(
        0xFF9D65FF, // Purple (Default)
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
            // SECTION: Appearance
            item {
                SettingsSectionHeader("Appearance")
                ListItem(
                    headlineContent = { Text("Theme") },
                    supportingContent = { Text(themeMode.replaceFirstChar { it.uppercase() }) },
                    leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                    modifier = Modifier.clickable { showThemeDialog = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Accent color") },
                    supportingContent = {
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
                    headlineContent = { Text("Auto-extract facts") },
                    supportingContent = { Text("Automatically learn key facts from your messages") },
                    leadingContent = { Icon(Icons.Default.Memory, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = autoExtractFacts,
                            onCheckedChange = { viewModel.setAutoExtractFacts(it) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
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
                    leadingContent = { Icon(Icons.Default.Policy, contentDescription = null) },
                    modifier = Modifier.clickable { 
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ekam-ai-labs/baton"))
                        context.startActivity(i)
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

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    listOf("system", "light", "dark").forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(selected = themeMode == mode, onClick = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(mode.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

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
                TextButton(
                    onClick = {
                        if (wipeConfirmationText == "DELETE") {
                            // Implement wipe
                            showWipeDialog = false
                            wipeConfirmationText = ""
                        }
                    },
                    enabled = wipeConfirmationText == "DELETE"
                ) {
                    Text("Clear Data", color = if (wipeConfirmationText == "DELETE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showWipeDialog = false 
                    wipeConfirmationText = ""
                }) { Text("Cancel") }
            }
        )
    }

    if (showClearMemoriesDialog) {
        AlertDialog(
            onDismissRequest = { showClearMemoriesDialog = false },
            title = { Text("Clear all memories", color = MaterialTheme.colorScheme.error) },
            text = { Text("This will permanently delete all semantic, episodic, and working memories across all agents.") },
            confirmButton = {
                TextButton(onClick = { showClearMemoriesDialog = false }) {
                    Text("Clear Memories", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemoriesDialog = false }) { Text("Cancel") }
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
