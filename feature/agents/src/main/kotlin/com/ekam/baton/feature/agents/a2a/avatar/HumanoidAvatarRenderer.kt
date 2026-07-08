package com.ekam.baton.feature.agents.a2a.avatar

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import com.ekam.baton.core.data.model.AgentRole
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full humanoid avatar renderer for the Baton Agent World.
 *
 * The avatar is built from 8 geometric primitives arranged in a consistent skeleton:
 *   Head (circle) → Neck (rounded rect) → Torso (rounded rect with role icon)
 *   → 2 Arms (bezier curves) → 2 Legs (rounded rects) → Feet (ellipses)
 *
 * All proportions are relative to the base unit [u] so the avatar scales cleanly.
 * Animations are driven by [AvatarAnimValues] from [rememberAvatarAnimValues].
 *
 * @param footCenter  The isometric screen position at the avatar's feet (ground contact).
 * @param components  Seeded visual component selection from [buildAvatarComponents].
 * @param state       Current activity state from [AvatarActivityState].
 * @param anim        Live animation values from [rememberAvatarAnimValues].
 * @param isoScale    Additional scale from the world canvas zoom level.
 */
fun DrawScope.drawHumanoid(
    footCenter: Offset,
    components: AvatarComponents,
    state: AvatarActivityState,
    anim: AvatarAnimValues,
    isoScale: Float = 1f
) {
    val u = 5.5f * isoScale          // base unit — all proportions relative to this
    val accent = components.accentColor
    val skin   = components.skinTone

    // ── GROUND CONTACT SHADOW ────────────────────────────────────────────────
    drawContactShadow(footCenter, u * 3f)

    // ── SKELETON ANCHOR POINTS ───────────────────────────────────────────────
    val pelvis  = footCenter.upBy(u * 8f)
    val chest   = footCenter.upBy(u * 16f)
    val neck    = footCenter.upBy(u * 20f)
    val headCtr = footCenter.upBy(u * 26f)

    val torsoW = u * when (components.bodyBuild) {
        BodyBuild.SLIM   -> 3.5f
        BodyBuild.MEDIUM -> 4.5f
        BodyBuild.BROAD  -> 5.5f
    }

    // ── LEGS ─────────────────────────────────────────────────────────────────
    val legSway = if (state == AvatarActivityState.ACTIVE) sin(anim.walkCycle) * u * 1.5f else 0f
    val legColor = darken(accent, 0.3f)
    val legW = u * 1.8f
    val legH = u * 8f

    // Left leg
    drawRoundRect(
        color = legColor,
        topLeft = Offset(pelvis.x - legW * 1.8f - legSway, pelvis.y),
        size = Size(legW, legH),
        cornerRadius = CornerRadius(u * 0.9f)
    )
    // Right leg
    drawRoundRect(
        color = legColor,
        topLeft = Offset(pelvis.x + legW * 0.8f + legSway, pelvis.y),
        size = Size(legW, legH),
        cornerRadius = CornerRadius(u * 0.9f)
    )
    // Feet
    drawOval(color = darken(accent, 0.4f), topLeft = Offset(pelvis.x - legW * 2f - legSway, pelvis.y + legH - u * 0.5f), size = Size(legW * 1.3f, u * 1.2f))
    drawOval(color = darken(accent, 0.4f), topLeft = Offset(pelvis.x + legW * 0.5f + legSway, pelvis.y + legH - u * 0.5f), size = Size(legW * 1.3f, u * 1.2f))

    // ── TORSO ────────────────────────────────────────────────────────────────
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(lighten(accent, 0.25f), darken(accent, 0.35f)),
            startY = chest.y, endY = pelvis.y
        ),
        topLeft = Offset(chest.x - torsoW / 2f, chest.y),
        size = Size(torsoW, u * 8f),
        cornerRadius = CornerRadius(u * 1.2f)
    )

    // Role icon on chest (centered)
    val iconCenter = Offset(chest.x, chest.y + u * 4f)
    drawRoleIconShape(iconCenter, components.role, u * 1.8f, Color.White.copy(alpha = 0.9f))

    // ── ARMS (organic bezier curves for natural feel) ─────────────────────────
    val armSwing = if (state == AvatarActivityState.ACTIVE)
        cos(anim.walkCycle) * u * 2f
    else
        u * 0.4f

    drawArm(
        from  = Offset(chest.x - torsoW / 2f, chest.y + u * 1.5f),
        swing = armSwing, u = u, color = darken(accent, 0.15f), isLeft = true
    )
    drawArm(
        from  = Offset(chest.x + torsoW / 2f, chest.y + u * 1.5f),
        swing = -armSwing, u = u, color = darken(accent, 0.15f), isLeft = false
    )

    // ── NECK ─────────────────────────────────────────────────────────────────
    drawRoundRect(
        color = darken(skin, 0.1f),
        topLeft = Offset(neck.x - u * 0.8f, neck.y),
        size = Size(u * 1.6f, u * 2f),
        cornerRadius = CornerRadius(u * 0.5f)
    )

    // ── HEAD ─────────────────────────────────────────────────────────────────
    drawHead(headCtr, u, components, accent)

    // ── STATE EFFECTS ─────────────────────────────────────────────────────────
    when (state) {
        AvatarActivityState.THINKING -> drawThinkingOrbit(headCtr, u, accent, anim)
        AvatarActivityState.ACTIVE   -> drawRisingParticles(chest.y, chest.x, u, accent, anim)
        AvatarActivityState.ERROR    -> drawErrorGlow(headCtr, u, anim)
        AvatarActivityState.WAITING  -> drawWaitingRing(headCtr, u, accent, anim)
        AvatarActivityState.SUCCESS  -> drawSuccessBurst(headCtr, u, accent)
        AvatarActivityState.IDLE     -> { /* breathing handled by isoScale */ }
    }

    // ── ACTIVITY STATUS RING (always visible) ─────────────────────────────────
    val stateColor = try {
        Color(android.graphics.Color.parseColor(state.colorHex))
    } catch (_: Exception) { Color.Gray }

    drawCircle(
        color = stateColor,
        radius = u * 1.2f,
        center = Offset(footCenter.x + u * 3f, footCenter.y - u * 26f),
        style = Stroke(width = u * 0.5f)
    )
    if (state != AvatarActivityState.IDLE) {
        drawCircle(
            color = stateColor.copy(alpha = 0.3f),
            radius = u * 2f,
            center = Offset(footCenter.x + u * 3f, footCenter.y - u * 26f)
        )
    }
}

// ── PRIVATE DRAWING HELPERS ───────────────────────────────────────────────────

private fun DrawScope.drawHead(center: Offset, u: Float, components: AvatarComponents, accent: Color) {
    val skin = components.skinTone
    val r = when (components.headShape) {
        HeadShape.ROUND, HeadShape.WIDE -> u * 4.5f
        HeadShape.ANGULAR               -> u * 4f
        HeadShape.SQUARE                -> u * 4.2f
    }

    // Glow halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.4f), Color.Transparent),
            center = center, radius = r * 2.2f
        ),
        radius = r * 2.2f, center = center
    )

    // Skull — radial gradient for sphere-like 3D shading (lit top-right)
    val litCenter = Offset(center.x - u * 0.8f, center.y - u * 1.2f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lighten(skin, 0.35f), skin, darken(skin, 0.25f)),
            center = litCenter, radius = r * 2f
        ),
        radius = r, center = center
    )

    // Specular highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.18f),
        radius = r * 0.4f,
        center = Offset(center.x - r * 0.35f, center.y - r * 0.4f)
    )

    // Hair (drawn behind head but composited on top of ears)
    drawHair(center, r, u, components.hairStyle, accent)

    // Eyes
    drawEyes(center, u, components.eyeStyle, accent)
}

private fun DrawScope.drawHair(center: Offset, r: Float, u: Float, style: HairStyle, accent: Color) {
    val hairColor = darken(accent, 0.45f)
    when (style) {
        HairStyle.BALD -> { /* no hair */ }
        HairStyle.SHORT -> {
            drawArc(
                color = hairColor,
                startAngle = 190f, sweepAngle = 160f, useCenter = true,
                topLeft = Offset(center.x - r, center.y - r * 1.1f),
                size = Size(r * 2f, r * 1.4f)
            )
        }
        HairStyle.TALL_SPIKY -> {
            // Jagged spikes
            val spikePath = Path().apply {
                moveTo(center.x - r * 0.8f, center.y - r * 0.8f)
                lineTo(center.x - r * 0.6f, center.y - r * 1.8f)
                lineTo(center.x - r * 0.3f, center.y - r * 1.1f)
                lineTo(center.x,             center.y - r * 2f)
                lineTo(center.x + r * 0.3f, center.y - r * 1.2f)
                lineTo(center.x + r * 0.6f, center.y - r * 1.7f)
                lineTo(center.x + r * 0.8f, center.y - r * 0.8f)
                close()
            }
            drawPath(spikePath, hairColor)
        }
        HairStyle.WAVE -> {
            val wavePath = Path().apply {
                moveTo(center.x - r, center.y - r * 0.5f)
                quadraticBezierTo(center.x - r * 0.5f, center.y - r * 1.6f, center.x, center.y - r * 1.4f)
                quadraticBezierTo(center.x + r * 0.5f, center.y - r * 1.2f, center.x + r, center.y - r * 0.5f)
                close()
            }
            drawPath(wavePath, hairColor)
        }
    }
}

private fun DrawScope.drawEyes(center: Offset, u: Float, style: EyeStyle, accent: Color) {
    val eyeY = center.y - u * 0.6f
    val lx = center.x - u * 1.5f
    val rx = center.x + u * 1.5f

    when (style) {
        EyeStyle.DOTS, EyeStyle.ROUND_GLOW -> {
            listOf(lx, rx).forEach { ex ->
                // White sclera
                drawCircle(Color.White.copy(0.95f), u * 0.85f, Offset(ex, eyeY))
                // Iris (accent)
                drawCircle(accent, u * 0.55f, Offset(ex, eyeY))
                // Pupil
                drawCircle(Color.Black.copy(0.7f), u * 0.28f, Offset(ex, eyeY))
                // Specular highlight (key to making them feel alive)
                drawCircle(Color.White, u * 0.18f, Offset(ex - u * 0.25f, eyeY - u * 0.25f))
                if (style == EyeStyle.ROUND_GLOW) {
                    drawCircle(accent.copy(0.35f), u * 1.3f, Offset(ex, eyeY))
                }
            }
        }
        EyeStyle.SCAN_LINE -> {
            listOf(lx, rx).forEach { ex ->
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(ex - u * 0.75f, eyeY - u * 0.22f),
                    size = Size(u * 1.5f, u * 0.44f),
                    cornerRadius = CornerRadius(u * 0.22f)
                )
                // Scan glow
                drawRoundRect(
                    color = accent.copy(0.25f),
                    topLeft = Offset(ex - u * 0.9f, eyeY - u * 0.4f),
                    size = Size(u * 1.8f, u * 0.8f),
                    cornerRadius = CornerRadius(u * 0.3f)
                )
            }
        }
        EyeStyle.ALMOND -> {
            listOf(lx, rx).forEach { ex ->
                val path = Path().apply {
                    moveTo(ex - u, eyeY)
                    quadraticBezierTo(ex, eyeY - u * 0.7f, ex + u, eyeY)
                    quadraticBezierTo(ex, eyeY + u * 0.45f, ex - u, eyeY)
                    close()
                }
                drawPath(path, accent)
                drawCircle(Color.White, u * 0.18f, Offset(ex - u * 0.35f, eyeY - u * 0.15f))
            }
        }
    }
}

private fun DrawScope.drawArm(
    from: Offset, swing: Float, u: Float, color: Color, isLeft: Boolean
) {
    val sign = if (isLeft) -1f else 1f
    val armPath = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(
            from.x + sign * u * 2f, from.y + swing,
            from.x + sign * u * 3.5f, from.y + u * 4f - swing,
            from.x + sign * u * 3f,   from.y + u * 8f
        )
    }
    drawPath(armPath, color, style = Stroke(width = u * 2f, cap = StrokeCap.Round))
    // Hand (small circle at end)
    val handCenter = Offset(from.x + sign * u * 3f, from.y + u * 8f)
    drawCircle(lighten(color, 0.1f), u * 1.1f, handCenter)
}

// ── STATE EFFECT HELPERS ──────────────────────────────────────────────────────

private fun DrawScope.drawThinkingOrbit(head: Offset, u: Float, accent: Color, anim: AvatarAnimValues) {
    repeat(3) { i ->
        val angle = Math.toRadians((anim.orbitAngle + i * 120f).toDouble())
        val px = head.x + (u * 8f) * cos(angle).toFloat()
        val py = head.y - (u * 4f) + (u * 3.5f) * sin(angle).toFloat()
        drawCircle(accent.copy(0.7f), u * 1.3f, Offset(px, py))
    }
}

private fun DrawScope.drawRisingParticles(chestY: Float, chestX: Float, u: Float, accent: Color, anim: AvatarAnimValues) {
    repeat(5) { i ->
        val phase = (anim.particlePhase + i * 0.2f) % 1f
        val px = chestX + (i - 2f) * u * 2.2f
        val py = chestY - phase * u * 18f
        drawCircle(accent.copy(alpha = (1f - phase) * 0.8f), u * 0.8f, Offset(px, py))
    }
}

private fun DrawScope.drawErrorGlow(head: Offset, u: Float, anim: AvatarAnimValues) {
    val red = Color(0xFFFF453A)
    drawCircle(red.copy(alpha = anim.glowPulse * 0.45f), u * 12f, head)
    // Shake-like distortion: drawn as a vibrating ring
    drawCircle(red.copy(0.6f), u * 6.5f, head, style = Stroke(width = u * 0.8f))
}

private fun DrawScope.drawWaitingRing(center: Offset, u: Float, accent: Color, anim: AvatarAnimValues) {
    val amber = Color(0xFFF59E0B)
    drawArc(
        color = amber.copy(alpha = 0.7f),
        startAngle = anim.orbitAngle,
        sweepAngle = 240f,
        useCenter = false,
        topLeft = Offset(center.x - u * 8f, center.y - u * 8f),
        size = Size(u * 16f, u * 16f),
        style = Stroke(width = u * 0.6f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(u * 2f, u * 1.5f)))
    )
}

private fun DrawScope.drawSuccessBurst(center: Offset, u: Float, accent: Color) {
    val green = Color(0xFF22C55E)
    repeat(8) { i ->
        val angle = Math.toRadians((i * 45f).toDouble())
        val endX = center.x + (u * 12f) * cos(angle).toFloat()
        val endY = center.y + (u * 12f) * sin(angle).toFloat()
        drawLine(green.copy(0.7f), center, Offset(endX, endY), strokeWidth = u * 0.8f, cap = StrokeCap.Round)
    }
    drawCircle(green.copy(0.3f), u * 10f, center)
}

// ── ROLE ICON SHAPES ──────────────────────────────────────────────────────────

private fun DrawScope.drawRoleIconShape(center: Offset, role: AgentRole, size: Float, color: Color) {
    when (role) {
        AgentRole.CODER -> {
            // ">_" terminal symbol
            val path = Path().apply {
                moveTo(center.x - size, center.y - size * 0.3f)
                lineTo(center.x - size * 0.3f, center.y)
                lineTo(center.x - size, center.y + size * 0.3f)
                moveTo(center.x - size * 0.1f, center.y + size * 0.3f)
                lineTo(center.x + size, center.y + size * 0.3f)
            }
            drawPath(path, color, style = Stroke(width = size * 0.28f, cap = StrokeCap.Round))
        }
        AgentRole.RESEARCHER -> {
            // Circle + handle (magnifying glass)
            drawCircle(color, size * 0.7f, center, style = Stroke(width = size * 0.25f))
            drawLine(color, Offset(center.x + size * 0.5f, center.y + size * 0.5f),
                Offset(center.x + size, center.y + size), strokeWidth = size * 0.25f, cap = StrokeCap.Round)
        }
        AgentRole.SECURITY -> {
            // Shield
            val shield = Path().apply {
                moveTo(center.x, center.y - size)
                lineTo(center.x + size, center.y - size * 0.4f)
                lineTo(center.x + size, center.y + size * 0.4f)
                quadraticBezierTo(center.x, center.y + size * 1.2f, center.x - size, center.y + size * 0.4f)
                lineTo(center.x - size, center.y - size * 0.4f)
                close()
            }
            drawPath(shield, color.copy(0.3f))
            drawPath(shield, color, style = Stroke(width = size * 0.25f))
        }
        AgentRole.CREATIVE -> {
            // Pencil
            val pencil = Path().apply {
                moveTo(center.x - size * 0.2f, center.y + size)
                lineTo(center.x + size * 0.8f, center.y - size)
                lineTo(center.x + size, center.y - size * 0.8f)
                lineTo(center.x, center.y + size * 1.2f)
                close()
            }
            drawPath(pencil, color.copy(0.4f))
            drawPath(pencil, color, style = Stroke(width = size * 0.2f))
        }
        AgentRole.MEMORY -> {
            // Three connected nodes (mini neural network)
            val n1 = Offset(center.x, center.y - size)
            val n2 = Offset(center.x - size, center.y + size * 0.5f)
            val n3 = Offset(center.x + size, center.y + size * 0.5f)
            drawLine(color.copy(0.6f), n1, n2, strokeWidth = size * 0.2f)
            drawLine(color.copy(0.6f), n1, n3, strokeWidth = size * 0.2f)
            drawLine(color.copy(0.6f), n2, n3, strokeWidth = size * 0.2f)
            listOf(n1, n2, n3).forEach { drawCircle(color, size * 0.35f, it) }
        }
        AgentRole.ANALYST -> {
            // Bar chart (3 bars of ascending height)
            val barW = size * 0.4f
            val heights = listOf(0.5f, 0.8f, 1.1f)
            heights.forEachIndexed { i, h ->
                val bx = center.x - size + i * (barW + size * 0.1f)
                drawRect(color, Offset(bx, center.y + size - size * h), Size(barW, size * h))
            }
        }
        AgentRole.COORDINATOR -> {
            // Globe circle with meridians
            drawCircle(color, size, center, style = Stroke(width = size * 0.22f))
            drawLine(color.copy(0.5f), Offset(center.x, center.y - size), Offset(center.x, center.y + size), strokeWidth = size * 0.18f)
            drawOval(color.copy(0.5f), Offset(center.x - size * 0.7f, center.y - size), Size(size * 1.4f, size * 2f), style = Stroke(width = size * 0.18f))
        }
    }
}

// ── COLOR UTILITY EXTENSIONS ──────────────────────────────────────────────────

private fun lighten(color: Color, amount: Float): Color = Color(
    red   = (color.red   + amount).coerceIn(0f, 1f),
    green = (color.green + amount).coerceIn(0f, 1f),
    blue  = (color.blue  + amount).coerceIn(0f, 1f),
    alpha = color.alpha
)

private fun darken(color: Color, amount: Float): Color = lighten(color, -amount)

private fun Offset.upBy(px: Float) = Offset(x, y - px)

private fun DrawScope.drawContactShadow(center: Offset, radius: Float) {
    drawIntoCanvas { canvas ->
        val shadowPaint = Paint().apply {
            asFrameworkPaint().apply {
                isAntiAlias = true
                color       = android.graphics.Color.TRANSPARENT
                setShadowLayer(radius * 0.6f, 0f, radius * 0.2f, android.graphics.Color.argb(80, 0, 0, 0))
            }
        }
        canvas.drawCircle(Offset(center.x, center.y + radius * 0.25f), radius * 0.7f, shadowPaint)
    }
}
