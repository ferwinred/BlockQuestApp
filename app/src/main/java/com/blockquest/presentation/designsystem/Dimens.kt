// =====================================================================
// Dimens.kt
// Block Quest — Design system: semantic dimensions
// =====================================================================
//
// All sizes in the UI should come from this object. Hardcoded
// `.dp` values are flagged by `tools/design-lint/` and fail
// CI.
//
// Conventions
// -----------
//   * Spacing scale is a 4pt grid: 0, 4, 8, 12, 16, 24, 32, 48, 64.
//   * Component sizes are named (e.g. `cell.size`, `button.height`).
//   * One-off values must be aliased here, not inlined.
// =====================================================================

package com.blockquest.presentation.designsystem

import androidx.compose.ui.unit.dp

/**
 * 4-point spacing grid. The linter enforces that spacing
 * values in `Modifier.padding(...)` come from this object.
 */
object Spacing {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val huge = 64.dp
}

/**
 * Corner radii.
 */
object Radius {
    val none = 0.dp
    val xs = 2.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 16.dp
    val pill = 999.dp
}

/**
 * Border widths.
 */
object BorderWidth {
    val none = 0.dp
    val thin = 1.dp
    val medium = 2.dp
    val thick = 3.dp
}

/**
 * Component-specific sizes. These are not on the 4pt grid
 * because they encode the visual identity of the game.
 */
object Sizing {
    val cell = 40.dp                // gameplay board cell on default board
    val cellPadding = 1.dp
    val tray = 96.dp                // height of the tray
    val trayPiece = 80.dp           // bounding box of a tray piece
    val button = 56.dp              // primary CTA button height
    val buttonSmall = 40.dp
    val iconSm = 16.dp
    val iconMd = 24.dp
    val iconLg = 32.dp
    val iconXl = 48.dp
    val avatar = 64.dp
    val topBar = 56.dp
    val bottomBar = 80.dp
    val skinThumb = 96.dp
    val titleRow = 64.dp
}

/**
 * Elevation tokens. Use these in `Card` / `Surface` rather
 * than raw `2.dp` etc.
 */
object Elevation {
    val none = 0.dp
    val xxs = 1.dp
    val xs = 2.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
}
