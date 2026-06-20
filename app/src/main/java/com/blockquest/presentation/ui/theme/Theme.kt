// =====================================================================
// Theme.kt
// Block Quest — Material 3 theme
// =====================================================================

package com.blockquest.presentation.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.blockquest.presentation.designsystem.Palettes
import com.blockquest.presentation.designsystem.SemanticColors

/**
 * CompositionLocal that exposes the semantic color palette
 * to the composable tree. Every Composable that needs a
 * game-specific color (cell tint, tray background, ghost)
 * reads from this local. The linter enforces that no
 * Composable reads `MaterialTheme.colorScheme` for these
 * — it must come from the palette.
 */
val LocalSemanticColors = staticCompositionLocalOf { Palettes.GrassLand }

private val GrassGreen = Color(0xFF6BCB77)
private val ForestEmerald = Color(0xFF2D5A3D)
private val SandGold = Color(0xFFFFE4A0)
private val SpaceDeep = Color(0xFF1E1B4B)

private val LightColors = lightColorScheme(
    primary = GrassGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5DC),
    onPrimaryContainer = ForestEmerald,
    secondary = SandGold,
    onSecondary = Color(0xFF3A2E00),
    tertiary = SpaceDeep,
    background = Color(0xFFFAFCFA),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFF6F8F6),
    onSurface = Color(0xFF1A1C1A),
)

private val DarkColors = darkColorScheme(
    primary = GrassGreen,
    onPrimary = Color(0xFF003912),
    primaryContainer = Color(0xFF1F5A2D),
    onPrimaryContainer = Color(0xFFB7F0BF),
    secondary = SandGold,
    onSecondary = Color(0xFF3A2E00),
    tertiary = Color(0xFFB3B0F0),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE2E3DF),
    surface = Color(0xFF1A1F1B),
    onSurface = Color(0xFFE2E3DF),
)

@Composable
fun BlockQuestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    palette: SemanticColors = Palettes.GrassLand,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx)
            else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalSemanticColors provides palette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
