package com.ekam.baton.feature.agents.a2a

import android.Manifest
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.ekam.baton.core.ui.components.glassCard
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import android.util.Base64
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun AirGapScannerScreen(
    onNavigateBack: () -> Unit,
    viewModel: A2AViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    // Laser Animation
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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
                            .setTargetResolution(Size(1280, 720))
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

        // Overlay & Reticle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val boxSize = canvasWidth * 0.7f
            val boxRect = Rect(
                offset = Offset((canvasWidth - boxSize) / 2f, (canvasHeight - boxSize) / 2f),
                size = GeometrySize(boxSize, boxSize)
            )

            val overlayPath = Path().apply {
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        boxRect,
                        CornerRadius(48f, 48f)
                    )
                )
                fillType = PathFillType.EvenOdd
            }
            
            drawPath(
                path = overlayPath,
                color = Color(0xFF0D131F).copy(alpha = 0.85f) // Deep Ocean Blue Frosted
            )

            // Reticle Border (Glowing Squircle)
            drawRoundRect(
                color = Color(0xFF306EE8), // Electric Cobalt
                topLeft = boxRect.topLeft,
                size = boxRect.size,
                cornerRadius = CornerRadius(48f, 48f),
                style = Stroke(width = 8f)
            )

            // Laser Beam
            val laserYPos = boxRect.top + (boxRect.height * laserY)
            drawLine(
                color = Color(0xFF306EE8).copy(alpha = 0.6f),
                start = Offset(boxRect.left + 20f, laserYPos),
                end = Offset(boxRect.right - 20f, laserYPos),
                strokeWidth = 6f
            )
        }

        // UI Elements
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .glassCard(shape = RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "SCANNING SOVEREIGN PEER...",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            // Bottom Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(64.dp)
                        .glassCard(shape = RoundedCornerShape(20.dp))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                }
                
                IconButton(
                    onClick = {
                        val nextTorch = !isTorchOn
                        camera?.cameraControl?.enableTorch(nextTorch)
                        isTorchOn = nextTorch
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .glassCard(shape = RoundedCornerShape(20.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashlightOn,
                        contentDescription = "Flashlight",
                        tint = if (isTorchOn) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            }
        }
        
        // Processing State
        if (scannedPayload != null) {
            LaunchedEffect(scannedPayload) {
                try {
                    // Decompress the QR Payload
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
                        // We generated an offer, so this scan must be the Answer
                        viewModel.completeHandshake(rawSdp)
                    } else {
                        // We are scanning an offer to generate an answer
                        viewModel.receiveOfferAndGenerateAnswer(rawSdp)
                    }
                    delay(500)
                    onNavigateBack() // Return to A2A Screen
                } catch (e: Exception) {
                    e.printStackTrace()
                    isProcessing = false
                    scannedPayload = null // Reset on fail
                }
            }
            
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF306EE8))
            }
        }
    }
}
