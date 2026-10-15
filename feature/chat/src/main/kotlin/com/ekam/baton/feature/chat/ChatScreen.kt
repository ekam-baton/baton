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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Block
import com.ekam.baton.core.data.ugc.ReportReason
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
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

private val attachmentJsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMemory: (String) -> Unit,
    onNavigateToAgentDetails: (String) -> Unit,
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
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    
    var contextMenuMessage by remember { mutableStateOf<Message?>(null) }
    var showForwardPicker by remember { mutableStateOf(false) }
    val agents by viewModel.agents.collectAsStateWithLifecycle()
    val isAgentBlocked by viewModel.isAgentBlocked.collectAsStateWithLifecycle()
    var showReportDialog by remember { mutableStateOf(false) }
    var reportTargetType by remember { mutableStateOf("MESSAGE") }
    var reportTargetId by remember { mutableStateOf("") }
    var showTopOverflowMenu by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(contextMenuMessage!!.content))
                        contextMenuMessage = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Share Text") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showForwardPicker = true
                    }
                )
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, tint = MaterialTheme.colorScheme.error, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteMessage(contextMenuMessage!!.id)
                        contextMenuMessage = null
                    }
                )
                ListItem(
                    headlineContent = { Text("Report Message", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Flag, tint = MaterialTheme.colorScheme.error, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val msgId = contextMenuMessage!!.id
                        contextMenuMessage = null
                        reportTargetType = "MESSAGE"
                        reportTargetId = msgId
                        showReportDialog = true
                    }
                )
            }
        }
    }

    if (showReportDialog) {
        ReportContentDialog(
            targetType = reportTargetType,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, details ->
                if (reportTargetType == "MESSAGE") {
                    viewModel.reportMessage(reportTargetId, reason, details) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Report submitted. Thank you for keeping Baton safe.")
                        }
                    }
                } else {
                    viewModel.reportCurrentAgent(reason, details) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Agent reported. Thank you for your feedback.")
                        }
                    }
                }
                showReportDialog = false
            }
        )
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
                        Column(
                            modifier = Modifier.clickable { 
                                val targetId = currentAgentId ?: currentAgent?.id
                                if (targetId != null) {
                                    onNavigateToAgentDetails(targetId)
                                }
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (currentAgent?.isActive == true) Color(0xFF10B981) else Color(0xFF9E9E9E))
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = currentAgent?.name ?: "Chat",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                ) 
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { currentAgent?.id?.let(onNavigateToAgentDetails) }) {
                            Icon(Icons.Default.Info, contentDescription = "Agent Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { showTopOverflowMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(
                                expanded = showTopOverflowMenu,
                                onDismissRequest = { showTopOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isAgentBlocked) "Unblock Agent" else "Block Agent") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Block,
                                            contentDescription = null,
                                            tint = if (isAgentBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showTopOverflowMenu = false
                                        viewModel.toggleBlockCurrentAgent()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Report Agent", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Flag,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        val agentId = currentAgentId ?: currentAgent?.id
                                        if (agentId != null) {
                                            reportTargetType = "AGENT"
                                            reportTargetId = agentId
                                            showReportDialog = true
                                        }
                                    }
                                )
                            }
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
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                if (isAgentBlocked) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = Color(0xFF1E1520),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Agent Blocked",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "You cannot send or receive messages while this agent is blocked.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Button(
                                onClick = { viewModel.toggleBlockCurrentAgent() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Unblock")
                            }
                        }
                    }
                } else {
                    AnimatedVisibility(visible = isStreaming) {
                        AgentActivityBubble(statusText = agentActivityStatus ?: "Thinking...")
                    }
                val latestMessage = if (messages.itemCount > 0) messages[0] else null
                val suggestions = if (latestMessage?.role == "assistant" && !isStreaming) latestMessage.suggestions else null
                ChatInputBar(
                    suggestions = suggestions,
                    activeMemoryCount = activeMemoryCount,
                    keyboardShortcuts = keyboardShortcuts,
                    hasTools = availableTools.isNotEmpty(),
                    replyingTo = replyingTo,
                    onClearReply = { replyingTo = null },
                    isStreaming = isStreaming,
                    agentActivityStatus = agentActivityStatus,
                    onSaveShortcuts = { updated -> viewModel.saveKeyboardShortcuts(updated) },
                    onMemoryClick = { currentAgentId?.let { onNavigateToMemory(it) } },
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
            if (messages.itemCount == 0) {
                SovereignEmptyState(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

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
                            onRetry = { viewModel.retryMessage(it) },
                            isStreamingAndLast = isStreaming && index == 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SovereignEmptyState(
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = if (visible) 0.35f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "emptyLogoAlpha"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "emptyLogoScale"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (visible) 0.45f else 0f,
        animationSpec = tween(durationMillis = 900, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "emptyTextAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.ic_baton_logo),
            contentDescription = "Baton Logo",
            modifier = Modifier
                .height(130.dp)
                .scale(animatedScale)
                .alpha(animatedAlpha)
        )

        Text(
            text = "Finding Peace in the Chaos",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = textAlpha),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                letterSpacing = 1.2.sp
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
