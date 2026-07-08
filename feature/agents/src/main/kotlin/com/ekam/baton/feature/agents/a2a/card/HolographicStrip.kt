package com.ekam.baton.feature.agents.a2a.card

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A stunning, premium holographic strip running down the side of the ID card.
 * Uses a continuously shifting rainbow/iridescent linear gradient with a 
 * metallic overlay.
 */
@Composable
fun HolographicStrip(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "hologram")
    
    // Animate the gradient offset
    val gradientShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_shift"
    )
    
    val shimmerShift by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing, delayMillis = 1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_shift"
    )

    // Iridescent colors representing a premium hologram
    val hologramColors = listOf(
        Color(0xFFE0C3FC), // Light purple
        Color(0xFF8EC5FC), // Light blue
        Color(0xFFE0C3FC),
        Color(0xFFFFD1FF), // Pinkish
        Color(0xFF8EC5FC),
    )

    val metallicShimmer = listOf(
        Color.Transparent,
        Color.White.copy(alpha = 0.6f),
        Color.Transparent
    )

    Canvas(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
    ) {
        // Base iridescent gradient (shifts up continuously)
        drawRect(
            brush = Brush.linearGradient(
                colors = hologramColors,
                start = Offset(0f, gradientShift - 1000f),
                end = Offset(0f, gradientShift)
            )
        )
        
        // Secondary shifting gradient for complex lighting (shifts down)
        drawRect(
            brush = Brush.linearGradient(
                colors = hologramColors.reversed(),
                start = Offset(size.width, -gradientShift),
                end = Offset(0f, 1000f - gradientShift)
            ),
            blendMode = BlendMode.Overlay
        )
        
        // Fast metallic shimmer highlight passing over
        drawRect(
            brush = Brush.linearGradient(
                colors = metallicShimmer,
                start = Offset(0f, shimmerShift),
                end = Offset(size.width, shimmerShift + 200f)
            ),
            blendMode = BlendMode.ColorDodge
        )
        
        // Subtle vertical pattern lines
        for (i in 1..4) {
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(size.width * (i / 5f), 0f),
                end = Offset(size.width * (i / 5f), size.height),
                strokeWidth = 1f
            )
        }
    }
}
