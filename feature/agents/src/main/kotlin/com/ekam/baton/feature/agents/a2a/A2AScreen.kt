package com.ekam.baton.feature.agents.a2a

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun A2AScreen(
    viewModel: A2AViewModel = koinViewModel()
) {
    val ephemeralPublicKey by viewModel.ephemeralPublicKey.collectAsState()
    val activeTunnels by viewModel.activeTunnels.collectAsState()

    val sdpOffer by viewModel.sdpOffer.collectAsState()
    val sdpAnswer by viewModel.sdpAnswer.collectAsState()

    var remoteOfferInput by remember { mutableStateOf("") }
    var remoteAnswerInput by remember { mutableStateOf("") }
    
    val scanOfferLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) { remoteOfferInput = result.contents }
    }
    val scanAnswerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) { remoteAnswerInput = result.contents }
    }
    
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("A2A Protocol", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    IconButton(onClick = { viewModel.rotateIdentity() }) {
                        Icon(Icons.Default.Sync, contentDescription = "Rotate Keys", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section 1: Identity Broadcast
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2C)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Your Identity Broadcast",
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        QrCodeImage(
                            content = ephemeralPublicKey,
                            modifier = Modifier.size(160.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = ephemeralPublicKey,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Ephemeral Public Key (X25519)") },
                            trailingIcon = {
                                IconButton(onClick = { 
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(ephemeralPublicKey))
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF232D4B),
                                unfocusedBorderColor = Color(0xFF232D4B)
                            )
                        )
                    }
                }
            }

            // Section 2: WebRTC Handshake Initialization
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2C)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Step 1: Host Connection (Create Offer)", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        Text("If you are initiating the connection, tap generate below. Wait for the ICE candidate gathering to complete, then copy the resulting Offer block and send it to your peer.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.generateOffer() },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Generate Offer SDP")
                        }
                        if (sdpOffer != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = sdpOffer ?: "", onValueChange = {}, readOnly = true, label = { Text("Offer (Send this to peer)") },
                                trailingIcon = {
                                    IconButton(onClick = { 
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(sdpOffer ?: ""))
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            QrCodeImage(content = sdpOffer ?: "", modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color(0xFF232D4B))

                        Text("Step 2: Join Connection (Answer Offer)", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        Text("If your peer sent you an Offer block, paste it here to generate an Answer block. Once generated, send the Answer block back to them.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = remoteOfferInput, onValueChange = { remoteOfferInput = it }, label = { Text("Paste Peer's Offer here") },
                            trailingIcon = {
                                IconButton(onClick = { scanOfferLauncher.launch(ScanOptions()) }) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.receiveOfferAndGenerateAnswer(remoteOfferInput) },
                            enabled = remoteOfferInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Generate Answer SDP")
                        }
                        if (sdpAnswer != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = sdpAnswer ?: "", onValueChange = {}, readOnly = true, label = { Text("Answer (Send back to peer)") },
                                trailingIcon = {
                                    IconButton(onClick = { 
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(sdpAnswer ?: ""))
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            QrCodeImage(content = sdpAnswer ?: "", modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color(0xFF232D4B))

                        Text("Step 3: Complete Handshake", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        Text("If you created the initial Offer in Step 1, paste the Answer block you received from your peer here to finalize the secure tunnel.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = remoteAnswerInput, onValueChange = { remoteAnswerInput = it }, label = { Text("Paste Peer's Answer here") },
                            trailingIcon = {
                                IconButton(onClick = { scanAnswerLauncher.launch(ScanOptions()) }) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.completeHandshake(remoteAnswerInput) },
                            enabled = remoteAnswerInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Complete Handshake", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 3: Active Tunnels
            item {
                Text(
                    text = "Active A2A Tunnels",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (activeTunnels.isEmpty()) {
                item {
                    Text(
                        text = "No active tunnels.",
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                items(activeTunnels) { tunnel ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A2C)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF34A853), shape = androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(tunnel, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
