package com.ekam.baton.feature.agents.a2a

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.ui.components.glassCard
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.koin.compose.viewmodel.koinViewModel
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirGapQrScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit = {},
    viewModel: A2AViewModel = koinViewModel()
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val sdpOffer by viewModel.sdpOffer.collectAsStateWithLifecycle()
    val sdpAnswer by viewModel.sdpAnswer.collectAsStateWithLifecycle()
    
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Compress and generate QR code for SDP
    LaunchedEffect(sdpOffer, sdpAnswer) {
        val payload = sdpAnswer ?: sdpOffer
        if (payload != null) {
            try {
                // Compress payload for denser QR Code
                val deflater = Deflater(Deflater.BEST_COMPRESSION)
                deflater.setInput(payload.toByteArray(Charsets.UTF_8))
                deflater.finish()
                val buffer = ByteArray(1024)
                val os = ByteArrayOutputStream()
                while (!deflater.finished()) {
                    val count = deflater.deflate(buffer)
                    os.write(buffer, 0, count)
                }
                os.close()
                val compressedBase64 = Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
                
                val writer = QRCodeWriter()
                val bitMatrix = writer.encode(compressedBase64, BarcodeFormat.QR_CODE, 800, 800)
                val width = bitMatrix.width
                val height = bitMatrix.height
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        bmp.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                    }
                }
                qrBitmap = bmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Generate offer if none exists
            viewModel.generateOffer()
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D131F), // Deep Ocean Blue
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Air-Gapped Signaling",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Secure Sovereign P2P Handshake",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Instructional Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Scan this QR code with the peer agent to securely pass the WebRTC encrypted tunnel keys.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // QR Code display
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .background(Color.White, shape = RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "Signaling QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator()
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (sdpAnswer != null) "AWAITING FINAL SCAN" else "AWAITING PEER SCAN",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // Action Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onNavigateToScanner()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SCAN PEER'S CODE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
            }
        }
    }
}
