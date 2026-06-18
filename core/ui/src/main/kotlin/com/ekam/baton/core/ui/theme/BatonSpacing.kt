package com.ekam.baton.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * BATON spacing scale.
 *
 * Use these constants instead of raw [Dp] literals throughout the UI to keep
 * layouts consistent and easy to update globally.
 *
 * Exposed via [LocalBatonSpacing] for access inside Composables:
 * ```kotlin
 * val spacing = LocalBatonSpacing.current
 * Modifier.padding(horizontal = spacing.md, vertical = spacing.sm)
 * ```
 */
@Immutable
data class BatonSpacing(
    /** 4 dp — tight padding, icon insets, divider thickness */
    val xs: Dp = 4.dp,
    /** 8 dp — compact element spacing, small chips, dense lists */
    val sm: Dp = 8.dp,
    /** 16 dp — standard content padding, card inner margin */
    val md: Dp = 16.dp,
    /** 24 dp — section separation, FAB margin, bottom-sheet top padding */
    val lg: Dp = 24.dp,
    /** 32 dp — hero section spacing, large modal padding */
    val xl: Dp = 32.dp,
)

/**
 * CompositionLocal that provides the active [BatonSpacing] to the composition tree.
 * Provided by [BatonTheme]; throws if accessed outside of it.
 */
val LocalBatonSpacing = compositionLocalOf<BatonSpacing> {
    error("No BatonSpacing provided. Wrap your content in BatonTheme {}.")
}
