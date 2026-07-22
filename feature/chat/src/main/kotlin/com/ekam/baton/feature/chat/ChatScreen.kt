package com.ekam.baton.feature.chat

import org.koin.compose.viewmodel.koinViewModel

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Clear
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.ekam.baton.core.data.preferences.KeyboardShortcut

import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekam.baton.core.data.model.Message
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMemory: (String?) -> Unit,
    onNavigateToCall: (String) -> Unit,
    viewModel: ChatViewModel = koinViewModel()
) {
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val activeMemoryCount by viewModel.activeMemoryCount.collectAsStateWithLifecycle()
    val currentAgentId by viewModel.currentAgentId.collectAsStateWithLifecycle()
    val currentAgent by viewModel.currentAgent.collectAsStateWithLifecycle()
    val agentActivityStatus by viewModel.agentActivityStatus.collectAsStateWithLifecycle()
    
    val availableTools by viewModel.availableTools.collectAsStateWithLifecycle()
    val toolAuthRequest by viewModel.toolAuthRequests.collectAsStateWithLifecycle(initialValue = null)
    val fontSizePref by viewModel.fontSize.collectAsStateWithLifecycle()
    
    var showToolsSheet by remember { mutableStateOf(false) }
    var showAgentDetails by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    
    var contextMenuMessage by remember { mutableStateOf<Message?>(null) }
    var showForwardPicker by remember { mutableStateOf(false) }
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }
    val uiError by viewModel.uiError.collectAsStateWithLifecycle()

    // FIX: Show errors in a Snackbar and clear them after display
    LaunchedEffect(uiError) {
        uiError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    toolAuthRequest?.let { request ->
        JITAuthorizationDialog(
            request = request,
            onResult = { isApproved -> viewModel.resolveToolAuth(request, isApproved) }
        )
    }

    if (showToolsSheet) {
        ToolExecutionBottomSheet(
            tools = availableTools,
            onExecuteTool = { toolName, args -> viewModel.executeTool(toolName, args) },
            onDismiss = { showToolsSheet = false }
        )
    }

    if (showAgentDetails && currentAgent != null) {
        var editUrl by remember(currentAgent) { mutableStateOf(currentAgent?.mcpEndpointUrl ?: "") }
        
        AlertDialog(
            onDismissRequest = { showAgentDetails = false },
            title = { Text(currentAgent?.name ?: "Agent Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!currentAgent?.ownerName.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "👤 Owned by: ${currentAgent?.ownerName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Text("Agent ID: ${currentAgent?.id}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("Endpoint URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.updateAgentEndpoint(editUrl)
                    showAgentDetails = false 
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAgentDetails = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showForwardPicker && contextMenuMessage != null) {
        AgentPickerBottomSheet(
            agents = agents,
            onAgentSelected = { targetAgent ->
                viewModel.forwardMessage(contextMenuMessage!!, targetAgent.id) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Forwarded to ${targetAgent.name}")
                    }
                }
                showForwardPicker = false
                contextMenuMessage = null
            },
            onDismissRequest = { 
                showForwardPicker = false 
                contextMenuMessage = null
            }
        )
    }

    if (contextMenuMessage != null && !showForwardPicker) {
        ModalBottomSheet(
            onDismissRequest = { contextMenuMessage = null },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    text = "Message Options",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                ListItem(
                    headlineContent = { Text("Copy Text") },
                    leadingContent = { Icon(Icons.Default.Keyboard, contentDescription = null) },
                    modifier = Modifier.clickable {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(contextMenuMessage!!.content))
                        contextMenuMessage = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Share Text") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, contextMenuMessage!!.content)
                        }
                        androidContext.startActivity(Intent.createChooser(intent, "Share via"))
                        contextMenuMessage = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Forward") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(24.dp)) }, 
                    modifier = Modifier.clickable {
                        showForwardPicker = true
                    }
                )
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, tint = MaterialTheme.colorScheme.error, contentDescription = null) },
                    modifier = Modifier.clickable {
                        viewModel.deleteMessage(contextMenuMessage!!.id)
                        contextMenuMessage = null
                    }
                )
            }
        }
    }

    // For TopAppBar info (agent name, avatar) we would ideally join tables or 
    // fetch the agent. In a real app we might pass agent details or have a ConversationWithAgent model.
    // For now we just use a generic title.

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        Column(modifier = Modifier.clickable { showAgentDetails = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (currentAgent?.isActive == true) Color(0xFF4CAF50) else Color(0xFF9E9E9E))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = currentAgent?.name ?: "Chat",
                                    style = MaterialTheme.typography.titleMedium
                                ) 
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(4.dp))
                                Text("End-to-End Encrypted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigateToCall("Agent") }) {
                            Icon(Icons.Default.Phone, contentDescription = "Call Agent")
                        }
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Agent Memory") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToMemory(currentAgentId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Chat") },
                                onClick = {
                                    viewModel.deleteConversation(conversationId)
                                    showMenu = false
                                    onNavigateBack()
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
            }
        },
        bottomBar = {
            val keyboardShortcuts by viewModel.keyboardShortcuts.collectAsStateWithLifecycle()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .imePadding()
            ) {
                AnimatedVisibility(visible = isStreaming) {
                    AgentActivityBubble(statusText = agentActivityStatus ?: "Thinking...")
                }
                ChatInputBar(
                    activeMemoryCount = activeMemoryCount,
                    keyboardShortcuts = keyboardShortcuts,
                    hasTools = availableTools.isNotEmpty(),
                    replyingTo = replyingTo,
                    onClearReply = { replyingTo = null },
                    isStreaming = isStreaming,
                    agentActivityStatus = agentActivityStatus,
                    onSaveShortcuts = { updated -> viewModel.saveKeyboardShortcuts(updated) },
                    onMemoryClick = { onNavigateToMemory(currentAgentId) },
                    onToolsClick = { showToolsSheet = true },
                    onSendMessage = { content, attachments ->
                        val replyPrefix = replyingTo?.content?.let { orig ->
                            val truncated = orig.take(50)
                            val ellipsis = if (orig.length > 50) "..." else ""
                            "[Replying to: \"$truncated$ellipsis\"]\n"
                        } ?: ""
                        val finalContent = replyPrefix + content
                        viewModel.sendMessage(finalContent, attachments)
                        replyingTo = null
                    }
                )
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        
        val listState = rememberLazyListState()
        val context = LocalContext.current

        val shouldScrollToBottom by remember {
            derivedStateOf { messages.itemCount }
        }
        LaunchedEffect(shouldScrollToBottom) {
            if (shouldScrollToBottom > 0) {
                listState.animateScrollToItem(0)
            }
        }

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
                android.util.Log.e("ChatScreen", "Error starting/stopping streaming service", e)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
                reverseLayout = true
            ) {
                items(
                    count = messages.itemCount,
                    key = { index -> messages.peek(index)?.id ?: "placeholder_$index" }
                ) { index ->
                    val message = messages[index]
                    if (message != null) {
                        MessageBubble(
                            message = message,
                            fontSizePref = fontSizePref,
                            modifier = Modifier.animateItem(),
                            onReply = { replyingTo = it },
                            onLongClick = { contextMenuMessage = it },
                            isStreamingAndLast = isStreaming && index == 0
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    fontSizePref: String = "medium",
    modifier: Modifier = Modifier, 
    onReply: ((Message) -> Unit)? = null,
    onLongClick: ((Message) -> Unit)? = null,
    isStreamingAndLast: Boolean = false
) {
    val isUser = message.role == "user"
    val isToolResult = message.role == "tool_result"
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val androidContext = LocalContext.current

    val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onReply?.invoke(message)
            }
            false // always snap back
        }
    )

    androidx.compose.material3.SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        enableDismissFromStartToEnd = onReply != null,
        backgroundContent = {
            val color by androidx.compose.animation.animateColorAsState(
                if (dismissState.targetValue == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) 
                    MaterialTheme.colorScheme.primaryContainer 
                else Color.Transparent, label = "swipeColor"
            )
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (dismissState.targetValue == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Reply",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongClick?.invoke(message) }
                ),
            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            val attachments = remember(message.attachments) {
                try {
                    val attachmentsJson = message.attachments
                    if (!attachmentsJson.isNullOrEmpty()) {
                        kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<List<com.ekam.baton.core.network.mcp.AttachmentDto>>(attachmentsJson)
                    } else emptyList()
                } catch (e: Exception) { emptyList() }
            }

            if (isUser) {
                // User Bubble
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        attachments.forEach { attachment ->
                            val mimeType = attachment.mimeType.lowercase()
                            when {
                                mimeType.startsWith("image/") -> {
                                    coil.compose.AsyncImage(
                                        model = attachment.uri ?: attachment.dataBase64,
                                        contentDescription = "Attachment",
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .heightIn(max = 300.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .padding(bottom = 8.dp)
                                            .clickable {
                                                attachment.uri?.let { uriString ->
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = attachment.mimeType
                                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    androidContext.startActivity(Intent.createChooser(intent, "Share File"))
                                                }
                                            },
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                mimeType == "application/pdf" -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                attachment.uri?.let { uriString ->
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = attachment.mimeType
                                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    androidContext.startActivity(Intent.createChooser(intent, "Share File"))
                                                }
                                            }
                                            .padding(12.dp)
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFE53935)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("PDF", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = attachment.fileName ?: "Document.pdf",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${(attachment.fileSize ?: 0) / 1024} KB",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                                mimeType.contains("excel") || mimeType.contains("spreadsheet") -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                attachment.uri?.let { uriString ->
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = attachment.mimeType
                                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    androidContext.startActivity(Intent.createChooser(intent, "Share File"))
                                                }
                                            }
                                            .padding(12.dp)
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF4CAF50)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("XLS", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = attachment.fileName ?: "Spreadsheet.xlsx",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${(attachment.fileSize ?: 0) / 1024} KB",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                                else -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                attachment.uri?.let { uriString ->
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = attachment.mimeType
                                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    androidContext.startActivity(Intent.createChooser(intent, "Share File"))
                                                }
                                            }
                                            .padding(12.dp)
                                            .padding(bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(androidx.compose.material.icons.Icons.Default.AttachFile, contentDescription = "File", modifier = Modifier.size(24.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = attachment.fileName ?: "File",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (message.content.isNotEmpty()) {
                            MarkdownRenderer(
                                text = message.content,
                                textColor = MaterialTheme.colorScheme.onTertiary,
                                fontSizePref = fontSizePref
                            )
                        }
                    }
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
                    val displayContent = if (isStreamingAndLast) message.content + " ▋" else message.content
                    MarkdownRenderer(
                        text = displayContent,
                        textColor = MaterialTheme.colorScheme.onBackground,
                        fontSizePref = fontSizePref
                    )
                }
            }
        }
    }
}

@Composable
fun AgentActivityBubble(statusText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
                for (i in 0 until 3) {
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 600, delayMillis = i * 200, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot${i}Alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                    )
                }
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChatInputBar(
    activeMemoryCount: Int,
    keyboardShortcuts: List<KeyboardShortcut>,
    hasTools: Boolean,
    onSaveShortcuts: (List<KeyboardShortcut>) -> Unit,
    onMemoryClick: () -> Unit,
    onToolsClick: () -> Unit,
    replyingTo: Message?,
    onClearReply: () -> Unit,
    isStreaming: Boolean,
    agentActivityStatus: String?,
    onSendMessage: (String, List<Uri>) -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var attachments by remember { mutableStateOf(emptyList<Uri>()) }
    var showShortcutsDialog by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    
    // File Picker (Documents/Any safe type)
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            attachments = attachments + uris
        }
    }

    // Photo/Video Gallery Picker
    val mediaPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        if (uris.isNotEmpty()) {
            attachments = attachments + uris
        }
    }

    // ML Kit Document Scanner
    val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.forEach { page ->
                attachments = attachments + page.imageUri
            }
            scanResult?.pdf?.let { pdf ->
                attachments = attachments + pdf.uri
            }
        }
    }

    val context = LocalContext.current
    fun launchScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(15)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)
        // FIX: findActivity() can return null if called from a non-Activity context;
        // guard against it rather than force-unwrapping with !!.
        val activity = context.findActivity() ?: return
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                // Scanner unavailable — the ViewModel/snackbar will surface this
                // once we wire the error channel through; log for now.
                android.util.Log.e("ChatScreen", "Document scanner failed to start", e)
            }
    }

    if (showAttachmentSheet) {
        AttachmentPickerBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            onScanDocumentClick = { launchScanner() },
            onGalleryClick = { mediaPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
            onFilesClick = { fileLauncher.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }
        )
    }

    if (showShortcutsDialog) {
        ShortcutManagerDialog(
            shortcuts = keyboardShortcuts,
            onSaveShortcuts = onSaveShortcuts,
            onDismiss = { showShortcutsDialog = false }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            if (replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = if (replyingTo.role == "user") "You" else "Agent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(text = replyingTo.content, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClearReply) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear reply", modifier = Modifier.size(16.dp))
                }
            }
        }
        // Attachments preview
        // FIX: Use LazyRow instead of Row + forEach so that a large number of
        // attachments doesn't cause unbounded Row measurement / recomposition.
        if (attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attachments, key = { it.toString() }) { uri ->
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
                            Text(uri.lastPathSegment ?: "Attachment", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }


        // Combined Shortcuts and Context Toolbar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                // Small Memory Button
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.clickable { onMemoryClick() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("🧠", style = MaterialTheme.typography.labelSmall)
                        if (activeMemoryCount > 0) {
                            Spacer(Modifier.width(4.dp))
                            Text("$activeMemoryCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                IconButton(
                    onClick = { showShortcutsDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Manage Shortcuts",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // FIX: Supply a stable key so shortcuts don't animate/recompose on
            // unrelated list changes (e.g. memory count update above).
            items(keyboardShortcuts, key = { it.label }) { shortcut ->
                val localHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable {
                        localHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (shortcut.isImmediate) {
                            onSendMessage(shortcut.textToInsert, emptyList())
                        } else {
                            val currentText = text.text
                            val selection = text.selection
                            val textToInsert = shortcut.textToInsert
                            val newText = currentText.substring(0, selection.min) + textToInsert + currentText.substring(selection.max)
                            text = TextFieldValue(
                                text = newText,
                                selection = TextRange(selection.min + textToInsert.length)
                            )
                        }
                    }
                ) {
                    Text(
                        text = shortcut.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = { showAttachmentSheet = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasTools) {
                IconButton(
                    onClick = onToolsClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Settings, // Using Settings icon for Tools
                        contentDescription = "Tools",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                if (text.text.isEmpty()) {
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

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isPressed) 0.85f else 1f, label = "sendScale")

            IconButton(
                onClick = {
                    if (text.text.isNotBlank() || attachments.isNotEmpty()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSendMessage(text.text, attachments)
                        text = TextFieldValue("")
                        attachments = emptyList()
                    }
                },
                interactionSource = interactionSource,
                enabled = text.text.isNotBlank() || attachments.isNotEmpty(),
                modifier = Modifier.size(40.dp).scale(scale),
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
}

@Composable
fun ShortcutManagerDialog(
    shortcuts: List<KeyboardShortcut>,
    onSaveShortcuts: (List<KeyboardShortcut>) -> Unit,
    onDismiss: () -> Unit
) {
    var newLabel by remember { mutableStateOf("") }
    var newText by remember { mutableStateOf("") }
    var isImmediate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Keyboard Shortcuts",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (shortcuts.isNotEmpty()) {
                    Text(
                        text = "Current Shortcuts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    shortcuts.forEach { shortcut ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = shortcut.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = shortcut.textToInsert + if (shortcut.isImmediate) " (Immediate)" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    val updated = shortcuts.filter { it != shortcut }
                                    onSaveShortcuts(updated)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No shortcuts configured. Reset to defaults or add below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Add Shortcut",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text("Label (e.g. Help)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    label = { Text("Text to Insert") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Send immediately",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isImmediate,
                        onCheckedChange = { isImmediate = it }
                    )
                }

                Button(
                    onClick = {
                        if (newLabel.isNotBlank() && newText.isNotBlank()) {
                            val updated = shortcuts + KeyboardShortcut(
                                label = newLabel.trim(),
                                textToInsert = newText,
                                isImmediate = isImmediate
                            )
                            onSaveShortcuts(updated)
                            newLabel = ""
                            newText = ""
                            isImmediate = false
                        }
                    },
                    enabled = newLabel.isNotBlank() && newText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Shortcut")
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSaveShortcuts(listOf(
                            KeyboardShortcut(label = "Code", textToInsert = "```\n\n```", isImmediate = false),
                            KeyboardShortcut(label = "Status", textToInsert = "/status", isImmediate = true),
                            KeyboardShortcut(label = "Clear", textToInsert = "/clear", isImmediate = true),
                            KeyboardShortcut(label = "Help", textToInsert = "/help", isImmediate = true)
                        ))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset Defaults")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolExecutionBottomSheet(
    tools: List<com.ekam.baton.core.network.mcp.McpTool>,
    onExecuteTool: (String, kotlinx.serialization.json.JsonObject) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTool by remember { mutableStateOf<com.ekam.baton.core.network.mcp.McpTool?>(null) }
    var argumentsText by remember { mutableStateOf("") }
    var jsonError by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Agent Capabilities",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            if (selectedTool == null) {
                if (tools.isEmpty()) {
                    Text("No tools are exposed by this Agent.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tools.forEach { tool ->
                        Surface(
                            onClick = { selectedTool = tool },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(tool.name, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(tool.description ?: "No description provided", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                val tool = selectedTool!!
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedTool = null; argumentsText = ""; jsonError = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("Execute ${tool.name}", style = MaterialTheme.typography.titleMedium)
                }
                
                Text(tool.description ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                OutlinedTextField(
                    value = argumentsText,
                    onValueChange = { argumentsText = it; jsonError = false },
                    label = { Text("Arguments (JSON)") },
                    isError = jsonError,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace)
                )
                
                if (jsonError) {
                    Text("Invalid JSON format", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        try {
                            val args = if (argumentsText.isBlank()) {
                                kotlinx.serialization.json.JsonObject(emptyMap<String, kotlinx.serialization.json.JsonElement>())
                            } else {
                                kotlinx.serialization.json.Json.parseToJsonElement(argumentsText) as kotlinx.serialization.json.JsonObject
                            }
                            onExecuteTool(tool.name, args)
                            onDismiss()
                        } catch (e: Exception) {
                            jsonError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Execute")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun JITAuthorizationDialog(
    request: com.ekam.baton.core.network.mcp.ToolAuthorizationRequest,
    onResult: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Must be explicitly allowed/denied */ },
        title = {
            Text(
                text = "Authorization Required",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The Agent is requesting to execute a destructive tool. Do you want to allow this action?", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text("Tool Name:", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(request.toolName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text("Arguments:", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = request.arguments.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onResult(true) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Allow")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onResult(false) }) {
                Text("Deny")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

fun android.content.Context.findActivity(): android.app.Activity? {
    var currentContext = this
    while (currentContext is android.content.ContextWrapper) {
        if (currentContext is android.app.Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
