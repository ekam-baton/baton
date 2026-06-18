package com.ekam.baton.feature.chat

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekam.baton.core.data.db.entity.MessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMemory: (String?) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val activeMemoryCount by viewModel.activeMemoryCount.collectAsState()
    val currentAgentId by viewModel.currentAgentId.collectAsState()
    
    // For TopAppBar info (agent name, avatar) we would ideally join tables or 
    // fetch the agent. In a real app we might pass agent details or have a ConversationWithAgent model.
    // For now we just use a generic title.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Context menu */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                activeMemoryCount = activeMemoryCount,
                onMemoryClick = { onNavigateToMemory(currentAgentId) },
                onSendMessage = { content, attachments ->
                    viewModel.sendMessage(content, attachments)
                }
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.ime.union(WindowInsets.systemBars)
    ) { innerPadding ->
        
        val listState = rememberLazyListState()
        val context = LocalContext.current
        
        // Auto-scroll to bottom when new messages arrive
        LaunchedEffect(messages.size, isStreaming) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(0) // 0 is bottom because reversed
            }
        }

        // Manage background streaming service
        LaunchedEffect(isStreaming) {
            val intent = Intent(context, Class.forName("com.ekam.baton.BatonStreamingService")).apply {
                action = if (isStreaming) "com.ekam.baton.START_STREAMING" else "com.ekam.baton.STOP_STREAMING"
            }
            try {
                if (isStreaming && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            reverseLayout = true
        ) {
            if (isStreaming) {
                item {
                    AssistantTypingIndicator()
                }
            }

            // Messages are ordered ascending in DB, so we reverse them for reverseLayout
            items(messages.reversed(), key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageEntity, modifier: Modifier = Modifier) {
    val isUser = message.role == "user"
    val isToolResult = message.role == "tool_result"

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isUser) {
            // User Bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                MarkdownRenderer(
                    text = message.content,
                    textColor = MaterialTheme.colorScheme.onTertiary
                )
            }
        } else if (isToolResult) {
            // Tool Result Bubble
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        } else {
            // Assistant Bubble
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
                    )
                    .background(Color.Transparent, RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                MarkdownRenderer(
                    text = message.content,
                    textColor = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
fun AssistantTypingIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
                )
                .background(Color.Transparent, RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition()
                for (i in 0 until 3) {
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 600, delayMillis = i * 200, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    activeMemoryCount: Int,
    onMemoryClick: () -> Unit,
    onSendMessage: (String, List<Uri>) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var attachments by remember { mutableStateOf(emptyList<Uri>()) }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            attachments = attachments + uri
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        // Attachments preview
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEach { uri ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("File attached", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        
        // Context Indicator Chip
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            onClick = onMemoryClick
        ) {
            Text(
                text = "\uD83E\uDDE0 $activeMemoryCount memories injected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = { launcher.launch("*/*") },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Message...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            }

            IconButton(
                onClick = {
                    if (text.isNotBlank() || attachments.isNotEmpty()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSendMessage(text, attachments)
                        text = ""
                        attachments = emptyList()
                    }
                },
                enabled = text.isNotBlank() || attachments.isNotEmpty(),
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
