package com.ekam.baton.feature.chat

import org.koin.compose.viewmodel.koinViewModel

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekam.baton.core.data.model.Conversation
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.ui.components.glassCard

import androidx.paging.compose.collectAsLazyPagingItems
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToCall: (String) -> Unit,
    viewModel: ChatViewModel = koinViewModel()
) {
    val conversations = viewModel.conversations.collectAsLazyPagingItems()
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    var showAgentPicker by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "BATON",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    ) 
                },
                actions = {
                    IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAgentPicker = true },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ) {
                Icon(Icons.Default.Create, contentDescription = "New Chat")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = isSearchExpanded) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search conversations...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = MaterialTheme.shapes.large
                )
            }

            if (conversations.itemCount == 0) {
                EmptyChatsState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = conversations.itemCount,
                        key = { index -> conversations[index]?.id ?: index }
                    ) { index ->
                        val conversation = conversations[index]
                        if (conversation != null) {
                            SwipeToDeleteConversationItem(
                                conversation = conversation,
                                agentColorHex = agents.find { it.id == conversation.agentId }?.colorAccent,
                                onClick = { onNavigateToChat(conversation.id) },
                                onCallClick = { onNavigateToCall(conversation.title) },
                                onDelete = { viewModel.deleteConversation(conversation.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAgentPicker) {
        AgentPickerBottomSheet(
            agents = agents,
            onAgentSelected = { agent ->
                showAgentPicker = false
                viewModel.createConversation(agent.id) { newConvId ->
                    onNavigateToChat(newConvId)
                }
            },
            onDismissRequest = { showAgentPicker = false }
        )
    }
}

@Composable
fun EmptyChatsState(modifier: Modifier = Modifier, onInitializeClick: () -> Unit = {}) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .glassCard(shape = MaterialTheme.shapes.large)
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Active Agents",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The telemetry dashboard is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onInitializeClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("[Initialize First Agent]")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteConversationItem(
    conversation: Conversation,
    agentColorHex: String?,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.medium)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    ) {
        ConversationItem(
            conversation = conversation,
            agentColorHex = agentColorHex,
            onClick = onClick,
            onCallClick = onCallClick
        )
    }
}

@Composable
fun ConversationItem(
    conversation: Conversation,
    agentColorHex: String?,
    onClick: () -> Unit,
    onCallClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        val fallbackColor = MaterialTheme.colorScheme.primary
        val avatarColor = try {
            if (agentColorHex != null) Color(android.graphics.Color.parseColor(agentColorHex)) 
            else fallbackColor
        } catch (e: Exception) {
            fallbackColor
        }
        val contentColor = run {
            val luminance = 0.2126f * avatarColor.red + 0.7152f * avatarColor.green + 0.0722f * avatarColor.blue
            if (luminance > 0.5f) Color(0xFF0A0E1A) else Color.White
        }
        
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = conversation.title.firstOrNull()?.uppercase() ?: "?",
                color = contentColor,
                style = MaterialTheme.typography.titleLarge
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.isPinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(16.dp).padding(start = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val timeString = DateUtils.getRelativeTimeSpanString(
                    conversation.updatedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            val (telemetryText, telemetryColor) = when (conversation.id.lastOrNull()) {
                '1', '2' -> "⚠️ Agent loop detected - Halting" to MaterialTheme.colorScheme.error
                '3', '4' -> "✋ Blocked: Awaiting Human Approval" to Color(0xFFFFB300)
                '5', '6' -> "🛑 Token limit exceeded" to MaterialTheme.colorScheme.error
                else -> "⚡ Executing [Web Search]..." to MaterialTheme.colorScheme.primary
            }
            Text(
                text = telemetryText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                color = telemetryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        IconButton(onClick = onCallClick) {
            Icon(Icons.Default.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
