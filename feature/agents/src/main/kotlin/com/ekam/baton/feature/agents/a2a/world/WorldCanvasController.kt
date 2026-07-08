package com.ekam.baton.feature.agents.a2a.world

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/** Min/max zoom levels for the world canvas */
private const val ZOOM_MIN = 0.35f
private const val ZOOM_MAX = 2.0f

/**
 * Holds pan/zoom state for the isometric world canvas.
 *
 * [panOffset] is the translation applied to the canvas so the user can scroll.
 * [zoom] is the uniform scale factor (1f = 100%).
 *
 * The controller exposes a [modifier] that installs touch gesture detection so
 * the Canvas composable only needs to call `drawIntoCanvas` with the transform applied.
 */
class WorldCanvasController(initialPan: Offset = Offset.Zero, initialZoom: Float = 0.7f) {
    var panOffset by mutableStateOf(initialPan)
        private set

    var zoom by mutableFloatStateOf(initialZoom.coerceIn(ZOOM_MIN, ZOOM_MAX))
        private set

    fun onPan(delta: Offset) {
        panOffset += delta
    }

    fun onZoom(scaleFactor: Float, centroid: Offset) {
        val newZoom = (zoom * scaleFactor).coerceIn(ZOOM_MIN, ZOOM_MAX)
        // Zoom toward the centroid (pinch focal point)
        val scaleDelta = newZoom / zoom
        panOffset = centroid + (panOffset - centroid) * scaleDelta
        zoom = newZoom
    }

    /** The effective world origin (canvas center + pan) used by [IsometricEngine.worldToScreen] */
    fun worldOrigin(canvasWidth: Float, canvasHeight: Float): Offset =
        Offset(canvasWidth / 2f + panOffset.x, canvasHeight / 3f + panOffset.y)

    /** Modifier that wires two-finger pan + pinch-to-zoom gestures */
    val gestureModifier: Modifier
        get() = Modifier.pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                onZoom(zoom, centroid)
                onPan(pan)
            }
        }
}

@Composable
fun rememberWorldCanvasController(): WorldCanvasController =
    remember { WorldCanvasController() }
