package com.ekam.baton.feature.agents.a2a.world

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ekam.baton.core.data.model.AgentRole

/**
 * Renders the base floor tiles and glowing grid for a room.
 */
fun DrawScope.drawRoomBase(
    origin: Offset,
    role: AgentRole,
    gridSizeX: Int = 8,
    gridSizeY: Int = 8
) {
    val floorColor = getRoomFloorColor(role)
    val gridColor = getRoomGridColor(role)

    // Base large diamond representing the whole room floor
    val hw = IsometricEngine.TILE_W / 2f
    val hh = IsometricEngine.TILE_H / 2f
    val totalWidthX = gridSizeX * hw
    val totalWidthY = gridSizeY * hw
    val totalHeightX = gridSizeX * hh
    val totalHeightY = gridSizeY * hh

    val floorPath = Path().apply {
        moveTo(origin.x, origin.y) // Top corner (0,0)
        lineTo(origin.x + totalWidthX, origin.y + totalHeightX) // Right corner
        lineTo(origin.x + totalWidthX - totalWidthY, origin.y + totalHeightX + totalHeightY) // Bottom corner
        lineTo(origin.x - totalWidthY, origin.y + totalHeightY) // Left corner
        close()
    }

    drawPath(
        path = floorPath,
        color = floorColor
    )

    // Draw Isometric Grid Lines
    for (x in 0..gridSizeX) {
        val start = IsometricEngine.worldToScreen(x.toFloat(), 0f, 0f, origin)
        val end = IsometricEngine.worldToScreen(x.toFloat(), gridSizeY.toFloat(), 0f, origin)
        drawLine(
            color = gridColor,
            start = start,
            end = end,
            strokeWidth = 1f
        )
    }
    
    for (y in 0..gridSizeY) {
        val start = IsometricEngine.worldToScreen(0f, y.toFloat(), 0f, origin)
        val end = IsometricEngine.worldToScreen(gridSizeX.toFloat(), y.toFloat(), 0f, origin)
        drawLine(
            color = gridColor,
            start = start,
            end = end,
            strokeWidth = 1f
        )
    }
}

private fun getRoomFloorColor(role: AgentRole): Color {
    return when (role) {
        AgentRole.CODER -> Color(0xFF141A14)
        AgentRole.RESEARCHER -> Color(0xFF14141A)
        AgentRole.SECURITY -> Color(0xFF1A1414)
        AgentRole.CREATIVE -> Color(0xFF1A141A)
        AgentRole.MEMORY -> Color(0xFF141A1A)
        AgentRole.ANALYST -> Color(0xFF1A1A14)
        AgentRole.COORDINATOR -> Color(0xFF1A1A1A)
    }
}

private fun getRoomGridColor(role: AgentRole): Color {
    val base = when (role) {
        AgentRole.CODER -> Color(0xFF00FF88)
        AgentRole.RESEARCHER -> Color(0xFF00BFFF)
        AgentRole.SECURITY -> Color(0xFFFF3366)
        AgentRole.CREATIVE -> Color(0xFFFF00FF)
        AgentRole.MEMORY -> Color(0xFF00FFFF)
        AgentRole.ANALYST -> Color(0xFFFFD700)
        AgentRole.COORDINATOR -> Color(0xFFFFFFFF)
    }
    return base.copy(alpha = 0.2f)
}
