package com.ekam.baton.feature.agents.a2a.card

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.ekam.baton.core.data.model.AgentRole

/**
 * An interactive 3D flipping identity card for an Agent.
 * Tap to flip between [CardFront] and [CardBack].
 */
@Composable
fun AgentIdentityCard(
    agentId: String,
    agentName: String,
    role: AgentRole,
    modifier: Modifier = Modifier,
    initialFlipped: Boolean = false
) {
    var flipped by remember { mutableStateOf(initialFlipped) }
    
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "card_flip"
    )
    
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f) // Standard ID card ratio
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                flipped = !flipped
            }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
    ) {
        if (rotation <= 90f) {
            CardFront(
                agentId = agentId,
                agentName = agentName,
                role = role,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            CardBack(
                agentId = agentId,
                role = role,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Mirror back so it's not reversed when flipped
                        rotationY = 180f
                    }
            )
        }
    }
}
