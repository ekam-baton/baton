package com.ekam.baton.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ─── BATON Palette (Deep Sea Blue Theme) ────────────────────────────────────

// Core brand colors (Telegram Deep Sea Blue Theme)
val BatonBackground   = Color(0xFF0E1621) // Telegram dark slate background
val BatonSurface      = Color(0xFF17212B) // Telegram card/surface background
val BatonSurfaceVar   = Color(0xFF242F3D) // Telegram elevated surface
val BatonDeepSeaBlue  = Color(0xFF2A82B8) // Deep Sea Blue accent (Primary)
val BatonSentBubble   = Color(0xFF2B5278) // Deep Sea Blue for sent messages
val BatonNavy         = Color(0xFF070D14) // Deepest navy for contrast
val BatonError        = Color(0xFFE53935) // Red

// Derived / semantic colors
val BatonOnPrimary    = Color(0xFFFFFFFF)
val BatonOnSecondary  = Color(0xFF8B9BB4) // Telegram subtle grey/blue text
val BatonOnBackground = Color(0xFFF5F5F5)
val BatonOnSurface    = Color(0xFFDFE6EE) // Telegram main text color
val BatonOnError      = Color(0xFFFFFFFF)
val BatonOutline      = Color(0xFF2B3A4C) // Divider lines

// Light-theme overrides
val BatonLightPrimary    = BatonDeepSeaBlue
val BatonLightBackground = Color(0xFFFFFFFF)
val BatonLightSurface    = Color(0xFFF1F1F1)

// ─── Color Schemes ───────────────────────────────────────────────────────────

private val BatonDarkColorScheme = darkColorScheme(
    primary            = BatonDeepSeaBlue, 
    onPrimary          = BatonOnPrimary,
    primaryContainer   = BatonSurfaceVar,
    onPrimaryContainer = BatonOnBackground,
    secondary          = BatonSurfaceVar,
    onSecondary        = BatonOnSecondary,
    secondaryContainer = BatonSurface,
    onSecondaryContainer = BatonOnSurface,
    tertiary           = BatonSentBubble,
    onTertiary         = BatonOnPrimary,
    tertiaryContainer  = BatonSurfaceVar,
    onTertiaryContainer = BatonDeepSeaBlue,
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
    inversePrimary     = BatonDeepSeaBlue,
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
    tertiary           = BatonDeepSeaBlue,
    onTertiary         = Color(0xFFFFFFFF),
    background         = BatonLightBackground,
    onBackground       = Color(0xFF1A1C24),
    surface            = BatonLightSurface,
    onSurface          = Color(0xFF1A1C24),
    surfaceVariant     = Color(0xFFE1E5F4),
    onSurfaceVariant   = Color(0xFF43475A),
    error              = BatonError,
    onError            = Color(0xFFFFFFFF),
    outline            = Color(0xFF73778A),
)

// ─── Color Helper Extensions ─────────────────────────────────────────────────

/**
 * Checks whether this color is relatively light, based on standard luminance.
 */
fun Color.isLight(): Boolean {
    val luminance = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return luminance > 0.5f
}

// ─── Theme Composable ────────────────────────────────────────────────────────

/**
 * BATON design-system theme wrapper.
 *
 * Fully supports Material 3 Dynamic Color (Android 12+) so the app automatically 
 * shifts to match the user's wallpaper, while falling back to the brand aesthetics.
 */
@Composable
fun BatonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BatonDarkColorScheme
        else -> BatonLightColorScheme
    }
    val colorScheme = if (accentColor != null) {
        val highContrastText = if (accentColor.isLight()) {
            Color(0xFF070B14) // Dark charcoal text for light themes
        } else {
            Color.White // Pure white text for dark/vibrant themes
        }
        baseColorScheme.copy(
            primary = accentColor,
            onPrimary = highContrastText,
            tertiary = accentColor,
            onTertiary = highContrastText
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
