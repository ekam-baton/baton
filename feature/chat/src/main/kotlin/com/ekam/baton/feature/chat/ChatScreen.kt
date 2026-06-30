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
import com.ekam.baton.core.data.model.Message
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    
    val availableTools by viewModel.availableTools.collectAsStateWithLifecycle()
    val toolAuthRequest by viewModel.toolAuthRequests.collectAsStateWithLifecycle(initialValue = null)
    
    var showToolsSheet by remember { mutableStateOf(false) }
    var showAgentDetails by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
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
                Column {
                    Text("Agent ID: ${currentAgent?.id}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
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

    // For TopAppBar info (agent name, avatar) we would ideally join tables or 
    // fetch the agent. In a real app we might pass agent details or have a ConversationWithAgent model.
    // For now we just use a generic title.

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = currentAgent?.name ?: "Chat",
                        modifier = Modifier.clickable { showAgentDetails = true }
                    ) 
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
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        bottomBar = {
            val keyboardShortcuts by viewModel.keyboardShortcuts.collectAsStateWithLifecycle()
            ChatInputBar(
                activeMemoryCount = activeMemoryCount,
                keyboardShortcuts = keyboardShortcuts,
                hasTools = availableTools.isNotEmpty(),
                replyingTo = replyingTo,
                onClearReply = { replyingTo = null },
                onSaveShortcuts = { updated -> viewModel.saveKeyboardShortcuts(updated) },
                onMemoryClick = { onNavigateToMemory(currentAgentId) },
                onToolsClick = { showToolsSheet = true },
                onSendMessage = { content, attachments ->
                    val replyPrefix = replyingTo?.content?.let { orig ->
                        // FIX: Only append "..." if the content was actually truncated
                        val truncated = orig.take(50)
                        val ellipsis = if (orig.length > 50) "..." else ""
                        "[Replying to: \"$truncated$ellipsis\"]\n"
                    } ?: ""
                    val finalContent = replyPrefix + content
                    viewModel.sendMessage(finalContent, attachments)
                    replyingTo = null
                }
            )
        },

        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        
        val listState = rememberLazyListState()
        val context = LocalContext.current
        
        // FIX: Only auto-scroll on new message count changes, NOT on isStreaming transitions
        // This prevents jumping the user back to bottom when streaming stops
        LaunchedEffect(messages.itemCount) {
            if (messages.itemCount > 0) {
                listState.animateScrollToItem(0)
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

            items(
                count = messages.itemCount,
                key = { index -> messages[index]?.id ?: index }
            ) { index ->
                val message = messages[index]
                if (message != null) {
                    MessageBubble(
                        message = message,
                        modifier = Modifier.animateItem(),
                        onReply = { replyingTo = it }
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, modifier: Modifier = Modifier, onReply: ((Message) -> Unit)? = null) {
    val isUser = message.role == "user"
    val isToolResult = message.role == "tool_result"
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

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
            modifier = Modifier.fillMaxWidth(),
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
                                            .padding(bottom = 8.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                }
                                mimeType == "application/pdf" -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                textColor = MaterialTheme.colorScheme.onTertiary
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
                    MarkdownRenderer(
                        text = message.content,
                        textColor = MaterialTheme.colorScheme.onBackground
                    )
                }
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
    keyboardShortcuts: List<KeyboardShortcut>,
    hasTools: Boolean,
    onSaveShortcuts: (List<KeyboardShortcut>) -> Unit,
    onMemoryClick: () -> Unit,
    onToolsClick: () -> Unit,
    replyingTo: Message?,
    onClearReply: () -> Unit,
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
        scanner.getStartScanIntent(context.findActivity()!!)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                // Handle failure
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
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .padding(8.dp)
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
        
        // Combined Shortcuts and Context Toolbar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
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

            items(keyboardShortcuts) { shortcut ->
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

            IconButton(
                onClick = {
                    if (text.text.isNotBlank() || attachments.isNotEmpty()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSendMessage(text.text, attachments)
                        text = TextFieldValue("")
                        attachments = emptyList()
                    }
                },
                enabled = text.text.isNotBlank() || attachments.isNotEmpty(),
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
