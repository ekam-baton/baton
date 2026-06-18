package com.ekam.baton.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// ─── BATON Palette ──────────────────────────────────────────────────────────

// Core brand colors
val BatonNavy         = Color(0xFF0A0E1A) // deep navy, almost black
val BatonSlate        = Color(0xFF1C2333) // dark slate
val BatonElectric     = Color(0xFF3D8EFF) // electric blue accent
val BatonBackground   = Color(0xFF070B14) // near-black canvas
val BatonSurface      = Color(0xFF0F1623) // card / bottom sheet surface
val BatonSurfaceVar   = Color(0xFF1A2235) // elevated surface variant
val BatonError        = Color(0xFFFF453A) // iOS-style red

// Derived / semantic colors
val BatonOnPrimary    = Color(0xFFFFFFFF)
val BatonOnSecondary  = Color(0xFFB0BEC5)
val BatonOnBackground = Color(0xFFE8EAF6)
val BatonOnSurface    = Color(0xFFCFD8DC)
val BatonOnError      = Color(0xFFFFFFFF)
val BatonOutline      = Color(0xFF2A3448)

// Light-theme overrides (minimal — BATON defaults to dark)
val BatonLightPrimary    = Color(0xFF1565C0)
val BatonLightBackground = Color(0xFFF5F7FF)
val BatonLightSurface    = Color(0xFFFFFFFF)

// ─── Color Schemes ───────────────────────────────────────────────────────────

private val BatonDarkColorScheme = darkColorScheme(
    primary            = BatonNavy,
    onPrimary          = BatonOnPrimary,
    primaryContainer   = BatonSlate,
    onPrimaryContainer = BatonOnBackground,
    secondary          = BatonSlate,
    onSecondary        = BatonOnSecondary,
    secondaryContainer = BatonSurfaceVar,
    onSecondaryContainer = BatonOnSurface,
    tertiary           = BatonElectric,
    onTertiary         = BatonOnPrimary,
    tertiaryContainer  = Color(0xFF1A3560),
    onTertiaryContainer = BatonElectric,
    background         = BatonBackground,
    onBackground       = BatonOnBackground,
    surface            = BatonSurface,
    onSurface          = BatonOnSurface,
    surfaceVariant     = BatonSurfaceVar,
    onSurfaceVariant   = BatonOnSecondary,
    error              = BatonError,
    onError            = BatonOnError,
    errorContainer     = Color(0xFF3B1515),
    onErrorContainer   = BatonError,
    outline            = BatonOutline,
    outlineVariant     = Color(0xFF1E2D45),
    scrim              = Color(0xFF000000),
    inverseSurface     = BatonOnBackground,
    inverseOnSurface   = BatonBackground,
    inversePrimary     = BatonElectric,
)

private val BatonLightColorScheme = lightColorScheme(
    primary            = BatonLightPrimary,
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary          = Color(0xFF455A87),
    onSecondary        = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2FF),
    onSecondaryContainer = Color(0xFF001944),
    tertiary           = Color(0xFF1565C0),
    onTertiary         = Color(0xFFFFFFFF),
    background         = BatonLightBackground,
    onBackground       = Color(0xFF1A1C24),
    surface            = BatonLightSurface,
    onSurface          = Color(0xFF1A1C24),
    surfaceVariant     = Color(0xFFE1E5F4),
    onSurfaceVariant   = Color(0xFF43475A),
    error              = Color(0xFFBA1A1A),
    onError            = Color(0xFFFFFFFF),
    outline            = Color(0xFF73778A),
)

// ─── Theme Composable ────────────────────────────────────────────────────────

/**
 * BATON design-system theme wrapper.
 *
 * Defaults to [darkTheme] = `true` to honour the sovereign, terminal-aesthetic
 * brand identity. Pass `darkTheme = false` to opt into the light variant.
 *
 * Usage:
 * ```kotlin
 * BatonTheme {
 *     // your Compose content
 * }
 * ```
 */
@Composable
fun BatonTheme(
    darkTheme: Boolean = true,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val baseColorScheme = if (darkTheme) BatonDarkColorScheme else BatonLightColorScheme
    val colorScheme = if (accentColor != null) {
        baseColorScheme.copy(
            tertiary = accentColor,
            onTertiary = Color.White
        )
    } else {
        baseColorScheme
    }

    CompositionLocalProvider(
        LocalBatonSpacing provides BatonSpacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = BatonTypography,
            shapes      = BatonShapes,
            content     = content
        )
    }
}
