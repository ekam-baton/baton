package com.ekam.baton.ui.walkthrough

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.max

enum class WalkthroughStep {
    WELCOME, CHATS, AGENTS, A2A, SETTINGS
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun WalkthroughOverlay(
    walkthroughState: WalkthroughState,
    onFinish: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var currentStep by remember { mutableStateOf(WalkthroughStep.WELCOME) }

    // Sequence logic
    val stepSequence = listOf(
        WalkthroughStep.WELCOME,
        WalkthroughStep.CHATS,
        WalkthroughStep.AGENTS,
        WalkthroughStep.A2A,
        WalkthroughStep.SETTINGS
    )
    val currentIndex = stepSequence.indexOf(currentStep)

    fun nextStep() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (currentIndex < stepSequence.size - 1) {
            currentStep = stepSequence[currentIndex + 1]
        } else {
            onFinish()
        }
    }
    
    fun skip() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onFinish()
    }

    // Determine target rect based on current step
    val targetRect = when (currentStep) {
        WalkthroughStep.WELCOME -> null
        WalkthroughStep.CHATS -> walkthroughState.highlightedElements["Chats"]
        WalkthroughStep.AGENTS -> walkthroughState.highlightedElements["Agents"]
        WalkthroughStep.A2A -> walkthroughState.highlightedElements["A2A"]
        WalkthroughStep.SETTINGS -> walkthroughState.highlightedElements["Settings"]
    }

    // Animate spotlight parameters
    val density = LocalDensity.current
    val targetCenter = targetRect?.center ?: Offset.Zero
    val targetRadius = targetRect?.let { max(it.width, it.height) / 1.5f + with(density) { 8.dp.toPx() } } ?: 0f

    val animatedCenterX by animateFloatAsState(
        targetValue = targetCenter.x,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "centerX"
    )
    val animatedCenterY by animateFloatAsState(
        targetValue = targetCenter.y,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "centerY"
    )
    val animatedRadius by animateFloatAsState(
        targetValue = targetRadius,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "radius"
    )

    // Pulsing effect for the spotlight border
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Catch clicks so they don't fall through to the app underneath
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { nextStep() }
    ) {
        // Overlay and Spotlight Cutout
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f) // Required for BlendMode.Clear
        ) {
            // Draw dim overlay
            drawRect(color = Color.Black.copy(alpha = 0.85f))

            if (currentStep != WalkthroughStep.WELCOME && targetRect != null) {
                // Cut out spotlight
                drawCircle(
                    color = Color.Transparent,
                    radius = animatedRadius,
                    center = Offset(animatedCenterX, animatedCenterY),
                    blendMode = BlendMode.Clear
                )
                
                // Draw soft glowing border around the cutout
                drawCircle(
                    color = Color.White.copy(alpha = pulseAlpha),
                    radius = animatedRadius + 4.dp.toPx(),
                    center = Offset(animatedCenterX, animatedCenterY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Floating Glassmorphic Tooltip / Content
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "WalkthroughContent"
            ) { step ->
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .padding(32.dp)
                        .graphicsLayer {
                            // Basic glassmorphism effect (if supported by Compose version, otherwise just translucent)
                            shadowElevation = 16.dp.toPx()
                            shape = RoundedCornerShape(24.dp)
                            clip = true
                        }
                        .background(Color(0x33FFFFFF))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (step) {
                                WalkthroughStep.WELCOME -> "Welcome to BATON"
                                WalkthroughStep.CHATS -> "Secure E2E Chats"
                                WalkthroughStep.AGENTS -> "Intelligent Agents"
                                WalkthroughStep.A2A -> "A2A Handshake"
                                WalkthroughStep.SETTINGS -> "Full Control"
                            },
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when (step) {
                                WalkthroughStep.WELCOME -> "A new era of secure, self-sovereign AI. Let's take a quick tour."
                                WalkthroughStep.CHATS -> "Communicate securely using military-grade Double Ratchet encryption. Your keys never leave your device."
                                WalkthroughStep.AGENTS -> "Connect directly to open-source AI models hosted on your own infrastructure or local machine."
                                WalkthroughStep.A2A -> "Decentralized Agent-to-Agent protocol. Allow your AI to securely communicate with others."
                                WalkthroughStep.SETTINGS -> "Export your cryptographic identity, manage memory retention, and customize your experience."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFFCFD8DC),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { skip() }) {
                                Text("Skip", color = Color.White.copy(alpha = 0.7f))
                            }
                            Button(
                                onClick = { nextStep() },
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(if (step == WalkthroughStep.SETTINGS) "Get Started" else "Next")
                            }
                        }
                    }
                }
            }
        }
    }
}
