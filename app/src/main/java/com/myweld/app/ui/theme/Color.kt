package com.myweld.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MyWeld Premium Color System
 *
 * Dark mode: Industrial/metallic with neon accents
 * Light mode: Clean with subtle industrial hints
 */
object MyWeldColors {

    // ========================================================================
    // DARK MODE — Industrial Premium
    // ========================================================================

    // Backgrounds (deep, layered)
    val DarkBackground = Color(0xFF0D0D14)          // Near-black with blue tint
    val DarkSurface = Color(0xFF1A1A2E)             // Charcoal navy
    val DarkSurfaceVariant = Color(0xFF16213E)       // Deep navy
    val DarkSurfaceElevated = Color(0xFF1F2940)      // Slightly lighter

    // Primary — Neon Cyan (electric, technical feel)
    val DarkPrimary = Color(0xFF00D4FF)
    val DarkOnPrimary = Color(0xFF00232E)
    val DarkPrimaryContainer = Color(0xFF003544)
    val DarkOnPrimaryContainer = Color(0xFF7AE8FF)

    // Secondary — Ember Orange (warm accent for warnings/energy)
    val DarkSecondary = Color(0xFFFF6B35)
    val DarkOnSecondary = Color(0xFF2E1500)
    val DarkSecondaryContainer = Color(0xFF4A2200)
    val DarkOnSecondaryContainer = Color(0xFFFFB899)

    // Tertiary — Metallic Silver (subtle accents)
    val DarkTertiary = Color(0xFFC0C0C0)
    val DarkOnTertiary = Color(0xFF1A1A1A)
    val DarkTertiaryContainer = Color(0xFF3A3A3A)
    val DarkOnTertiaryContainer = Color(0xFFE0E0E0)

    // Text
    val DarkOnBackground = Color(0xFFE8E8EC)
    val DarkOnSurface = Color(0xFFE0E0E8)
    val DarkOnSurfaceVariant = Color(0xFF9498A8)

    // Outline
    val DarkOutline = Color(0xFF3A3E4E)
    val DarkOutlineVariant = Color(0xFF2A2E3E)

    // Error
    val DarkError = Color(0xFFFF5555)
    val DarkOnError = Color(0xFF3B0000)
    val DarkErrorContainer = Color(0xFF5C1010)
    val DarkOnErrorContainer = Color(0xFFFFB4AB)

    // ========================================================================
    // LIGHT MODE — Clean Industrial
    // ========================================================================

    val LightBackground = Color(0xFFF5F5F8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFEBEDF2)
    val LightSurfaceElevated = Color(0xFFF0F2F7)

    val LightPrimary = Color(0xFF0095B3)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFD0F0FF)
    val LightOnPrimaryContainer = Color(0xFF003543)

    val LightSecondary = Color(0xFFCC5528)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFFFE0D0)
    val LightOnSecondaryContainer = Color(0xFF3E1500)

    val LightTertiary = Color(0xFF545454)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFE8E8E8)
    val LightOnTertiaryContainer = Color(0xFF1A1A1A)

    val LightOnBackground = Color(0xFF1A1A1E)
    val LightOnSurface = Color(0xFF1C1C20)
    val LightOnSurfaceVariant = Color(0xFF5A5E6A)

    val LightOutline = Color(0xFFCDD0D8)
    val LightOutlineVariant = Color(0xFFE0E3EA)

    val LightError = Color(0xFFCC3333)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFE5E5)
    val LightOnErrorContainer = Color(0xFF5C1010)

    // ========================================================================
    // SEMANTIC COLORS — Shared across themes
    // ========================================================================

    // Voltage bar gradient stops
    val VoltageRed = Color(0xFFFF3333)
    val VoltageOrange = Color(0xFFFF9933)
    val VoltageYellow = Color(0xFFFFCC00)
    val VoltageGreen = Color(0xFF39FF14)

    // State indicator colors
    val StateIdle = Color(0xFF6B7080)
    val StateReady = Color(0xFF39FF14)
    val StateCharging = Color(0xFF00D4FF)
    val StateFiring = Color(0xFFFF6B35)
    val StateError = Color(0xFFFF3333)

    // Glow effects (used with alpha)
    val GlowCyan = Color(0xFF00D4FF)
    val GlowOrange = Color(0xFFFF6B35)
    val GlowGreen = Color(0xFF39FF14)
    val GlowRed = Color(0xFFFF3333)
}
