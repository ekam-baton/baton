package com.ekam.baton.feature.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ekam.baton.core.data.model.Message

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    fontSizePref: String = "medium",
    modifier: Modifier = Modifier, 
    onReply: ((Message) -> Unit)? = null,
    onLongClick: ((Message) -> Unit)? = null,
    onRetry: ((Message) -> Unit)? = null,
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
                else Color.Transparent,
                label = "swipeColor"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Reply",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick?.invoke(message)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (message.replyToMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .widthIn(max = 280.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = message.replyToMessage!!.content,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isUser) {
                // User Bubble
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column {
                        message.attachments?.forEach { attachment ->
                            when {
                                attachment.mimeType.startsWith("image/") -> {
                                    attachment.uri?.let { uriString ->
                                        coil3.compose.AsyncImage(
                                            model = Uri.parse(uriString),
                                            contentDescription = "Attached Image",
                                            modifier = Modifier
                                                .fillMaxWidth(0.7f)
                                                .heightIn(max = 200.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(Uri.parse(uriString), attachment.mimeType)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    androidContext.startActivity(intent)
                                                }
                                                .padding(bottom = 8.dp),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                                attachment.mimeType == "application/pdf" -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                attachment.uri?.let { uriString ->
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    androidContext.startActivity(Intent.createChooser(intent, "Share PDF"))
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
                                attachment.mimeType.contains("sheet") || attachment.mimeType.contains("excel") -> {
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
                                        Icon(Icons.Default.AttachFile, contentDescription = "File", modifier = Modifier.size(24.dp))
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
                
                if (message.isFailed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Failed to send", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Retry", 
                            color = MaterialTheme.colorScheme.primary, 
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { onRetry?.invoke(message) }
                        )
                    }
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
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
