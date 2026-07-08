package com.ekam.baton.feature.agents.a2a.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random
import kotlin.math.sin

/**
 * Draws the background for the isometric world.
 * - Deep dark terminal void background
 * - Subtle twinkling starfield / dust particles
 * - Faint grid lines at the world origin
 */
fun DrawScope.drawWorldBackground(
    canvasWidth: Float,
    canvasHeight: Float,
    animTime: Float,
    panOffset: Offset
) {
    // 1. Deep Void Background
    drawRect(color = Color(0xFF0D0D14)) // Very dark terminal blue-black

    // 2. Parallax Starfield / Dust
    // Use a fixed seed so stars don't jump, but pan offset creates parallax
    val rnd = Random(42)
    val starCount = 150
    val parallaxFactor = 0.3f
    
    val baseColor = Color.White.copy(alpha = 0.3f)

    for (i in 0 until starCount) {
        // Distribute pseudo-randomly over a large virtual space
        val virtualX = rnd.nextFloat() * canvasWidth * 3f - canvasWidth
        val virtualY = rnd.nextFloat() * canvasHeight * 3f - canvasHeight
        
        // Apply parallax offset
        var screenX = virtualX + panOffset.x * parallaxFactor
        var screenY = virtualY + panOffset.y * parallaxFactor
        
        // Wrap around logic so we never run out of stars when panning far
        screenX = (screenX % canvasWidth + canvasWidth) % canvasWidth
        screenY = (screenY % canvasHeight + canvasHeight) % canvasHeight
        
        // Size and twinkle
        val size = rnd.nextFloat() * 3f + 1f
        val phase = rnd.nextFloat() * Math.PI * 2
        val twinkle = 0.5f + 0.5f * sin(animTime * 2f + phase).toFloat()
        
        drawCircle(
            color = baseColor.copy(alpha = baseColor.alpha * twinkle),
            radius = size,
            center = Offset(screenX, screenY)
        )
    }
}
