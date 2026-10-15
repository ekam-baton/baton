package com.ekam.baton.feature.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekam.baton.core.network.mcp.McpTool
import com.ekam.baton.core.network.mcp.ToolAuthorizationRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolExecutionBottomSheet(
    tools: List<McpTool>,
    onExecuteTool: (String, JsonObject) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTool by remember { mutableStateOf<McpTool?>(null) }
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
                text = "Available Agent Tools",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            tools.forEach { tool ->
                Surface(
                    color = if (selectedTool?.name == tool.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    onClick = {
                        selectedTool = tool
                        argumentsText = "{}"
                        jsonError = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = tool.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tool.description ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (selectedTool != null) {
                HorizontalDivider()
                Text(
                    text = "Configure Arguments for ${selectedTool!!.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedTextField(
                    value = argumentsText,
                    onValueChange = {
                        argumentsText = it
                        jsonError = false
                    },
                    label = { Text("Arguments (JSON)") },
                    isError = jsonError,
                    supportingText = {
                        if (jsonError) {
                            Text("Invalid JSON format", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(
                    onClick = {
                        try {
                            val json = Json.parseToJsonElement(argumentsText).jsonObject
                            onExecuteTool(selectedTool!!.name, json)
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
    request: ToolAuthorizationRequest,
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
                Text("Tool Name:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(request.toolName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                Text("Arguments:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
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
