package com.ekam.baton.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Applies a premium shimmer effect for skeleton loading states.
 */
fun Modifier.shimmerEffect(): Modifier = background(
    brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2A3448).copy(alpha = 0.5f),
            Color(0xFF333F55).copy(alpha = 0.5f),
            Color(0xFF2A3448).copy(alpha = 0.5f),
        )
    )
)
