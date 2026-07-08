package com.ekam.baton.feature.agents.a2a.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ekam.baton.core.data.model.AgentRole
import com.ekam.baton.core.data.model.WorldRoom
import com.ekam.baton.core.data.model.WorldRoomProp
import com.ekam.baton.feature.agents.a2a.avatar.AvatarActivityState
import com.ekam.baton.feature.agents.a2a.avatar.AvatarAnimValues
import com.ekam.baton.feature.agents.a2a.avatar.AvatarComponents
import com.ekam.baton.feature.agents.a2a.avatar.drawHumanoid

/**
 * Orchestrates the rendering of the entire isometric world.
 * Follows the Painter's Algorithm: sorts all items by depth and draws back-to-front.
 */
fun DrawScope.drawWorld(
    rooms: List<WorldRoom>,
    props: Map<String, List<WorldRoomProp>>, // RoomID to Props
    avatars: Map<String, AvatarData>, // RoomID to AvatarData
    canvasController: WorldCanvasController,
    animTime: Float,
    animValues: AvatarAnimValues
) {
    val origin = canvasController.worldOrigin(size.width, size.height)
    val zoom = canvasController.zoom

    // 1. Draw Background
    drawWorldBackground(size.width, size.height, animTime, canvasController.panOffset)

    // 2. Prepare Render Items for Depth Sorting
    val renderItems = mutableListOf<RenderItem>()

    // Add Rooms (Floors are at z = -0.1 to draw before props)
    rooms.forEach { room ->
        val originOffset = IsometricEngine.worldToScreen(room.worldX.toFloat(), room.worldY.toFloat(), 0f, origin)
        renderItems.add(
            RenderItem(
                depthKey = IsometricEngine.depthKey(room.worldX.toFloat(), room.worldY.toFloat(), -0.1f),
                draw = {
                    drawRoomBase(originOffset, room.role, 8, 8)
                }
            )
        )

        // Add Props for this room
        props[room.id]?.forEach { prop ->
            val propGx = room.worldX + prop.gridX
            val propGy = room.worldY + prop.gridY
            val propOffset = IsometricEngine.worldToScreen(propGx, propGy, prop.gridZ, origin)
            
            renderItems.add(
                RenderItem(
                    depthKey = IsometricEngine.depthKey(propGx, propGy, prop.gridZ),
                    draw = {
                        WorldAssets.draw(
                            propType = prop.propType,
                            origin = propOffset,
                            u = IsometricEngine.TILE_H * 0.2f * zoom,
                            color = getRoleColor(room.role),
                            anim = (animTime + prop.animSeed / 1000f) % 1f,
                            animSeed = prop.animSeed
                        )
                    }
                )
            )
        }

        // Add Avatar for this room (Avatar sits in the center of the room)
        avatars[room.id]?.let { avatarData ->
            val avatarGx = room.worldX + 4f
            val avatarGy = room.worldY + 4f
            val avatarOffset = IsometricEngine.worldToScreen(avatarGx, avatarGy, 0f, origin)
            
            renderItems.add(
                RenderItem(
                    depthKey = IsometricEngine.depthKey(avatarGx, avatarGy, 0f),
                    draw = {
                        drawHumanoid(
                            footCenter = avatarOffset,
                            components = avatarData.components,
                            state = avatarData.state,
                            anim = animValues,
                            isoScale = zoom
                        )
                    }
                )
            )
        }
    }

    // 3. Sort by depth (lowest depthKey first)
    renderItems.sortBy { it.depthKey }

    // 4. Draw all items back-to-front
    renderItems.forEach { it.draw() }
}

data class AvatarData(
    val components: AvatarComponents,
    val state: AvatarActivityState
)

private class RenderItem(
    val depthKey: Float,
    val draw: () -> Unit
)

private fun getRoleColor(role: AgentRole): Color {
    return when (role) {
        AgentRole.CODER -> Color(0xFF00FF88)
        AgentRole.RESEARCHER -> Color(0xFF00BFFF)
        AgentRole.SECURITY -> Color(0xFFFF3366)
        AgentRole.CREATIVE -> Color(0xFFFF00FF)
        AgentRole.MEMORY -> Color(0xFF00FFFF)
        AgentRole.ANALYST -> Color(0xFFFFD700)
        AgentRole.COORDINATOR -> Color(0xFFFFFFFF)
    }
}
