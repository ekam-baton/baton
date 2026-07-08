package com.ekam.baton.feature.agents.a2a.avatar

/**
 * The visual activity state of an avatar, driven by the agent's A2A session status.
 *
 * State → visual mapping (industry standard, per AgentOps / BrowserUse research):
 *  IDLE      → gray, slow breathing scale
 *  THINKING  → blue, orbiting thought dots
 *  ACTIVE    → emerald, arm swing + rising particles
 *  WAITING   → amber, dashed spinning ring
 *  ERROR     → red, red glow + shake
 *  SUCCESS   → green, particle burst then fade to IDLE
 */
enum class AvatarActivityState(val colorHex: String) {
    IDLE(     "#6B7280"),
    THINKING( "#3B82F6"),
    ACTIVE(   "#10B981"),
    WAITING(  "#F59E0B"),
    ERROR(    "#EF4444"),
    SUCCESS(  "#22C55E")
}
