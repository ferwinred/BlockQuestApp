// =====================================================================
// SemanticColors.kt
// Block Quest — Design system: semantic color tokens
// =====================================================================
//
// The design system defines color tokens that map to specific
// UI uses (not raw color values). The linter forbids any
// `Color(0x...)` literal in a Composable — every color must
// come from `SemanticColors` or `MaterialTheme.colorScheme`.
//
// Theme variants are stored as `SkinPalette` and applied
// via the cosmetics system. The default palette is
// `Pradera`. Switching to `Bosque` (or any other) swaps
// the entire palette in one place.
// =====================================================================

package com.blockquest.presentation.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import com.blockquest.domain.model.Skin

/**
 * Semantic color tokens. These names describe what the color
 * is *for*, not what it looks like — that's the point.
 *
 * Bind to a palette via `BlockQuestTheme(colors = Pradera)`.
 */
data class SemanticColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    // Game-specific.
    val cellEmpty: Color,
    val cellOccupied: Color,
    val cellCrystal: Color,
    val cellHeatLocked: Color,
    val cellBlackHole: Color,
    val trayBackground: Color,
    val ghostValid: Color,
    val ghostInvalid: Color,
    val comboGlow: Color,
) {
    fun colorFilterForPrimary(): ColorFilter = ColorFilter.tint(primary)
}

/**
 * Palette presets, one per skin. The `themeName` on a
 * `Skin` matches a key in this object.
 */
object Palettes {
    val GrassLand = SemanticColors(
        primary = Color(0xFF6BCB77),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6F5DC),
        onPrimaryContainer = Color(0xFF1F5A2D),
        secondary = Color(0xFFFFE4A0),
        onSecondary = Color(0xFF3A2E00),
        tertiary = Color(0xFF1E1B4B),
        onTertiary = Color.White,
        background = Color(0xFFFAFCFA),
        onBackground = Color(0xFF1A1C1A),
        surface = Color(0xFFF6F8F6),
        onSurface = Color(0xFF1A1C1A),
        error = Color(0xFFD64545),
        success = Color(0xFF6BCB77),
        warning = Color(0xFFFFB347),
        cellEmpty = Color(0xFFE0E5E0),
        cellOccupied = Color(0xFF6BCB77),
        cellCrystal = Color(0xFF9D7EE8),
        cellHeatLocked = Color(0xFFFFB347),
        cellBlackHole = Color(0xFF1E1B4B),
        trayBackground = Color(0xFFEEF3EE),
        ghostValid = Color(0xFF6BCB77),
        ghostInvalid = Color(0xFFD64545),
        comboGlow = Color(0xFFFFE4A0),
    )

    val Forest = SemanticColors(
        primary = Color(0xFF2D5A3D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF1F5A2D),
        onPrimaryContainer = Color(0xFFB7F0BF),
        secondary = Color(0xFF8FBC8F),
        onSecondary = Color(0xFF1F2D1F),
        tertiary = Color(0xFFFFB347),
        onTertiary = Color(0xFF1F1500),
        background = Color(0xFF0F1F12),
        onBackground = Color(0xFFD6E5D6),
        surface = Color(0xFF1A2D1F),
        onSurface = Color(0xFFD6E5D6),
        error = Color(0xFFE85A4F),
        success = Color(0xFF8FBC8F),
        warning = Color(0xFFFFB347),
        cellEmpty = Color(0xFF2D3D2D),
        cellOccupied = Color(0xFF2D5A3D),
        cellCrystal = Color(0xFFB7F0BF),
        cellHeatLocked = Color(0xFFFFB347),
        cellBlackHole = Color(0xFF0F1F12),
        trayBackground = Color(0xFF1F2D1F),
        ghostValid = Color(0xFF8FBC8F),
        ghostInvalid = Color(0xFFE85A4F),
        comboGlow = Color(0xFFB7F0BF),
    )

    val Desert = SemanticColors(
        primary = Color(0xFFE89B3C),
        onPrimary = Color(0xFF1F1500),
        primaryContainer = Color(0xFFFFD9A0),
        onPrimaryContainer = Color(0xFF3A2E00),
        secondary = Color(0xFFC19A4A),
        onSecondary = Color.White,
        tertiary = Color(0xFFD64545),
        onTertiary = Color.White,
        background = Color(0xFFFAF3E0),
        onBackground = Color(0xFF3A2E00),
        surface = Color(0xFFFFF1D6),
        onSurface = Color(0xFF3A2E00),
        error = Color(0xFFD64545),
        success = Color(0xFFE89B3C),
        warning = Color(0xFFFF8C42),
        cellEmpty = Color(0xFFEDDFC4),
        cellOccupied = Color(0xFFE89B3C),
        cellCrystal = Color(0xFFC19A4A),
        cellHeatLocked = Color(0xFFFF8C42),
        cellBlackHole = Color(0xFF2D1F0F),
        trayBackground = Color(0xFFF5E6C4),
        ghostValid = Color(0xFFE89B3C),
        ghostInvalid = Color(0xFFD64545),
        comboGlow = Color(0xFFFFD9A0),
    )

    val Spacious = SemanticColors(
        primary = Color(0xFF6E7BD9),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF1E1B4B),
        onPrimaryContainer = Color(0xFFB3B0F0),
        secondary = Color(0xFF9D7EE8),
        onSecondary = Color.White,
        tertiary = Color(0xFF00D9D9),
        onTertiary = Color(0xFF001F1F),
        background = Color(0xFF0A0A1E),
        onBackground = Color(0xFFB3B0F0),
        surface = Color(0xFF14142B),
        onSurface = Color(0xFFB3B0F0),
        error = Color(0xFFE85A8F),
        success = Color(0xFF00D9D9),
        warning = Color(0xFFFFB347),
        cellEmpty = Color(0xFF1F1F3D),
        cellOccupied = Color(0xFF6E7BD9),
        cellCrystal = Color(0xFF9D7EE8),
        cellHeatLocked = Color(0xFFFFB347),
        cellBlackHole = Color(0xFF000010),
        trayBackground = Color(0xFF14142B),
        ghostValid = Color(0xFF6E7BD9),
        ghostInvalid = Color(0xFFE85A8F),
        comboGlow = Color(0xFF00D9D9),
    )

    val Final = SemanticColors(
        primary = Color(0xFFD4AF37),
        onPrimary = Color(0xFF1F1500),
        primaryContainer = Color(0xFF3A2D00),
        onPrimaryContainer = Color(0xFFFFE4A0),
        secondary = Color(0xFF8B0000),
        onSecondary = Color.White,
        tertiary = Color(0xFF4B0082),
        onTertiary = Color.White,
        background = Color(0xFF1A0A0A),
        onBackground = Color(0xFFFFE4A0),
        surface = Color(0xFF2D1515),
        onSurface = Color(0xFFFFE4A0),
        error = Color(0xFFE85A4F),
        success = Color(0xFFD4AF37),
        warning = Color(0xFFFF8C42),
        cellEmpty = Color(0xFF3A2D2D),
        cellOccupied = Color(0xFFD4AF37),
        cellCrystal = Color(0xFF8B0000),
        cellHeatLocked = Color(0xFFFF8C42),
        cellBlackHole = Color(0xFF000000),
        trayBackground = Color(0xFF2D1515),
        ghostValid = Color(0xFFD4AF37),
        ghostInvalid = Color(0xFFE85A4F),
        comboGlow = Color(0xFFFFE4A0),
    )

    val Lucky = SemanticColors(
        primary = Color(0xFFFF6B9D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD6E0),
        onPrimaryContainer = Color(0xFF8B0033),
        secondary = Color(0xFFFFB347),
        onSecondary = Color(0xFF3A2E00),
        tertiary = Color(0xFF9D7EE8),
        onTertiary = Color.White,
        background = Color(0xFFFFF8FA),
        onBackground = Color(0xFF3A1A2E),
        surface = Color(0xFFFFEBF0),
        onSurface = Color(0xFF3A1A2E),
        error = Color(0xFFD64545),
        success = Color(0xFFFF6B9D),
        warning = Color(0xFFFFB347),
        cellEmpty = Color(0xFFFFD6E0),
        cellOccupied = Color(0xFFFF6B9D),
        cellCrystal = Color(0xFF9D7EE8),
        cellHeatLocked = Color(0xFFFFB347),
        cellBlackHole = Color(0xFF3A1A2E),
        trayBackground = Color(0xFFFFEBF0),
        ghostValid = Color(0xFFFF6B9D),
        ghostInvalid = Color(0xFFD64545),
        comboGlow = Color(0xFFFFB347),
    )

    val Neon = SemanticColors(
        primary = Color(0xFFFF00FF),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF2D002D),
        onPrimaryContainer = Color(0xFFFF99FF),
        secondary = Color(0xFF00FFFF),
        onSecondary = Color.Black,
        tertiary = Color(0xFFFFFF00),
        onTertiary = Color.Black,
        background = Color(0xFF0A0A14),
        onBackground = Color(0xFF00FFFF),
        surface = Color(0xFF14142B),
        onSurface = Color(0xFF00FFFF),
        error = Color(0xFFFF0080),
        success = Color(0xFF00FF80),
        warning = Color(0xFFFF8000),
        cellEmpty = Color(0xFF1F1F3D),
        cellOccupied = Color(0xFFFF00FF),
        cellCrystal = Color(0xFF00FFFF),
        cellHeatLocked = Color(0xFFFFFF00),
        cellBlackHole = Color(0xFF000000),
        trayBackground = Color(0xFF14142B),
        ghostValid = Color(0xFFFF00FF),
        ghostInvalid = Color(0xFFFF0080),
        comboGlow = Color(0xFFFFFF00),
    )

    fun forTheme(themeName: String): SemanticColors = when (themeName) {
        "grassland" -> GrassLand
        "forest" -> Forest
        "desert" -> Desert
        "spacious" -> Spacious
        "final" -> Final
        "lucky" -> Lucky
        "neon" -> Neon
        else -> GrassLand
    }
}

/**
 * Convenience: pick a palette for a given active skin.
 */
fun paletteForSkin(skin: Skin): SemanticColors = Palettes.forTheme(skin.themeName)
