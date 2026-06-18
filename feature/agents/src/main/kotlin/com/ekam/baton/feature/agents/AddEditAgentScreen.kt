package com.ekam.baton.feature.agents

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.network.tunnel.TunnelValidationResult
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private val ACCENT_COLORS = listOf(
    "#3D8EFF", // BatonElectric
    "#FF453A", // Red
    "#32D74B", // Green
    "#FF9F0A", // Orange
    "#BF5AF2", // Purple
    "#64D2FF", // Light Blue
    "#FF375F", // Pink
    "#FFD60A"  // Yellow
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAgentScreen(
    agentId: String?,
    onNavigateBack: () -> Unit,
    viewModel: AgentsViewModel = hiltViewModel()
) {
    val agents by viewModel.agents.collectAsState()
    val existingAgent = remember(agents, agentId) { agents.find { it.id == agentId } }

    var name by remember { mutableStateOf(existingAgent?.name ?: "") }
    var description by remember { mutableStateOf(existingAgent?.description ?: "") }
    var endpointUrl by remember { mutableStateOf(existingAgent?.mcpEndpointUrl ?: "") }
    var selectedColor by remember { mutableStateOf(existingAgent?.colorAccent ?: ACCENT_COLORS.first()) }
    
    // Auth
    val authOptions = listOf("None", "API Key", "OAuth 2.1")
    var selectedAuthIndex by remember { 
        mutableIntStateOf(
            when (existingAgent?.authType) {
                "api_key" -> 1
                "oauth" -> 2
                else -> 0
            }
        ) 
    }
    var apiKey by remember { mutableStateOf("") }
    var oauthClientId by remember { mutableStateOf("") }
    var oauthAuthUrl by remember { mutableStateOf("") }
    var oauthTokenUrl by remember { mutableStateOf("") }
    var oauthScopes by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // Advanced Security
    var showAdvanced by remember { mutableStateOf(false) }
    val initialSecurityMode = remember { existingAgent?.securityMode ?: "standard" }
    var securityMode by remember { mutableStateOf(initialSecurityMode) }
    var clientPublicKey by remember { mutableStateOf("") }
    var clientPrivateKeyEnc by remember { mutableStateOf("") }
    var clientPrivateKeyIv by remember { mutableStateOf("") }
    var peerPublicKey by remember { mutableStateOf("") }
    var certPins by remember { mutableStateOf("") }

    var showDowngradeWarning by remember { mutableStateOf(false) }
    var pendingSecurityMode by remember { mutableStateOf<String?>(null) }

    // Load existing config
    LaunchedEffect(existingAgent) {
        if (existingAgent != null) {
            try {
                val json = JSONObject(existingAgent.authConfig)
                if (existingAgent.authType == "api_key") apiKey = json.optString("api_key", "")
                else if (existingAgent.authType == "oauth") {
                    oauthClientId = json.optString("client_id", "")
                    oauthAuthUrl = json.optString("auth_url", "")
                    oauthTokenUrl = json.optString("token_url", "")
                    oauthScopes = json.optString("scopes", "")
                }
            } catch (e: Exception) {}
            try {
                val secJson = JSONObject(existingAgent.securityConfig)
                clientPublicKey = secJson.optString("client_public_key", "")
                clientPrivateKeyEnc = secJson.optString("client_private_key_enc", "")
                clientPrivateKeyIv = secJson.optString("client_private_key_iv", "")
                peerPublicKey = secJson.optString("peer_public_key", "")
                val pinsArray = secJson.optJSONArray("cert_pins")
                if (pinsArray != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until pinsArray.length()) list.add(pinsArray.getString(i))
                    certPins = list.joinToString(", ")
                }
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(securityMode) {
        if (securityMode != "standard" && clientPublicKey.isBlank()) {
            val keys = viewModel.generateClientKeys()
            clientPublicKey = keys.publicKeyHex
            clientPrivateKeyEnc = keys.encryptedPrivateKeyBase64
            clientPrivateKeyIv = keys.privateKeyIvBase64
        }
    }

    val isUrlValid = endpointUrl.startsWith("http://") || endpointUrl.startsWith("https://")
    val isSecurityValid = securityMode == "standard" || peerPublicKey.isNotBlank()
    val isValid = name.isNotBlank() && endpointUrl.isNotBlank() && isUrlValid && isSecurityValid &&
            (selectedAuthIndex != 2 || (oauthClientId.isNotBlank() && oauthAuthUrl.isNotBlank() && oauthTokenUrl.isNotBlank()))

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (agentId == null) "New AI Connection" else "Edit AI Connection", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val authType = when (selectedAuthIndex) {
                                1 -> "api_key"
                                2 -> "oauth"
                                else -> "none"
                            }
                            val authConfig = JSONObject().apply {
                                if (authType == "api_key") put("api_key", apiKey)
                                if (authType == "oauth") {
                                    put("client_id", oauthClientId)
                                    put("auth_url", oauthAuthUrl)
                                    put("token_url", oauthTokenUrl)
                                    put("scopes", oauthScopes)
                                }
                            }.toString()

                            val securityConfig = JSONObject().apply {
                                if (securityMode != "standard") {
                                    put("client_public_key", clientPublicKey)
                                    put("client_private_key_enc", clientPrivateKeyEnc)
                                    put("client_private_key_iv", clientPrivateKeyIv)
                                    put("peer_public_key", peerPublicKey)
                                    val pinsList = certPins.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    put("cert_pins", org.json.JSONArray(pinsList))
                                }
                            }.toString()

                            val targetAgentId = existingAgent?.id ?: UUID.randomUUID().toString()
                            val agent = AgentEntity(
                                id = targetAgentId,
                                name = name,
                                description = description,
                                mcpEndpointUrl = endpointUrl,
                                authType = authType,
                                authConfig = authConfig,
                                colorAccent = selectedColor,
                                createdAt = existingAgent?.createdAt ?: System.currentTimeMillis(),
                                securityMode = securityMode,
                                securityConfig = securityConfig
                            )
                            if (existingAgent == null) viewModel.addAgent(agent) else viewModel.updateAgent(agent)

                            if (authType == "oauth") {
                                val config = com.ekam.baton.core.network.auth.OAuthConfig(
                                    clientId = oauthClientId,
                                    authorizationUrl = oauthAuthUrl,
                                    tokenUrl = oauthTokenUrl,
                                    scopes = oauthScopes.split(",").map { it.trim() }
                                )
                                val authUrl = viewModel.startOAuthFlow(targetAgentId, config)
                                viewModel.launchAuthBrowser(context, authUrl)
                            }
                            onNavigateBack()
                        },
                        enabled = isValid,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(android.graphics.Color.parseColor(selectedColor))),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // SECTION: IDENTITY
            PremiumCardGroup(title = "Identity") {
                PremiumTextField(value = name, onValueChange = { name = it }, label = "AI Name (required)")
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                PremiumTextField(value = description, onValueChange = { description = it }, label = "Description (optional)", minLines = 2)
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme Color", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(ACCENT_COLORS) { colorHex ->
                            val color = Color(android.graphics.Color.parseColor(colorHex))
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(2.dp, if (selectedColor == colorHex) MaterialTheme.colorScheme.onBackground else Color.Transparent, CircleShape)
                                    .clickable { selectedColor = colorHex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == colorHex) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            // SECTION: CONNECTION
            PremiumCardGroup(title = "Connection") {
                PremiumTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = "Connection Link (required)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = endpointUrl.isNotBlank() && !isUrlValid
                )
                
                // Testing UI inside Connection
                var validationResult by remember { mutableStateOf<TunnelValidationResult?>(null) }
                var isValidating by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()
                val tunnelValidator = viewModel.getTunnelValidator()

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp), contentAlignment = Alignment.CenterEnd) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                isValidating = true
                                validationResult = tunnelValidator.validateEndpoint(endpointUrl)
                                isValidating = false
                            }
                        },
                        enabled = isUrlValid && !isValidating
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing...")
                        } else {
                            Text("Test Link")
                        }
                    }
                }

                validationResult?.let { result ->
                    val (color, text) = when (result.status) {
                        com.ekam.baton.core.network.tunnel.Status.VALID -> Color(0xFF32D74B) to "✓ Connected — found ${result.serverName ?: "agent"}"
                        com.ekam.baton.core.network.tunnel.Status.REACHABLE_NO_MCP -> Color(0xFFFF9F0A) to "⚠ Reachable but no MCP detected. Check agent is running."
                        com.ekam.baton.core.network.tunnel.Status.UNREACHABLE -> Color(0xFFFF453A) to "✗ Unreachable. Check URL and ensure cloudflared is running."
                        com.ekam.baton.core.network.tunnel.Status.INVALID_URL -> Color(0xFFFF453A) to "✗ Invalid URL format."
                    }
                    Surface(color = color.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
                            if (result.error != null && result.status != com.ekam.baton.core.network.tunnel.Status.INVALID_URL) {
                                Text(text = result.error!!, color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // SECTION: ACCESS KEYS
            PremiumCardGroup(title = "Access Keys") {
                Column(modifier = Modifier.padding(16.dp)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        authOptions.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = authOptions.size),
                                onClick = { selectedAuthIndex = index },
                                selected = index == selectedAuthIndex
                            ) { Text(label) }
                        }
                    }
                }

                if (selectedAuthIndex == 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    PremiumTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = "API Key",
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle visibility")
                            }
                        }
                    )
                } else if (selectedAuthIndex == 2) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    PremiumTextField(value = oauthClientId, onValueChange = { oauthClientId = it }, label = "Client ID (required)")
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    PremiumTextField(value = oauthAuthUrl, onValueChange = { oauthAuthUrl = it }, label = "Authorization URL (required)")
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    PremiumTextField(value = oauthTokenUrl, onValueChange = { oauthTokenUrl = it }, label = "Token URL (required)")
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    PremiumTextField(value = oauthScopes, onValueChange = { oauthScopes = it }, label = "Scopes (comma-separated)")
                }
            }

            // SECTION: ADVANCED CONFIGURATION
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showAdvanced = !showAdvanced }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced Configuration", style = MaterialTheme.typography.titleMedium)
                    Icon(if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Toggle")
                }
                
                AnimatedVisibility(visible = showAdvanced) {
                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Connection Security Protocol", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val securityOptions = listOf("Standard", "Secured", "Signed", "Sovereign")
                            val securityModes = listOf("standard", "secured", "signed", "sovereign")
                            val currentModeIndex = securityModes.indexOf(securityMode).coerceAtLeast(0)
                            val modeToLevel = mapOf("standard" to 0, "secured" to 1, "signed" to 2, "sovereign" to 3)
                            
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                securityOptions.forEachIndexed { index, label ->
                                    SegmentedButton(
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = securityOptions.size),
                                        onClick = {
                                            val newMode = securityModes[index]
                                            val currentLevel = modeToLevel[securityMode] ?: 0
                                            val newLevel = modeToLevel[newMode] ?: 0
                                            val initialLevel = modeToLevel[initialSecurityMode] ?: 0
                                            
                                            if (newLevel < currentLevel && newLevel < initialLevel) {
                                                pendingSecurityMode = newMode
                                                showDowngradeWarning = true
                                            } else {
                                                securityMode = newMode
                                            }
                                        },
                                        selected = index == currentModeIndex
                                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                                }
                            }

                            if (securityMode != "standard") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Client Cryptographic Identity (X25519)", style = MaterialTheme.typography.titleSmall)
                                Text("Configure this client public key on your PC agent:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val clipboardManager = LocalClipboardManager.current
                                Surface(
                                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (clientPublicKey.length > 32) clientPublicKey.take(24) + "..." + clientPublicKey.takeLast(8) else clientPublicKey,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(clientPublicKey)) }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }

                        if (securityMode != "standard") {
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            PremiumTextField(value = peerPublicKey, onValueChange = { peerPublicKey = it }, label = "Agent Public Key (X25519 hex, required)", isError = peerPublicKey.isBlank())
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            PremiumTextField(value = certPins, onValueChange = { certPins = it }, label = "Certificate Pins (comma-separated SHA-256 hashes, optional)")
                        }
                    }
                }
            }

            if (showDowngradeWarning && pendingSecurityMode != null) {
                AlertDialog(
                    onDismissRequest = { showDowngradeWarning = false; pendingSecurityMode = null },
                    title = { Text("Security Downgrade Warning") },
                    text = { Text("You are downgrading the connection security for this agent below its original configuration. Your traffic may lose end-to-end encryption or replay protections. Are you sure you want to proceed?") },
                    confirmButton = {
                        TextButton(onClick = { securityMode = pendingSecurityMode!!; showDowngradeWarning = false; pendingSecurityMode = null }) {
                            Text("Downgrade", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDowngradeWarning = false; pendingSecurityMode = null }) { Text("Cancel") }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PremiumCardGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
