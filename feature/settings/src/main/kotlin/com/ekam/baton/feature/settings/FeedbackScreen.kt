package com.ekam.baton.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onNavigateBack: () -> Unit,
    onSubmitFeedback: (String, String) -> Unit // (type, description)
) {
    var feedbackType by remember { mutableStateOf("Bug Report") }
    var description by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Feedback") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Help us improve Baton. What kind of feedback do you have?")
            
            // Simple Dropdown/Selector mockup
            val types = listOf("Bug Report", "Feature Request", "Agent Issue")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { type ->
                    FilterChip(
                        selected = (type == feedbackType),
                        onClick = { feedbackType = type },
                        label = { Text(type) }
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Description") },
                placeholder = { Text("Please describe the issue or suggestion in detail...") }
            )

            Button(
                onClick = {
                    if (description.isNotBlank()) {
                        onSubmitFeedback(feedbackType, description)
                        scope.launch {
                            snackbarHostState.showSnackbar("Feedback sent! Our Autonomous Agent will review it.")
                            onNavigateBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = description.isNotBlank()
            ) {
                Text("Submit")
            }
        }
    }
}
