package com.ekam.baton.feature.agents.a2a.avatar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * All live animation values needed by [HumanoidAvatarRenderer].
 *
 * Every value is produced by [rememberInfiniteTransition] so they run on the
 * Compose animation engine (60fps, GPU-driven). Values are pure floats — the
 * renderer uses sin/cos to convert them into positions.
 */
data class AvatarAnimValues(
    val breathScale: Float,      // 0.97 → 1.03  idle breathing
    val walkCycle: Float,        // 0 → 2π  arm/leg swing phase (radians)
    val orbitAngle: Float,       // 0 → 360  THINKING dot orbit angle (degrees)
    val particlePhase: Float,    // 0 → 1   ACTIVE rising particle progress
    val glowPulse: Float,        // 0 → 1   ERROR/WAITING glow alpha pulse
    val radarAngle: Float,       // 0 → 360  extra rotation for props
)

@Composable
fun rememberAvatarAnimValues(): AvatarAnimValues {
    val transition = rememberInfiniteTransition(label = "avatar")

    val breathScale by transition.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "breath"
    )

    val walkCycle by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "walk"
    )

    val orbitAngle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "orbit"
    )

    val particlePhase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "particle"
    )

    val glowPulse by transition.animateFloat(
        initialValue = 0.2f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    val radarAngle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "radar"
    )

    return AvatarAnimValues(
        breathScale  = breathScale,
        walkCycle    = walkCycle,
        orbitAngle   = orbitAngle,
        particlePhase = particlePhase,
        glowPulse    = glowPulse,
        radarAngle   = radarAngle
    )
}
