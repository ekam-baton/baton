package com.ekam.baton.feature.agents.a2a.world

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Baton World Asset Library
 *
 * 50+ isometric room prop DrawScope extension functions.
 * Every prop takes:
 *   @param origin   screen-space anchor (center base of prop)
 *   @param u        base unit size in pixels (ties to world zoom)
 *   @param color    primary accent color
 *   @param anim     0→1 animation phase (looping) — prop animates based on this
 *   @param animSeed per-prop offset so props don't all animate in lockstep
 *
 * Dispatch key: `WorldAssets.draw(propType, ...)` routes to the correct function.
 */
object WorldAssets {

    fun draw(
        propType: String,
        origin: Offset,
        u: Float,
        color: Color,
        anim: Float,
        animSeed: Int = 0
    ) {
        // All props are called through the renderer — routing happens there.
        // This object is the namespace; actual dispatch is in WorldRenderer.
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 1 — CODER ROOM ASSETS
    // ════════════════════════════════════════════════════════════════════════

    /** L-shaped desk with two monitors showing green code. */
    fun DrawScope.drawDualMonitorDesk(origin: Offset, u: Float, color: Color, anim: Float) {
        val desk = darken(color, 0.4f)
        val surface = darken(color, 0.3f)

        // Desk surface (isometric top face)
        drawIsoFlatSurface(origin, u * 6f, u * 4f, u * 0.8f, surface, darken(color, 0.5f), darken(color, 0.55f))

        // Left monitor
        val mon1 = Offset(origin.x - u * 1.5f, origin.y - u * 4f)
        drawIsoMonitor(mon1, u, Color(0xFF1a1a2e), Color(0xFF00FF88), anim)

        // Right monitor
        val mon2 = Offset(origin.x + u * 1.5f, origin.y - u * 4f)
        drawIsoMonitor(mon2, u, Color(0xFF1a1a2e), color, anim + 0.3f)

        // Monitor stands
        drawRect(desk, Offset(mon1.x - u * 0.2f, mon1.y + u * 2.5f), Size(u * 0.4f, u))
        drawRect(desk, Offset(mon2.x - u * 0.2f, mon2.y + u * 2.5f), Size(u * 0.4f, u))
    }

    private fun DrawScope.drawIsoMonitor(center: Offset, u: Float, bgColor: Color, glowColor: Color, anim: Float) {
        // Screen bezel
        drawRoundRect(Color(0xFF2a2a3e), Offset(center.x - u * 2f, center.y - u * 1.5f), Size(u * 4f, u * 3f), CornerRadius(u * 0.3f))
        // Screen glow background
        drawRoundRect(bgColor, Offset(center.x - u * 1.7f, center.y - u * 1.2f), Size(u * 3.4f, u * 2.4f), CornerRadius(u * 0.2f))
        // Animated code lines
        repeat(4) { i ->
            val lineY = center.y - u + i * u * 0.55f
            val lineWidth = u * (1.5f + sin((anim * PI * 2 + i * 0.8f).toFloat()) * 0.8f).toFloat()
            drawLine(glowColor.copy(alpha = 0.7f + sin((anim * PI * 2 + i).toFloat()) * 0.3f),
                Offset(center.x - u * 1.5f, lineY), Offset(center.x - u * 1.5f + lineWidth, lineY),
                strokeWidth = u * 0.15f)
        }
        // Blinking cursor
        if ((anim * 4).toInt() % 2 == 0) {
            drawRect(glowColor, Offset(center.x - u * 1.5f, center.y + u * 0.8f), Size(u * 0.2f, u * 0.4f))
        }
    }

    /** Mechanical keyboard with RGB underglow */
    fun DrawScope.drawMechKeyboard(origin: Offset, u: Float, color: Color, anim: Float) {
        val bodyColor = Color(0xFF1C1C2E)
        drawRoundRect(bodyColor, Offset(origin.x - u * 2.5f, origin.y - u * 0.8f), Size(u * 5f, u * 1.6f), CornerRadius(u * 0.3f))
        // RGB underglow strip
        val glowColor = color.copy(alpha = 0.5f + sin((anim * PI * 2).toFloat()) * 0.3f)
        drawRoundRect(glowColor, Offset(origin.x - u * 2.5f, origin.y + u * 0.5f), Size(u * 5f, u * 0.3f), CornerRadius(u * 0.15f))
        // Key grid (simplified)
        repeat(5) { col ->
            repeat(2) { row ->
                val kx = origin.x - u * 2f + col * u * 0.9f
                val ky = origin.y - u * 0.5f + row * u * 0.55f
                drawRoundRect(Color(0xFF2A2A3E), Offset(kx, ky), Size(u * 0.65f, u * 0.4f), CornerRadius(u * 0.1f))
            }
        }
    }

    /** Server tower with blinking LED indicators */
    fun DrawScope.drawServerTower(origin: Offset, u: Float, color: Color, anim: Float) {
        val body = Color(0xFF1A1A2A)
        val face = Color(0xFF1F1F30)
        // Tower body (isometric box)
        drawIsoBox(origin, u * 2f, u * 4f, u * 3f, face, darken(face, 0.15f), darken(face, 0.25f))
        // Rack slots
        repeat(5) { i ->
            val slotY = origin.y - u * 4.5f + i * u * 0.7f
            drawRoundRect(Color(0xFF0D0D1A), Offset(origin.x - u * 0.8f, slotY), Size(u * 1.6f, u * 0.5f), CornerRadius(u * 0.1f))
            // Blinking LEDs
            val ledPhase = (anim * 3f + i * 0.4f) % 1f
            drawCircle(if (ledPhase > 0.5f) color else Color(0xFF2A2A3A), u * 0.15f, Offset(origin.x + u * 0.6f, slotY + u * 0.25f))
        }
    }

    /** Floating data cube (wireframe, slowly rotating) */
    fun DrawScope.drawFloatingDataCube(origin: Offset, u: Float, color: Color, anim: Float) {
        val hoverY = origin.y - u * sin((anim * PI * 2).toFloat()) * u * 0.5f
        val rotatedCenter = Offset(origin.x, hoverY - u * 3f)
        val size = u * 2.5f
        val angle = anim * 360f

        withTransform({
            rotate(angle, rotatedCenter)
        }) {
            // Wireframe cube faces
            val edges = listOf(
                Pair(Offset(-size, -size), Offset(size, -size)),
                Pair(Offset(size, -size), Offset(size, size)),
                Pair(Offset(size, size), Offset(-size, size)),
                Pair(Offset(-size, size), Offset(-size, -size)),
            )
            edges.forEach { (a, b) ->
                drawLine(color.copy(alpha = 0.7f),
                    Offset(rotatedCenter.x + a.x, rotatedCenter.y + a.y),
                    Offset(rotatedCenter.x + b.x, rotatedCenter.y + b.y),
                    strokeWidth = u * 0.2f)
            }
        }
        // Glow effect
        drawCircle(color.copy(alpha = 0.15f), u * 3.5f, rotatedCenter)
    }

    /** Coffee cup with rising steam animation */
    fun DrawScope.drawCoffeeCup(origin: Offset, u: Float, color: Color, anim: Float) {
        val cupColor = Color(0xFF4A3728)
        val steamColor = Color(0xFFAAAAAA)
        // Cup body (trapezoid approximation)
        val cupPath = Path().apply {
            moveTo(origin.x - u, origin.y)
            lineTo(origin.x + u, origin.y)
            lineTo(origin.x + u * 0.7f, origin.y - u * 1.8f)
            lineTo(origin.x - u * 0.7f, origin.y - u * 1.8f)
            close()
        }
        drawPath(cupPath, cupColor)
        // Hot liquid top
        drawOval(Color(0xFF6B3A1F), Offset(origin.x - u * 0.7f, origin.y - u * 2f), Size(u * 1.4f, u * 0.4f))
        // Steam wisps (animated upward)
        repeat(3) { i ->
            val phase = (anim + i * 0.33f) % 1f
            val sx = origin.x + (i - 1f) * u * 0.5f + sin((phase * PI * 2 + i).toFloat()) * u * 0.3f
            val sy = origin.y - u * 2f - phase * u * 3f
            drawCircle(steamColor.copy(alpha = (1f - phase) * 0.4f), u * 0.4f, Offset(sx, sy))
        }
        // Handle
        drawArc(cupColor, 0f, 180f, false,
            Offset(origin.x + u * 0.7f, origin.y - u * 1.4f), Size(u * 0.8f, u * 0.8f),
            style = Stroke(u * 0.25f))
    }

    /** Desk succulent / cactus in pot */
    fun DrawScope.drawDeskSucculent(origin: Offset, u: Float, color: Color, anim: Float) {
        val potColor = Color(0xFFC1440E)
        val plantColor = Color(0xFF4CAF50)
        val breath = 1f + sin((anim * PI * 2).toFloat()) * 0.04f

        // Pot
        drawRoundRect(potColor, Offset(origin.x - u, origin.y - u * 1.5f), Size(u * 2f, u * 1.5f), CornerRadius(u * 0.3f))
        drawOval(darken(potColor, 0.15f), Offset(origin.x - u, origin.y - u * 1.8f), Size(u * 2f, u * 0.6f))

        // Cactus body
        val cacW = u * 0.8f * breath
        drawRoundRect(plantColor, Offset(origin.x - cacW / 2, origin.y - u * 4f), Size(cacW, u * 2.5f), CornerRadius(cacW / 2))
        // Arms
        drawRoundRect(plantColor, Offset(origin.x - u * 1.2f, origin.y - u * 3.5f), Size(u * 0.6f, u * 1.2f), CornerRadius(u * 0.3f))
        drawRoundRect(plantColor, Offset(origin.x + u * 0.6f, origin.y - u * 3.2f), Size(u * 0.6f, u * 1f), CornerRadius(u * 0.3f))
        // Spines
        drawCircle(Color(0xFFFFFFAA).copy(0.4f), u * 0.12f, Offset(origin.x, origin.y - u * 4.5f))
        drawCircle(Color(0xFFFFFFAA).copy(0.4f), u * 0.12f, Offset(origin.x - u * 0.3f, origin.y - u * 3.5f))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 2 — RESEARCHER ROOM ASSETS
    // ════════════════════════════════════════════════════════════════════════

    /** Tall bookshelf with coloured spine books */
    fun DrawScope.drawBookshelf(origin: Offset, u: Float, color: Color, anim: Float) {
        val woodColor = Color(0xFF5C3A1E)
        val shelfW = u * 3f
        val shelfH = u * 5f
        // Frame
        drawIsoBox(origin, shelfW, u * 0.4f, shelfH, woodColor, darken(woodColor, 0.2f), darken(woodColor, 0.3f))
        // Book spines on shelves (3 shelves)
        val bookColors = listOf(color, Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D), Color(0xFF95E1D3))
        repeat(3) { shelf ->
            val shelfY = origin.y - u * 1.5f - shelf * u * 1.4f
            var bx = origin.x - shelfW / 2 + u * 0.2f
            val bookCount = 4 + shelf
            repeat(bookCount) { b ->
                val bW = u * (0.4f + (b % 3) * 0.15f)
                val bH = u * (0.9f + (b % 2) * 0.2f)
                drawRoundRect(bookColors[b % bookColors.size], Offset(bx, shelfY - bH), Size(bW, bH), CornerRadius(u * 0.05f))
                bx += bW + u * 0.05f
            }
        }
    }

    /** Magnifying glass with animated scanning arc */
    fun DrawScope.drawMagnifyingGlass(origin: Offset, u: Float, color: Color, anim: Float) {
        val handleColor = Color(0xFF7B5E40)
        val glassColor = color.copy(alpha = 0.25f)
        val lensR = u * 1.8f
        val scanAngle = anim * 360f

        // Handle
        drawLine(handleColor, Offset(origin.x + lensR * 0.7f, origin.y - lensR * 0.7f),
            Offset(origin.x + lensR * 1.6f, origin.y - lensR * 1.6f), strokeWidth = u * 0.5f, cap = StrokeCap.Round)
        // Lens rim
        drawCircle(color, lensR, origin, style = Stroke(width = u * 0.35f))
        // Glass fill
        drawCircle(glassColor, lensR - u * 0.2f, origin)
        // Animated scan line
        val scanX = origin.x + (lensR - u * 0.3f) * cos(Math.toRadians(scanAngle.toDouble())).toFloat()
        val scanY = origin.y + (lensR - u * 0.3f) * sin(Math.toRadians(scanAngle.toDouble())).toFloat()
        drawLine(color.copy(0.6f), origin, Offset(scanX, scanY), strokeWidth = u * 0.2f)
        drawCircle(color.copy(0.15f), lensR - u * 0.2f, origin) // reflection
    }

    /** Floating documents (paper sheets that drift upward) */
    fun DrawScope.drawFloatingDocuments(origin: Offset, u: Float, color: Color, anim: Float) {
        repeat(3) { i ->
            val phase = (anim + i * 0.33f) % 1f
            val dx = sin((phase * PI * 2 + i).toFloat()) * u * 1.5f
            val dy = origin.y - phase * u * 4f - i * u * 1.5f
            val alpha = if (phase > 0.8f) (1f - phase) * 5f else 1f
            // Paper
            drawRoundRect(Color.White.copy(alpha * 0.85f), Offset(origin.x + dx - u, dy - u), Size(u * 2f, u * 2.5f), CornerRadius(u * 0.15f))
            // Text lines
            repeat(4) { line ->
                drawLine(Color(0xFF888888).copy(alpha * 0.5f),
                    Offset(origin.x + dx - u * 0.7f, dy - u * 0.7f + line * u * 0.4f),
                    Offset(origin.x + dx + u * 0.5f, dy - u * 0.7f + line * u * 0.4f),
                    strokeWidth = u * 0.1f)
            }
        }
    }

    /** Globe with slow rotation */
    fun DrawScope.drawGlobe(origin: Offset, u: Float, color: Color, anim: Float) {
        val r = u * 2f
        val rotY = anim * 360f
        // Stand
        drawLine(Color(0xFF888888), Offset(origin.x, origin.y), Offset(origin.x, origin.y - u), strokeWidth = u * 0.3f)
        // Globe body
        drawCircle(Color(0xFF1A6BAF).copy(0.8f), r, origin)
        // Continents (simplified arcs as land masses)
        val continentColor = Color(0xFF4CAF50).copy(0.7f)
        drawArc(continentColor, rotY + 10f, 80f, true, Offset(origin.x - r * 0.6f, origin.y - r * 0.5f), Size(r * 1.2f, r))
        drawArc(continentColor, rotY + 200f, 60f, true, Offset(origin.x - r * 0.3f, origin.y + r * 0.1f), Size(r, r * 0.8f))
        // Meridian lines
        drawCircle(color.copy(0.3f), r, origin, style = Stroke(width = u * 0.12f))
        drawOval(color.copy(0.2f), Offset(origin.x - r * 0.5f, origin.y - r), Size(r, r * 2f), style = Stroke(width = u * 0.1f))
    }

    /** Tea kettle with steam */
    fun DrawScope.drawTeaKettle(origin: Offset, u: Float, color: Color, anim: Float) {
        val kettleColor = Color(0xFFD4A853)
        val body = Path().apply {
            moveTo(origin.x - u, origin.y)
            quadraticBezierTo(origin.x - u * 1.2f, origin.y - u * 1.5f, origin.x, origin.y - u * 2f)
            quadraticBezierTo(origin.x + u * 1.2f, origin.y - u * 1.5f, origin.x + u, origin.y)
            close()
        }
        drawPath(body, kettleColor)
        // Lid
        drawOval(darken(kettleColor, 0.2f), Offset(origin.x - u * 0.6f, origin.y - u * 2.2f), Size(u * 1.2f, u * 0.4f))
        // Handle
        drawArc(darken(kettleColor, 0.15f), -90f, -180f, false, Offset(origin.x + u * 0.5f, origin.y - u * 1.6f), Size(u, u * 1f), style = Stroke(u * 0.3f))
        // Spout
        drawLine(kettleColor, Offset(origin.x - u, origin.y - u * 1f), Offset(origin.x - u * 1.5f, origin.y - u * 1.8f), strokeWidth = u * 0.5f, cap = StrokeCap.Round)
        // Steam
        val phase = anim % 1f
        drawCircle(Color(0xFFCCCCCC).copy((1f - phase) * 0.4f), u * 0.4f, Offset(origin.x - u * 1.5f, origin.y - u * 2f - phase * u * 2f))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 3 — SECURITY ROOM ASSETS
    // ════════════════════════════════════════════════════════════════════════

    /** Radar screen with rotating sweep arm */
    fun DrawScope.drawRadarScreen(origin: Offset, u: Float, color: Color, anim: Float) {
        val screenColor = Color(0xFF001100)
        val sweepAngle = anim * 360f
        val r = u * 2.8f

        // Screen bezel
        drawCircle(Color(0xFF1A1A1A), r + u * 0.4f, origin)
        drawCircle(screenColor, r, origin)
        // Grid rings
        listOf(0.33f, 0.66f, 1f).forEach { scale ->
            drawCircle(color.copy(0.25f), r * scale, origin, style = Stroke(u * 0.08f))
        }
        // Cross hairs
        drawLine(color.copy(0.2f), Offset(origin.x - r, origin.y), Offset(origin.x + r, origin.y), strokeWidth = u * 0.08f)
        drawLine(color.copy(0.2f), Offset(origin.x, origin.y - r), Offset(origin.x, origin.y + r), strokeWidth = u * 0.08f)
        // Sweep arc (green glow fading)
        drawArc(color.copy(0.5f), sweepAngle - 60f, 60f, true,
            Offset(origin.x - r, origin.y - r), Size(r * 2, r * 2))
        // Sweep arm
        val armX = origin.x + r * cos(Math.toRadians(sweepAngle.toDouble())).toFloat()
        val armY = origin.y + r * sin(Math.toRadians(sweepAngle.toDouble())).toFloat()
        drawLine(color.copy(0.9f), origin, Offset(armX, armY), strokeWidth = u * 0.2f)
        // Blip
        drawCircle(color, u * 0.3f, Offset(origin.x + r * 0.5f, origin.y - r * 0.3f))
    }

    /** Tall server rack with cascade-blinking LEDs */
    fun DrawScope.drawServerRack(origin: Offset, u: Float, color: Color, anim: Float) {
        drawIsoBox(origin, u * 2.5f, u * 1.5f, u * 6f, Color(0xFF1A1A2A), Color(0xFF0D0D18), Color(0xFF0A0A14))
        repeat(8) { slot ->
            val slotY = origin.y - u * 5.5f + slot * u * 0.65f
            drawRoundRect(Color(0xFF0D0D20), Offset(origin.x - u * 1f, slotY), Size(u * 2f, u * 0.5f), CornerRadius(u * 0.08f))
            val ledOn = ((anim * 5f + slot * 0.3f) % 1f) > 0.5f
            drawCircle(if (ledOn) color else color.copy(0.15f), u * 0.15f, Offset(origin.x + u * 0.8f, slotY + u * 0.25f))
        }
    }

    /** Encryption key prop (large glowing key) */
    fun DrawScope.drawEncryptionKey(origin: Offset, u: Float, color: Color, anim: Float) {
        val pulse = 0.5f + sin((anim * PI * 2).toFloat()) * 0.5f
        // Key ring
        drawCircle(color.copy(0.2f + pulse * 0.2f), u * 2f, origin)
        drawCircle(color, u * 1.5f, origin, style = Stroke(u * 0.4f))
        // Key shaft
        drawLine(color, Offset(origin.x + u * 1.5f, origin.y), Offset(origin.x + u * 4f, origin.y), strokeWidth = u * 0.45f, cap = StrokeCap.Round)
        // Teeth
        listOf(2.5f, 3f, 3.5f).forEachIndexed { i, xOff ->
            val toothLen = if (i % 2 == 0) u * 0.6f else u * 0.4f
            drawLine(color, Offset(origin.x + xOff * u, origin.y), Offset(origin.x + xOff * u, origin.y + toothLen), strokeWidth = u * 0.35f, cap = StrokeCap.Round)
        }
        // Glow halo
        drawCircle(color.copy(0.12f * pulse), u * 3f, origin)
    }

    /** Alert beacon — spinning light with flash */
    fun DrawScope.drawAlertBeacon(origin: Offset, u: Float, color: Color, anim: Float) {
        val red = Color(0xFFFF453A)
        val beaconColor = if ((anim * 4).toInt() % 2 == 0) red else red.copy(0.15f)
        drawCircle(Color(0xFF1A1A1A), u * 1.2f, origin)
        drawCircle(beaconColor, u, origin)
        if ((anim * 4).toInt() % 2 == 0) {
            drawCircle(red.copy(0.3f), u * 2.5f, origin) // flash bloom
        }
        // Spinning ring
        val sweepAngle = anim * 360f
        drawArc(red.copy(0.4f), sweepAngle, 120f, false, Offset(origin.x - u * 2f, origin.y - u * 2f), Size(u * 4f, u * 4f), style = Stroke(u * 0.3f))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 4 — CREATIVE ROOM ASSETS
    // ════════════════════════════════════════════════════════════════════════

    /** Drawing tablet with stylus tracing a path */
    fun DrawScope.drawDrawingTablet(origin: Offset, u: Float, color: Color, anim: Float) {
        // Tilted tablet face
        val tabletW = u * 5f
        val tabletH = u * 3.5f
        drawRoundRect(Color(0xFF1A1A2E), Offset(origin.x - tabletW / 2, origin.y - tabletH), Size(tabletW, tabletH), CornerRadius(u * 0.5f))
        // Drawing surface
        drawRoundRect(Color(0xFF2A2A3E), Offset(origin.x - tabletW / 2 + u * 0.3f, origin.y - tabletH + u * 0.3f), Size(tabletW - u * 0.6f, tabletH - u * 0.6f), CornerRadius(u * 0.3f))
        // Progressive artwork (circle being drawn)
        val progress = anim
        drawArc(color.copy(0.8f), -90f, progress * 360f, false,
            Offset(origin.x - u * 1.5f, origin.y - tabletH + u * 0.8f), Size(u * 3f, u * 2f),
            style = Stroke(width = u * 0.2f, cap = StrokeCap.Round))
        // Stylus position at arc tip
        val stylusAngle = -90f + progress * 360f
        val stylusX = origin.x + u * 1.5f * cos(Math.toRadians(stylusAngle.toDouble())).toFloat()
        val stylusY = origin.y - tabletH + u * 1.8f + u * sin(Math.toRadians(stylusAngle.toDouble())).toFloat()
        drawCircle(Color.White, u * 0.35f, Offset(stylusX, stylusY))
    }

    /** Easel with canvas showing progressive painting */
    fun DrawScope.drawEaselCanvas(origin: Offset, u: Float, color: Color, anim: Float) {
        val woodColor = Color(0xFF8B6914)
        // Legs (A-frame)
        drawLine(woodColor, Offset(origin.x, origin.y - u * 6f), Offset(origin.x - u * 1.5f, origin.y), strokeWidth = u * 0.35f)
        drawLine(woodColor, Offset(origin.x, origin.y - u * 6f), Offset(origin.x + u * 1.5f, origin.y), strokeWidth = u * 0.35f)
        drawLine(woodColor, Offset(origin.x - u * 1f, origin.y - u * 2.5f), Offset(origin.x + u * 1f, origin.y - u * 2.5f), strokeWidth = u * 0.3f)
        // Canvas
        val cw = u * 3f; val ch = u * 3.5f
        drawRect(Color.White.copy(0.9f), Offset(origin.x - cw / 2, origin.y - u * 5.5f), Size(cw, ch))
        // Abstract brushstrokes (animated)
        val strokes = listOf(color, Color(0xFFFF6B6B), Color(0xFFFFE66D), Color(0xFF95E1D3))
        repeat((anim * strokes.size).toInt().coerceAtMost(strokes.size)) { i ->
            val phase = (anim - i / strokes.size.toFloat()).coerceIn(0f, 1f)
            drawArc(strokes[i].copy(0.8f), 30f + i * 60f, 120f * phase, false,
                Offset(origin.x - u + i * u * 0.5f, origin.y - u * 5f + i * u * 0.3f),
                Size(u * 2f, u * 1.5f),
                style = Stroke(u * 0.35f, cap = StrokeCap.Round))
        }
    }

    /** Glowing lightbulb — pulsing idea */
    fun DrawScope.drawLightbulb(origin: Offset, u: Float, color: Color, anim: Float) {
        val pulse = 0.6f + sin((anim * PI * 2).toFloat()) * 0.4f
        val bulbColor = color.copy(alpha = pulse)
        // Glow
        drawCircle(color.copy(0.12f * pulse), u * 4f, origin)
        drawCircle(color.copy(0.2f * pulse), u * 2.5f, origin)
        // Bulb
        drawCircle(bulbColor, u * 1.5f, origin)
        // Base threads
        repeat(3) { i ->
            drawLine(darken(color, 0.3f), Offset(origin.x - u * 0.5f, origin.y + u * 1.5f + i * u * 0.4f),
                Offset(origin.x + u * 0.5f, origin.y + u * 1.5f + i * u * 0.4f), strokeWidth = u * 0.25f)
        }
        // Filament
        drawLine(Color.Yellow.copy(0.7f * pulse), Offset(origin.x - u * 0.5f, origin.y), Offset(origin.x + u * 0.5f, origin.y - u * 0.5f), strokeWidth = u * 0.15f)
    }

    /** Color palette disc */
    fun DrawScope.drawColorPalette(origin: Offset, u: Float, color: Color, anim: Float) {
        val rotAngle = anim * 15f // very slow rotation
        val r = u * 2f
        val paletteColors = listOf(Color.Red, Color(0xFFFFA040), Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color(0xFFFF6EC7))
        withTransform({ rotate(rotAngle, origin) }) {
            // Background disc
            drawCircle(Color(0xFF2A1F0A), r, origin)
            paletteColors.forEachIndexed { i, c ->
                val angle = i * 45f
                drawArc(c, angle, 38f, true, Offset(origin.x - r, origin.y - r), Size(r * 2, r * 2))
            }
            // Center hole
            drawCircle(Color(0xFF2A1F0A), r * 0.3f, origin)
        }
    }

    /** Neon sign — flickering text glow */
    fun DrawScope.drawNeonSign(origin: Offset, u: Float, color: Color, anim: Float) {
        val flicker = if ((anim * 8).toInt() == 3) 0.4f else 0.9f // occasional flicker
        val signW = u * 4f; val signH = u * 1.8f
        drawRoundRect(Color(0xFF1A1A1A), Offset(origin.x - signW / 2, origin.y - signH), Size(signW, signH), CornerRadius(u * 0.3f))
        // Neon glow bands
        drawRoundRect(color.copy(alpha = flicker * 0.25f), Offset(origin.x - signW / 2 + u * 0.2f, origin.y - signH + u * 0.2f), Size(signW - u * 0.4f, signH - u * 0.4f), CornerRadius(u * 0.2f))
        drawRoundRect(color.copy(alpha = flicker * 0.8f), Offset(origin.x - signW / 2 + u * 0.3f, origin.y - signH + u * 0.3f), Size(signW - u * 0.6f, signH - u * 0.6f), CornerRadius(u * 0.15f), style = Stroke(u * 0.2f))
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 5 — MEMORY ROOM ASSETS
    // ════════════════════════════════════════════════════════════════════════

    /** Central floating brain orb with synapse glow */
    fun DrawScope.drawBrainOrb(origin: Offset, u: Float, color: Color, anim: Float) {
        val hoverY = origin.y + sin((anim * PI * 2).toFloat()) * u * 0.8f
        val center = Offset(origin.x, hoverY - u * 3f)
        val r = u * 2.5f

        drawCircle(color.copy(0.1f), r * 2.5f, center)
        drawCircle(Brush.radialGradient(
            listOf(lighten(color, 0.3f), color, darken(color, 0.2f)),
            center, r * 1.5f
        ), r, center)
        // Neural network overlay
        repeat(6) { i ->
            val a = (i * 60f + anim * 30f).toFloat()
            val nx = center.x + r * 0.6f * cos(Math.toRadians(a.toDouble())).toFloat()
            val ny = center.y + r * 0.6f * sin(Math.toRadians(a.toDouble())).toFloat()
            drawCircle(Color.White.copy(0.4f), u * 0.3f, Offset(nx, ny))
            drawLine(Color.White.copy(0.2f), center, Offset(nx, ny), strokeWidth = u * 0.1f)
        }
    }

    /** Memory crystal shard (rotating, refracting) */
    fun DrawScope.drawMemoryShard(origin: Offset, u: Float, color: Color, anim: Float) {
        val rotAngle = anim * 360f
        val facets = listOf(
            Color(lighten(color, 0.4f).value),
            Color(color.value),
            Color(darken(color, 0.2f).value),
            Color(lighten(color, 0.15f).value),
        )
        withTransform({ rotate(rotAngle, origin) }) {
            // Crystal polygon
            val shardPath = Path().apply {
                moveTo(origin.x, origin.y - u * 2.5f)
                lineTo(origin.x + u * 1.5f, origin.y - u * 0.5f)
                lineTo(origin.x + u * 1f, origin.y + u * 2f)
                lineTo(origin.x - u * 1f, origin.y + u * 2f)
                lineTo(origin.x - u * 1.5f, origin.y - u * 0.5f)
                close()
            }
            drawPath(shardPath, facets[0].copy(0.7f))
            // Inner facet lines
            drawLine(facets[2].copy(0.5f), Offset(origin.x, origin.y - u * 2.5f), Offset(origin.x + u * 0.5f, origin.y + u * 0.5f), strokeWidth = u * 0.15f)
            drawLine(facets[1].copy(0.5f), Offset(origin.x, origin.y - u * 2.5f), Offset(origin.x - u * 0.5f, origin.y + u * 0.5f), strokeWidth = u * 0.15f)
        }
        // Sparkle
        drawCircle(color.copy(0.3f), u * 3f, origin)
    }

    /** Data archive cylinder */
    fun DrawScope.drawArchiveCylinder(origin: Offset, u: Float, color: Color, anim: Float) {
        val h = u * 3f; val r = u * 1.5f
        val pulse = 0.4f + sin((anim * PI * 2).toFloat()) * 0.3f

        // Cylinder body
        drawRect(darken(color, 0.4f), Offset(origin.x - r, origin.y - h), Size(r * 2f, h))
        // Top oval
        drawOval(color.copy(0.8f), Offset(origin.x - r, origin.y - h - r * 0.4f), Size(r * 2f, r * 0.8f))
        drawOval(color.copy(0.15f + pulse * 0.2f), Offset(origin.x - r, origin.y - h - r * 0.4f), Size(r * 2f, r * 0.8f)) // glow
        // Data rings
        repeat(4) { i ->
            drawOval(color.copy(0.3f), Offset(origin.x - r, origin.y - h * 0.8f + i * h * 0.18f), Size(r * 2f, r * 0.5f), style = Stroke(u * 0.12f))
        }
    }

    /** Neural synapse web on ceiling */
    fun DrawScope.drawSynapseWeb(origin: Offset, u: Float, color: Color, anim: Float) {
        val nodes = listOf(
            Offset(origin.x, origin.y - u * 2f),
            Offset(origin.x - u * 3f, origin.y),
            Offset(origin.x + u * 3f, origin.y),
            Offset(origin.x - u * 1.5f, origin.y - u * 4f),
            Offset(origin.x + u * 1.5f, origin.y - u * 4f),
        )
        // Connections
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val pulse = (anim + (i + j) * 0.15f) % 1f
                drawLine(color.copy(0.2f + pulse * 0.3f), nodes[i], nodes[j], strokeWidth = u * 0.12f)
            }
        }
        // Nodes
        nodes.forEach { n ->
            drawCircle(color.copy(0.6f), u * 0.4f, n)
        }
    }

    /** Timestamp rings (concentric ripples on floor) */
    fun DrawScope.drawTimestampRings(origin: Offset, u: Float, color: Color, anim: Float) {
        repeat(4) { i ->
            val r = u * (1.5f + i * 0.8f)
            val phase = (anim + i * 0.25f) % 1f
            drawCircle(color.copy((1f - phase) * 0.4f), r, origin, style = Stroke(u * 0.15f * (1f - phase * 0.5f)))
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 6 — ANALYST ROOM ASSETS
    // ════════════════════════════════════════════════════════════════════════

    /** Stats dashboard (hovering panel with metrics) */
    fun DrawScope.drawStatsDashboard(origin: Offset, u: Float, color: Color, anim: Float) {
        val hoverY = origin.y - u * 4f + sin((anim * PI * 2).toFloat()) * u * 0.4f
        val panelW = u * 5f; val panelH = u * 3.5f
        // Panel
        drawRoundRect(Color(0xFF0A0E1A).copy(0.9f), Offset(origin.x - panelW / 2, hoverY - panelH), Size(panelW, panelH), CornerRadius(u * 0.4f))
        drawRoundRect(color.copy(0.3f), Offset(origin.x - panelW / 2, hoverY - panelH), Size(panelW, panelH), CornerRadius(u * 0.4f), style = Stroke(u * 0.15f))
        // Bar chart
        val barColors = listOf(color, darken(color, 0.2f), lighten(color, 0.2f), darken(color, 0.1f))
        val heights = listOf(0.6f, 0.9f, 0.4f, 0.75f)
        heights.forEachIndexed { i, h ->
            val bx = origin.x - panelW * 0.35f + i * panelW * 0.22f
            val bH = panelH * 0.6f * h
            drawRoundRect(barColors[i].copy(0.8f), Offset(bx, hoverY - u * 0.4f - bH), Size(panelW * 0.15f, bH), CornerRadius(u * 0.1f))
        }
    }

    /** Data stream particle column */
    fun DrawScope.drawDataStream(origin: Offset, u: Float, color: Color, anim: Float) {
        // Channel outline
        drawRoundRect(color.copy(0.15f), Offset(origin.x - u * 0.6f, origin.y - u * 6f), Size(u * 1.2f, u * 6f), CornerRadius(u * 0.6f))
        // Particles
        repeat(8) { i ->
            val phase = (anim + i * 0.125f) % 1f
            val py = origin.y - phase * u * 6f
            drawCircle(color.copy(1f - phase), u * (0.4f - phase * 0.2f), Offset(origin.x, py))
        }
    }

    /** Wall charts (bar + line graph on the wall face) */
    fun DrawScope.drawWallCharts(origin: Offset, u: Float, color: Color, anim: Float) {
        val w = u * 4f; val h = u * 3f
        val tl = Offset(origin.x - w / 2, origin.y - h)
        // Background
        drawRoundRect(Color(0xFF0A0E1A), tl, Size(w, h), CornerRadius(u * 0.3f))
        // Grid lines
        repeat(4) { i -> drawLine(color.copy(0.1f), Offset(tl.x, tl.y + i * h / 3), Offset(tl.x + w, tl.y + i * h / 3), strokeWidth = u * 0.07f) }
        // Bar chart (animated heights)
        val bars = listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f)
        bars.forEachIndexed { i, h2 ->
            val animH = h2 * (0.8f + sin((anim * PI * 2 + i * 0.5f).toFloat()) * 0.2f)
            val bx = tl.x + u * 0.3f + i * (w - u * 0.6f) / bars.size
            drawRoundRect(color.copy(0.7f), Offset(bx, tl.y + h - h * animH), Size(w / bars.size - u * 0.2f, h * animH), CornerRadius(u * 0.1f))
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 7 — COORDINATOR ROOM ASSETS
    // ════════════════════════════════════════════════════════════════════════

    /** Network globe with agent connection arcs */
    fun DrawScope.drawNetworkGlobe(origin: Offset, u: Float, color: Color, anim: Float) {
        val r = u * 3f
        val rotAngle = anim * 360f
        // Globe
        drawCircle(Color(0xFF0A0E1A), r, origin)
        drawCircle(color.copy(0.2f), r, origin, style = Stroke(u * 0.2f))
        withTransform({ rotate(rotAngle, origin) }) {
            // Meridian
            drawOval(color.copy(0.35f), Offset(origin.x - r * 0.5f, origin.y - r), Size(r, r * 2f), style = Stroke(u * 0.15f))
            // Equator
            drawOval(color.copy(0.35f), Offset(origin.x - r, origin.y - r * 0.3f), Size(r * 2f, r * 0.6f), style = Stroke(u * 0.15f))
        }
        // Connection arcs (pulsing)
        repeat(4) { i ->
            val phase = (anim + i * 0.25f) % 1f
            val startAngle = (i * 90f + rotAngle) % 360f
            drawArc(color.copy(0.4f * (1f - phase)), startAngle, 60f, false,
                Offset(origin.x - r, origin.y - r), Size(r * 2f, r * 2f),
                style = Stroke(u * 0.25f))
        }
    }

    /** Conference table (large round, isometric) */
    fun DrawScope.drawConferenceTable(origin: Offset, u: Float, color: Color, anim: Float) {
        val tableColor = Color(0xFF4A3020)
        // Top oval (table surface)
        drawOval(tableColor, Offset(origin.x - u * 4f, origin.y - u * 2f), Size(u * 8f, u * 4f))
        drawOval(lighten(tableColor, 0.1f), Offset(origin.x - u * 3.5f, origin.y - u * 1.7f), Size(u * 7f, u * 3.4f))
        // Leg
        drawRect(darken(tableColor, 0.2f), Offset(origin.x - u * 0.4f, origin.y), Size(u * 0.8f, u * 1.5f))
    }

    /** Agenda board / task list */
    fun DrawScope.drawAgendaBoard(origin: Offset, u: Float, color: Color, anim: Float) {
        val w = u * 3.5f; val h = u * 4f
        drawRoundRect(Color(0xFF1A1A2E), Offset(origin.x - w / 2, origin.y - h), Size(w, h), CornerRadius(u * 0.2f))
        drawRoundRect(Color.White.copy(0.85f), Offset(origin.x - w / 2 + u * 0.2f, origin.y - h + u * 0.2f), Size(w - u * 0.4f, h - u * 0.4f), CornerRadius(u * 0.1f))
        // Tasks (some checked, some not)
        val tasks = listOf(true, true, false, true, false)
        tasks.forEachIndexed { i, done ->
            val ty = origin.y - h + u * 0.6f + i * u * 0.65f
            // Checkbox
            drawRoundRect(if (done) color else Color.Gray.copy(0.3f), Offset(origin.x - w / 2 + u * 0.3f, ty - u * 0.2f), Size(u * 0.4f, u * 0.4f), CornerRadius(u * 0.1f))
            if (done) drawLine(Color.White, Offset(origin.x - w / 2 + u * 0.35f, ty), Offset(origin.x - w / 2 + u * 0.7f, ty - u * 0.3f), strokeWidth = u * 0.1f)
            // Line
            drawLine(Color.Gray.copy(0.5f), Offset(origin.x - w / 2 + u * 0.8f, ty), Offset(origin.x + w / 2 - u * 0.3f, ty), strokeWidth = u * 0.1f)
        }
    }

    /** Radio signal tower */
    fun DrawScope.drawRadioTower(origin: Offset, u: Float, color: Color, anim: Float) {
        // Tower structure
        drawLine(Color(0xFF888888), Offset(origin.x, origin.y), Offset(origin.x, origin.y - u * 5f), strokeWidth = u * 0.3f)
        listOf(1f, 2.5f, 4f).forEach { ht ->
            drawLine(Color(0xFF888888), Offset(origin.x - u * 0.8f, origin.y - ht * u), Offset(origin.x + u * 0.8f, origin.y - ht * u), strokeWidth = u * 0.2f)
        }
        // Signal arcs (rippling outward)
        repeat(3) { i ->
            val phase = (anim + i * 0.33f) % 1f
            val r = u * (1f + phase * 3f)
            drawArc(color.copy((1f - phase) * 0.6f), -60f, 120f, false,
                Offset(origin.x - r, origin.y - u * 5f - r), Size(r * 2f, r * 2f),
                style = Stroke(u * 0.25f * (1f - phase)))
        }
        // Antenna tip
        drawCircle(color, u * 0.4f, Offset(origin.x, origin.y - u * 5f))
    }

    /** Star badge (floating, slowly orbiting) */
    fun DrawScope.drawStarBadge(origin: Offset, u: Float, color: Color, anim: Float) {
        val hoverY = origin.y - u * 4f + sin((anim * PI * 2).toFloat()) * u * 0.6f
        val center = Offset(origin.x, hoverY)
        val outerR = u * 2.5f
        val innerR = u * 1.1f
        val points = 5
        val starPath = Path()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = Math.toRadians((i * 180.0 / points - 90))
            val x = center.x + (r * cos(angle)).toFloat()
            val y = center.y + (r * sin(angle)).toFloat()
            if (i == 0) starPath.moveTo(x, y) else starPath.lineTo(x, y)
        }
        starPath.close()
        drawPath(starPath, color.copy(0.2f))
        drawPath(starPath, color, style = Stroke(width = u * 0.3f))
        // Glow
        drawCircle(color.copy(0.15f), outerR * 1.5f, center)
    }

    // ════════════════════════════════════════════════════════════════════════
    // SECTION 8 — SHARED / AMBIENT ASSETS
    // ════════════════════════════════════════════════════════════════════════

    /** Desk lamp with cone of light on floor */
    fun DrawScope.drawDeskLamp(origin: Offset, u: Float, color: Color, anim: Float) {
        val lampColor = Color(0xFF888888)
        val warmLight = Color(0xFFFFD060)
        // Base
        drawOval(darken(lampColor, 0.2f), Offset(origin.x - u, origin.y - u * 0.3f), Size(u * 2f, u * 0.6f))
        // Arm
        val path = Path().apply {
            moveTo(origin.x, origin.y - u * 0.3f)
            quadraticBezierTo(origin.x - u * 0.5f, origin.y - u * 2f, origin.x - u, origin.y - u * 3.5f)
        }
        drawPath(path, lampColor, style = Stroke(u * 0.35f, cap = StrokeCap.Round))
        // Shade
        drawArc(darken(lampColor, 0.1f), 0f, 180f, true, Offset(origin.x - u * 1.8f, origin.y - u * 4.2f), Size(u * 1.6f, u * 0.8f))
        // Light cone
        val conePath = Path().apply {
            moveTo(origin.x - u * 0.9f, origin.y - u * 3.5f)
            lineTo(origin.x - u * 2f, origin.y)
            lineTo(origin.x + u * 0.2f, origin.y)
            close()
        }
        drawPath(conePath, warmLight.copy(0.06f))
    }

    /** Floor rug decorative patch */
    fun DrawScope.drawFloorRug(origin: Offset, u: Float, color: Color, anim: Float) {
        val rugW = u * 6f; val rugH = u * 3f
        drawOval(color.copy(0.2f), Offset(origin.x - rugW / 2, origin.y - rugH / 2), Size(rugW, rugH))
        drawOval(color.copy(0.12f), Offset(origin.x - rugW / 2 + u, origin.y - rugH / 2 + u * 0.5f), Size(rugW - u * 2, rugH - u))
        // Decorative cross lines
        drawLine(color.copy(0.15f), Offset(origin.x - rugW / 2 + u * 0.5f, origin.y), Offset(origin.x + rugW / 2 - u * 0.5f, origin.y), strokeWidth = u * 0.15f)
        drawLine(color.copy(0.15f), Offset(origin.x, origin.y - rugH / 2 + u * 0.3f), Offset(origin.x, origin.y + rugH / 2 - u * 0.3f), strokeWidth = u * 0.15f)
    }

    /** Ambient dust motes (always-on slow drift) */
    fun DrawScope.drawAmbientDustMotes(origin: Offset, u: Float, color: Color, anim: Float) {
        repeat(6) { i ->
            val phase = (anim * 0.3f + i * 0.16f) % 1f
            val dx = sin((phase * PI * 2 + i * 1.1f).toFloat()) * u * 3f
            val dy = origin.y - phase * u * 5f + i * u * 1.2f
            drawCircle(color.copy((1f - phase) * 0.12f), u * 0.2f, Offset(origin.x + dx, dy))
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // HELPER: Generic Isometric box + flat surface primitives
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Draws a flat isometric top surface (rhombus) — useful for desks, floors, tables.
     */
    fun DrawScope.drawIsoFlatSurface(
        origin: Offset, w: Float, d: Float, h: Float,
        topColor: Color, leftColor: Color, rightColor: Color
    ) {
        val hw = w / 2f; val hd = d / 2f
        // Top rhombus
        val topPath = Path().apply {
            moveTo(origin.x,    origin.y - h - hd * 0.5f)
            lineTo(origin.x + hw, origin.y - h)
            lineTo(origin.x,    origin.y - h + hd * 0.5f)
            lineTo(origin.x - hw, origin.y - h)
            close()
        }
        drawPath(topPath, topColor)
        // Left side face
        val leftPath = Path().apply {
            moveTo(origin.x - hw, origin.y - h)
            lineTo(origin.x,    origin.y - h + hd * 0.5f)
            lineTo(origin.x,    origin.y + hd * 0.5f)
            lineTo(origin.x - hw, origin.y)
            close()
        }
        drawPath(leftPath, leftColor)
        // Right side face
        val rightPath = Path().apply {
            moveTo(origin.x,    origin.y - h + hd * 0.5f)
            lineTo(origin.x + hw, origin.y - h)
            lineTo(origin.x + hw, origin.y)
            lineTo(origin.x,    origin.y + hd * 0.5f)
            close()
        }
        drawPath(rightPath, rightColor)
    }

    /**
     * Draws a solid isometric box with 3 visible faces.
     */
    fun DrawScope.drawIsoBox(origin: Offset, w: Float, d: Float, h: Float, topColor: Color, leftColor: Color, rightColor: Color) {
        val hw = w / 2f; val hd = d / 2f

        val topLeft  = Offset(origin.x - hw, origin.y - h)
        val topMid   = Offset(origin.x,      origin.y - h - hd * 0.5f)
        val topRight = Offset(origin.x + hw, origin.y - h)
        val topBack  = Offset(origin.x,      origin.y - h + hd * 0.5f)

        val botLeft  = Offset(origin.x - hw, origin.y)
        val botMid   = Offset(origin.x,      origin.y + hd * 0.5f)
        val botRight = Offset(origin.x + hw, origin.y)

        // Top face
        drawPath(Path().apply {
            moveTo(topMid.x, topMid.y); lineTo(topRight.x, topRight.y)
            lineTo(topBack.x, topBack.y); lineTo(topLeft.x, topLeft.y); close()
        }, topColor)

        // Left face
        drawPath(Path().apply {
            moveTo(topLeft.x, topLeft.y); lineTo(topBack.x, topBack.y)
            lineTo(botMid.x, botMid.y); lineTo(botLeft.x, botLeft.y); close()
        }, leftColor)

        // Right face
        drawPath(Path().apply {
            moveTo(topBack.x, topBack.y); lineTo(topRight.x, topRight.y)
            lineTo(botRight.x, botRight.y); lineTo(botMid.x, botMid.y); close()
        }, rightColor)
    }
}

// ── COLOR HELPERS (module-private) ───────────────────────────────────────────

internal fun lighten(color: Color, amount: Float): Color = Color(
    red   = (color.red   + amount).coerceIn(0f, 1f),
    green = (color.green + amount).coerceIn(0f, 1f),
    blue  = (color.blue  + amount).coerceIn(0f, 1f),
    alpha = color.alpha
)

internal fun darken(color: Color, amount: Float): Color = lighten(color, -amount)
