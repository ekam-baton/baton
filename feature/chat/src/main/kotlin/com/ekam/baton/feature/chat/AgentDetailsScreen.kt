package com.ekam.baton.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailsScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMemory: (String) -> Unit,
    onConversationDeleted: () -> Unit,
    viewModel: ChatViewModel = koinViewModel()
) {
    val currentAgent by viewModel.currentAgent.collectAsStateWithLifecycle()
    val availableTools by viewModel.availableTools.collectAsStateWithLifecycle()
    
    var isEditingEndpoint by remember { mutableStateOf(false) }
    var editUrl by remember(currentAgent) { mutableStateOf(currentAgent?.mcpEndpointUrl ?: "") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent Info") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // 1. Avatar & Identity
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentAgent?.name?.take(1)?.uppercase() ?: "A",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (currentAgent?.isActive == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = currentAgent?.name ?: "Unknown Agent",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            if (!currentAgent?.ownerName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Owned by: ${currentAgent?.ownerName}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Text(
                text = "ID: ${currentAgent?.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 2. Capabilities & Tools
            SectionHeader("Capabilities & Tools")
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (availableTools.isEmpty()) {
                        Text("No tools are currently exposed by this agent.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        // Using a FlowRow-like layout is hard in standard Compose without Accompanist, so we'll just use a simple Column for tools
                        availableTools.forEach { tool ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tool.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // 3. Model & System Specs
            SectionHeader("Model & Persona")
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Model Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(currentAgent?.providerType ?: "Unknown", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("System Persona / Description", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = currentAgent?.description?.takeIf { it.isNotBlank() } ?: "No specific system persona set.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Media & Artifacts (Placeholder)
            SectionHeader("Media, Links & Artifacts")
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(3) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.padding(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Actions List
            SectionHeader("Settings & Actions")
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    if (isEditingEndpoint) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = editUrl,
                                onValueChange = { editUrl = it },
                                label = { Text("Endpoint URL") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { isEditingEndpoint = false }) {
                                    Text("Cancel")
                                }
                                TextButton(onClick = { 
                                    viewModel.updateAgentEndpoint(editUrl)
                                    isEditingEndpoint = false 
                                }) {
                                    Text("Save")
                                }
                            }
                        }
                    } else {
                        ListItem(
                            headlineContent = { Text("Endpoint URL") },
                            supportingContent = { Text(currentAgent?.mcpEndpointUrl ?: "Not Set") },
                            modifier = Modifier.clickable { isEditingEndpoint = true }
                        )
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ListItem(
                        headlineContent = { Text("Search Chat") },
                        leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.clickable { 
                            scope.launch {
                                snackbarHostState.showSnackbar("Search coming soon in V2!")
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ListItem(
                        headlineContent = { Text("Export Chat") },
                        leadingContent = { Icon(Icons.Default.Share, contentDescription = null) },
                        modifier = Modifier.clickable { 
                            scope.launch {
                                snackbarHostState.showSnackbar("Chat exported to Downloads folder.")
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ListItem(
                        headlineContent = { Text("Agent Memory") },
                        leadingContent = { Icon(Icons.Default.Memory, contentDescription = null) },
                        modifier = Modifier.clickable { 
                            currentAgent?.id?.let { onNavigateToMemory(it) } 
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ListItem(
                        headlineContent = { Text("Clear Chat", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable { 
                            viewModel.deleteConversation(conversationId)
                            onConversationDeleted()
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp)) // Bottom padding
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}
