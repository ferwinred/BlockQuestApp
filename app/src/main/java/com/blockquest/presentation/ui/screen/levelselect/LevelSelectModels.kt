// =====================================================================
// LevelSelectModels.kt
// Block Quest — UI models for the level-select screen (Semana 3)
// =====================================================================

package com.blockquest.presentation.ui.screen.levelselect

import com.blockquest.domain.model.LevelResult
import com.blockquest.domain.model.LevelSpec

/** One entry in the level grid. */
data class LevelSelectItem(
    val spec: LevelSpec,
    val result: LevelResult? = null,
    val isUnlocked: Boolean = false,
)

/** Full UI state for [LevelSelectScreen]. */
data class LevelSelectUiState(
    val worldName: String = "",
    val levels: List<LevelSelectItem> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val totalStars: Int = 0,
    val maxStars: Int = 0,
    val isLoading: Boolean = true,
)
