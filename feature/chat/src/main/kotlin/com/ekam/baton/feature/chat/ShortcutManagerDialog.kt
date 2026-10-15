package com.ekam.baton.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ekam.baton.core.data.preferences.KeyboardShortcut

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
