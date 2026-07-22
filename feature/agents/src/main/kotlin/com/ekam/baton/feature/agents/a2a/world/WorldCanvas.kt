package com.ekam.baton.feature.agents.a2a.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.ekam.baton.core.data.model.WorldRoom
import com.ekam.baton.core.data.model.WorldRoomProp
import com.ekam.baton.core.data.repository.WorldRoomRepository
import com.ekam.baton.feature.agents.a2a.avatar.AvatarActivityState
import com.ekam.baton.feature.agents.a2a.avatar.buildAvatarComponents
import com.ekam.baton.feature.agents.a2a.avatar.rememberAvatarAnimValues

/**
 * The main entry point for the 3D Isometric World in Baton.
 * Connects the Compose gesture system, data repository, and animation loop
 * to the pure-DrawScope [drawWorld] rendering pipeline.
 */
@Composable
fun WorldCanvas(
    repository: WorldRoomRepository,
    modifier: Modifier = Modifier
) {
    val rooms by repository.observeAllRooms().collectAsState(initial = emptyList())
    
    // Resolve props for each room
    @android.annotation.SuppressLint("ProduceStateDoesNotAssignValue")
    val propsMap by produceState<Map<String, List<WorldRoomProp>>>(initialValue = emptyMap(), rooms) {
        value = rooms.associate { room ->
            room.id to repository.getPropsForRoom(room.id)
        }
    }

    // Deterministically resolve AvatarComponents for each room (so they stay consistent)
    val avatarsMap = rooms.associate { room ->
        val components = buildAvatarComponents(room.agentId, room.colorHex, room.role)
        // Assume IDLE state by default for the world view; can be dynamic later
        val state = AvatarActivityState.IDLE
        room.id to AvatarData(components, state)
    }

    val canvasController = rememberWorldCanvasController()
    val animValues = rememberAvatarAnimValues()
    
    // animTime is required for the WorldRenderer generic animations (like stars, floating props)
    // We can extract an effective animTime from the continuous particlePhase or radarAngle
    val animTime = (animValues.radarAngle / 360f) * 100f 

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D14)) // Fallback dark background
            .then(canvasController.gestureModifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawWorld(
                rooms = rooms,
                props = propsMap,
                avatars = avatarsMap,
                canvasController = canvasController,
                animTime = animTime,
                animValues = animValues
            )
        }
    }
}
