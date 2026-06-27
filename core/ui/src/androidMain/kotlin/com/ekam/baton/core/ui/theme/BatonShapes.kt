package com.ekam.baton.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * BATON shape scale.
 *
 * Defines the three primary corner radii used across the design system and maps
 * them onto [Shapes] so Material3 components pick them up automatically.
 *
 * | Role        | Radius | Usage                                           |
 * |-------------|--------|-------------------------------------------------|
 * | small       | 8 dp   | chips, tags, small buttons, input fields        |
 * | medium      | 12 dp  | cards, bottom-sheet handles, dialogs            |
 * | large       | 16 dp  | bottom sheets, expanded cards, modals           |
 * | extraLarge  | 24 dp  | FABs, hero containers (M3 extraLarge slot)      |
 */
val BatonShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
