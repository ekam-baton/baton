package com.ekam.baton.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Applies a premium glassmorphism effect to a component.
 * 
 * Includes a translucent background and a subtle light border.
 */
fun Modifier.glassCard(
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = Color(0xFF1A2235).copy(alpha = 0.6f),
    borderColor: Color = Color.White.copy(alpha = 0.15f)
): Modifier = composed {
    this
        .background(color = backgroundColor, shape = shape)
        .border(width = 1.dp, color = borderColor, shape = shape)
}
