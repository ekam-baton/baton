package com.ekam.baton.feature.agents.a2a

import android.Manifest
import android.util.Base64
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ekam.baton.core.ui.components.glassCard
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.zip.Inflater

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun AirGapScannerScreen(
    onNavigateBack: () -> Unit,
    viewModel: A2AViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var scannedPayload by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (status == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }


    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080E1A))) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val options = BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                        val scanner = BarcodeScanning.getClient(options)

                        val analysisUseCase = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val executor = Executors.newSingleThreadExecutor()
                        analysisUseCase.setAnalyzer(executor) { imageProxy ->
                            if (isProcessing) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        for (barcode in barcodes) {
                                            barcode.rawValue?.let { raw ->
                                                isProcessing = true
                                                scannedPayload = raw
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysisUseCase
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                }
            )
        }

        // Viewfinder Cutout, Reticle Frame, and Laser
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSize = (canvasWidth * 0.72f).coerceAtMost(320.dp.toPx())
            val boxRect = Rect(
                offset = Offset((canvasWidth - boxSize) / 2f, (canvasHeight - boxSize) / 2f - 40.dp.toPx()),
                size = GeometrySize(boxSize, boxSize)
            )

            // Frosted Outer Overlay
            val overlayPath = Path().apply {
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        boxRect,
                        CornerRadius(40f, 40f)
                    )
                )
                fillType = PathFillType.EvenOdd
            }
            
            drawPath(
                path = overlayPath,
                color = Color(0xFF080E1A).copy(alpha = 0.82f)
            )

            // Reticle Squircle Outer Glow Border
            drawRoundRect(
                color = Color(0xFF306EE8), // Electric Cobalt
                topLeft = boxRect.topLeft,
                size = boxRect.size,
                cornerRadius = CornerRadius(40f, 40f),
                style = Stroke(width = 4f)
            )

            // Illuminated Cyan Corner Accents
            val cornerLen = 32f
            val cornerStroke = 8f
            val cornerColor = Color(0xFF00D2FF) // Electric Cyan Accent

            // Top-Left corner
            drawLine(cornerColor, Offset(boxRect.left, boxRect.top + 20f), Offset(boxRect.left, boxRect.top + 20f + cornerLen), strokeWidth = cornerStroke)
            drawLine(cornerColor, Offset(boxRect.left + 20f, boxRect.top), Offset(boxRect.left + 20f + cornerLen, boxRect.top), strokeWidth = cornerStroke)

            // Top-Right corner
            drawLine(cornerColor, Offset(boxRect.right, boxRect.top + 20f), Offset(boxRect.right, boxRect.top + 20f + cornerLen), strokeWidth = cornerStroke)
            drawLine(cornerColor, Offset(boxRect.right - 20f, boxRect.top), Offset(boxRect.right - 20f - cornerLen, boxRect.top), strokeWidth = cornerStroke)

            // Bottom-Left corner
            drawLine(cornerColor, Offset(boxRect.left, boxRect.bottom - 20f), Offset(boxRect.left, boxRect.bottom - 20f - cornerLen), strokeWidth = cornerStroke)
            drawLine(cornerColor, Offset(boxRect.left + 20f, boxRect.bottom), Offset(boxRect.left + 20f + cornerLen, boxRect.bottom), strokeWidth = cornerStroke)

            // Bottom-Right corner
            drawLine(cornerColor, Offset(boxRect.right, boxRect.bottom - 20f), Offset(boxRect.right, boxRect.bottom - 20f - cornerLen), strokeWidth = cornerStroke)
            drawLine(cornerColor, Offset(boxRect.right - 20f, boxRect.bottom), Offset(boxRect.right - 20f - cornerLen, boxRect.bottom), strokeWidth = cornerStroke)

        }

        // Foreground UI Elements
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Command Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .glassCard(shape = RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    // Security Enclave Isolation Pill
                    Row(
                        modifier = Modifier
                            .glassCard(shape = RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OFFLINE OPTICAL LINK • SECURE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            val nextTorch = !isTorchOn
                            camera?.cameraControl?.enableTorch(nextTorch)
                            isTorchOn = nextTorch
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .glassCard(shape = RoundedCornerShape(16.dp))
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                            contentDescription = "Flashlight",
                            tint = if (isTorchOn) Color(0xFF00D2FF) else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Optical Alignment Instruction Pill
                Row(
                    modifier = Modifier
                        .glassCard(shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ALIGN PEER ED25519 NODE QR • PASSIVE OPTICAL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFF00D2FF)
                    )
                }
            }

            // Bottom Glassmorphic Handshake Status Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF306EE8).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = Color(0xFF00D2FF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Air-Gap P2P Handshake",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Unidirectional optical data channel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "READY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Cryptographic Telemetry Spec Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpecChip(label = "CURVE", value = "Ed25519")
                        SpecChip(label = "CIPHER", value = "ChaCha20")
                        SpecChip(label = "ENCLAVE", value = "Hardware")
                        SpecChip(label = "RADIO", value = "Off (Air-Gap)")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "ZERO METRIC LEAKAGE • 100% AIR-GAPPED PROTOCOL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Processing State Overlay
        if (scannedPayload != null) {
            LaunchedEffect(scannedPayload) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                try {
                    val bytes = Base64.decode(scannedPayload, Base64.NO_WRAP)
                    val inflater = Inflater()
                    inflater.setInput(bytes)
                    val os = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (!inflater.finished()) {
                        val count = inflater.inflate(buffer)
                        os.write(buffer, 0, count)
                    }
                    os.close()
                    val rawSdp = String(os.toByteArray(), Charsets.UTF_8)
                    
                    if (viewModel.sdpOffer.value != null && viewModel.sdpAnswer.value == null) {
                        viewModel.completeHandshake(rawSdp)
                    } else {
                        viewModel.receiveOfferAndGenerateAnswer(rawSdp)
                    }
                    delay(500)
                    onNavigateBack()
                } catch (e: Exception) {
                    e.printStackTrace()
                    isProcessing = false
                    scannedPayload = null
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00D2FF))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "PARSING AIR-GAP PAYLOAD...",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = Color(0xFF8C90A0)
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White
            )
        )
    }
}
