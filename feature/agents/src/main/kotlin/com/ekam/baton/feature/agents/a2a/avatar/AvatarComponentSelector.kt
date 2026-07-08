package com.ekam.baton.feature.agents.a2a.avatar

import androidx.compose.ui.graphics.Color
import com.ekam.baton.core.data.model.AgentRole
import kotlin.random.Random

/**
 * Component selection system for humanoid avatars.
 *
 * Uses a seeded PRNG (derived from the agent's ID) to deterministically select
 * visual components — so each agent always gets the same unique look without
 * any stored state beyond the agent ID itself. (Inspired by DiceBear's approach.)
 */

enum class HeadShape { ROUND, ANGULAR, SQUARE, WIDE }
enum class EyeStyle  { DOTS, ALMOND, SCAN_LINE, ROUND_GLOW }
enum class HairStyle { SHORT, TALL_SPIKY, WAVE, BALD }
enum class BodyBuild { SLIM, MEDIUM, BROAD }

data class AvatarComponents(
    val headShape: HeadShape,
    val eyeStyle: EyeStyle,
    val hairStyle: HairStyle,
    val skinTone: Color,
    val accentColor: Color,       // matches agent.colorAccent
    val bodyBuild: BodyBuild,
    val role: AgentRole
)

private val SKIN_TONES = listOf(
    Color(0xFFFDBCB4), // light peach
    Color(0xFFEEAB8A), // warm sand
    Color(0xFFD08B5B), // medium tan
    Color(0xFF9E6041), // dark caramel
    Color(0xFF594033), // deep brown
    Color(0xFFADBCB4), // cool grey (android)
)

/**
 * Produces a deterministic [AvatarComponents] from an agent ID.
 * Calling this twice with the same [agentId] always returns identical results.
 */
fun buildAvatarComponents(agentId: String, colorAccentHex: String, role: AgentRole): AvatarComponents {
    val seed = agentId.fold(0L) { acc, c -> acc * 31 + c.code }
    val rng = Random(seed)

    val accent = try {
        Color(android.graphics.Color.parseColor(colorAccentHex))
    } catch (_: Exception) {
        Color(0xFF3D8EFF)
    }

    return AvatarComponents(
        headShape   = HeadShape.values()[rng.nextInt(HeadShape.values().size)],
        eyeStyle    = EyeStyle.values()[rng.nextInt(EyeStyle.values().size)],
        hairStyle   = HairStyle.values()[rng.nextInt(HairStyle.values().size)],
        skinTone    = SKIN_TONES[rng.nextInt(SKIN_TONES.size)],
        accentColor = accent,
        bodyBuild   = BodyBuild.values()[rng.nextInt(BodyBuild.values().size)],
        role        = role
    )
}
