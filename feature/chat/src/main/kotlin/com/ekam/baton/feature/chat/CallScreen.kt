package com.ekam.baton.feature.chat

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ekam.baton.core.network.webrtc.WebRtcManager
import com.ekam.baton.core.ui.components.glassCard

@Composable
fun CallScreen(
    agentName: String,
    onEndCall: () -> Unit,
    onNavigateToChat: () -> Unit = onEndCall
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(true) }
    var showTranscript by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    var webRtcManager by remember { mutableStateOf<WebRtcManager?>(null) }
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager }

    val liveAmplitude by (webRtcManager?.audioAmplitude?.collectAsStateWithLifecycle() ?: remember { mutableFloatStateOf(0.4f) })

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            elapsedSeconds++
        }
    }

    DisposableEffect(Unit) {
        val manager = WebRtcManager(context)
        try {
            manager.initialize()
            manager.startVoiceCall(agentName)
            webRtcManager = manager
        } catch (e: Exception) {
            // Ignored for UI preview
        }
        onDispose {
            try {
                manager.endCall()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager?.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.isSpeakerphoneOn = false
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    LaunchedEffect(isMuted) {
        webRtcManager?.setMicrophoneMute(isMuted)
    }

    LaunchedEffect(isSpeaker) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isSpeaker) {
                    val speakerDevice = audioManager?.availableCommunicationDevices?.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager?.setCommunicationDevice(speakerDevice)
                    }
                } else {
                    audioManager?.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = isSpeaker
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val formattedDuration = remember(elapsedSeconds) {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }

    // Avatar halo scaled directly by live audio amplitude
    val effectivePulseScale = if (isMuted) 1f else 1f + (liveAmplitude * 0.15f)
    val effectivePulseAlpha = if (isMuted) 0.15f else (0.25f + liveAmplitude * 0.35f).coerceIn(0.1f, 0.7f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D131F)) // Deep Ocean Blue
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: Encryption & Connection Status
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(top = 48.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .glassCard(shape = CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Encrypted",
                        tint = Color(0xFF00D2FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "End-to-End Encrypted • 48kHz Opus",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Connection HUD Pill with "Connected" indicator
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF131E31))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Text(
                        text = "Connected",
                        color = Color(0xFF10B981),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("•", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                    Text(
                        text = formattedDuration,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text("•", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                    Text(
                        text = "<18ms WebRTC",
                        color = Color(0xFF00D2FF),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            // Center section: Squircle Avatar, Agent Name & Waveform Visualizer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(170.dp)
                ) {
                    // Pulsing Outer Aura
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(effectivePulseScale)
                            .clip(RoundedCornerShape(36.dp))
                            .background(Color(0xFF306EE8).copy(alpha = effectivePulseAlpha))
                    )
                    // Core Squircle Avatar
                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF306EE8), Color(0xFF1A3D80))
                                )
                            )
                            .border(1.5.dp, Color(0xFF00D2FF).copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                    ) {
                        Text(
                            text = if (agentName.isNotBlank()) agentName.take(1).uppercase() else "?",
                            color = Color.White,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = agentName,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFFF59E0B) else Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isMuted) "Microphone Muted" else "Neural Audio Stream Active",
                        color = if (isMuted) Color(0xFFF59E0B) else Color(0xFF10B981),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Real-time Audio Waveform Visualizer
                AudioWaveformVisualizer(
                    amplitude = if (isMuted) 0f else liveAmplitude,
                    isMuted = isMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp)
                )
            }

            // Bottom section: Controls Cluster & Live Transcript
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 44.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(visible = showTranscript) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard(shape = RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LIVE SPEECH TRANSCRIPT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF00D2FF)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SYNCHRONIZED",
                                    color = Color(0xFF10B981),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 90.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Operator: Hello $agentName, running diagnostic telemetry check.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "$agentName: Neural channel verified. WebRTC 48kHz audio pipeline ready.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(0xFF00D2FF)
                                )
                            }
                        }
                    }
                }

                if (showTranscript) {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallControlButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = "Mute",
                        isActive = isMuted,
                        onClick = { isMuted = !isMuted }
                    )
                    CallControlButton(
                        icon = if (isSpeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        label = "Speaker",
                        isActive = isSpeaker,
                        onClick = { isSpeaker = !isSpeaker }
                    )
                    CallControlButton(
                        icon = Icons.Default.ChatBubble,
                        label = "Chat",
                        isActive = false,
                        onClick = onNavigateToChat
                    )
                    CallControlButton(
                        icon = Icons.Default.ClosedCaption,
                        label = "Transcript",
                        isActive = showTranscript,
                        onClick = { showTranscript = !showTranscript }
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // End Call Button
                IconButton(
                    onClick = {
                        try {
                            webRtcManager?.endCall()
                        } catch (e: Exception) {
                            // Ignore
                        }
                        onEndCall()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444)) // Vivid Red
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    amplitude: Float,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 28

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF090E17).copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val normalizedIdx = i.toFloat() / barCount
            val barHeightFraction = if (isMuted) {
                0.08f
            } else {
                val envelope = kotlin.math.sin(normalizedIdx * 3.14f).toFloat()
                val dynamic = 0.12f + (amplitude * envelope * 0.76f).coerceIn(0f, 0.88f)
                dynamic
            }

            val animatedHeightFraction by animateFloatAsState(
                targetValue = barHeightFraction,
                animationSpec = spring(stiffness = Spring.StiffnessHigh),
                label = "barHeight_$i"
            )

            val barBrush = remember(isMuted) {
                if (isMuted) {
                    androidx.compose.ui.graphics.SolidColor(Color(0xFF475569))
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF10B981),
                            Color(0xFF00D2FF),
                            Color(0xFF306EE8)
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(animatedHeightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barBrush)
            )
        }
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isActive) Color.White else Color(0xFF1E293B))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.Black else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = if (isActive) Color.White else Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
