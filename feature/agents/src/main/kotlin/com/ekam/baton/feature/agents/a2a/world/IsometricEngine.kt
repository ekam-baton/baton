package com.ekam.baton.feature.agents.a2a.world

import androidx.compose.ui.geometry.Offset
import kotlin.math.floor

/**
 * Isometric 2D projection engine using the industry-standard 2:1 tile ratio.
 *
 * World space: 3D grid (gx, gy, gz) where gz=0 is ground level.
 * Screen space: 2D pixel canvas anchored to an [origin] point.
 *
 * The 2:1 ratio (TILE_W:TILE_H = 2:1) is the proven pixel-art isometric standard
 * (Stardew Valley, SimCity, Habbo Hotel) — mathematically simpler than true 30°
 * trigonometry and perfectly aligned to integer pixel grids.
 *
 * Depth sort key: gx + gy + gz — lower = further from camera = draw first.
 */
object IsometricEngine {

    /** Full tile width in pixels at 1x scale. */
    const val TILE_W = 96f

    /** Full tile height in pixels at 1x scale (= TILE_W / 2 for 2:1 ratio). */
    const val TILE_H = 48f

    // Half-tile shortcuts used in every projection call
    private const val HW = TILE_W / 2f
    private const val HH = TILE_H / 2f

    /**
     * Convert 3D world grid position to 2D screen pixel offset.
     *
     * @param gx  world grid X
     * @param gy  world grid Y
     * @param gz  world grid Z (height, 0 = ground)
     * @param origin  screen anchor point (center of the world canvas)
     */
    fun worldToScreen(gx: Float, gy: Float, gz: Float = 0f, origin: Offset): Offset = Offset(
        x = origin.x + (gx - gy) * HW,
        y = origin.y + (gx + gy) * HH - gz * TILE_H
    )

    /**
     * Convert a screen tap / pixel position back to approximate world tile coordinates.
     * Result must be floor()-rounded; watch for off-by-one at tile edges.
     *
     * @return (gridX, gridY) — both integers via floor
     */
    fun screenToWorld(sx: Float, sy: Float, origin: Offset): Pair<Int, Int> {
        val dx = sx - origin.x
        val dy = sy - origin.y
        val gxF = (dx / HW + dy / HH) / 2f
        val gyF = (dy / HH - dx / HW) / 2f
        return Pair(floor(gxF).toInt(), floor(gyF).toInt())
    }

    /**
     * Depth sort key. Objects with lower values are drawn first (painter's algorithm).
     * gz is scaled by 2 so tall objects (gz > 0) sort correctly above floor tiles.
     */
    fun depthKey(gx: Float, gy: Float, gz: Float = 0f): Float = gx + gy + gz * 0.5f

    /**
     * Viewport cull check. Returns true if the screen position might be visible.
     * Adds a 2-tile margin to handle large sprites that overlap tile boundaries.
     */
    fun isVisible(screenPos: Offset, canvasWidth: Float, canvasHeight: Float): Boolean =
        screenPos.x > -TILE_W * 2 && screenPos.x < canvasWidth + TILE_W * 2 &&
        screenPos.y > -TILE_H * 2 && screenPos.y < canvasHeight + TILE_H * 2
}
