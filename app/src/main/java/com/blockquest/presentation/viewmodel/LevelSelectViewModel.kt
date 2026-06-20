// =====================================================================
// LevelSelectViewModel.kt
// Block Quest — State for the level-selection grid (Semana 3)
// =====================================================================

package com.blockquest.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockquest.domain.level.UnlockService
import com.blockquest.domain.repository.ProgressionRepository
import com.blockquest.domain.usecase.ObserveAllLevelsUseCase
import com.blockquest.domain.usecase.ObserveWorldsUseCase
import com.blockquest.presentation.ui.screen.levelselect.LevelSelectItem
import com.blockquest.presentation.ui.screen.levelselect.LevelSelectUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LevelSelectViewModel @Inject constructor(
    private val observeLevels: ObserveAllLevelsUseCase,
    private val observeWorlds: ObserveWorldsUseCase,
    private val progressionRepo: ProgressionRepository,
    private val unlockService: UnlockService,
) : ViewModel() {

    /**
     * Returns a StateFlow of [LevelSelectUiState] scoped to [worldIndex].
     * Called once per screen entry; Hilt reuses the same ViewModel
     * instance while the screen is in the back-stack.
     */
    fun ui(worldIndex: Int): StateFlow<LevelSelectUiState> = combine(
        observeLevels(),
        observeWorlds(),
        progressionRepo.observeProgression(),
    ) { levels, worlds, progression ->

        val world   = worlds.find { it.worldIndex == worldIndex }
        val unlocks = unlockService.evaluate(worlds, levels, progression)

        val worldLevels = levels
            .filter { it.worldIndex == worldIndex }
            .sortedBy { it.levelNumber }

        // A level is unlocked when the world is unlocked AND either
        // (a) it is the first level of the world, or
        // (b) the previous level has been completed.
        val completedIds = progression.results
            .filterValues { it.completed }
            .keys

        val items = worldLevels.mapIndexed { idx, spec ->
            val previousCompleted = idx == 0 ||
                    completedIds.contains(worldLevels[idx - 1].levelId)
            val worldUnlocked = worldIndex in unlocks.unlockedWorlds
            LevelSelectItem(
                spec       = spec,
                result     = progression.results[spec.levelId],
                isUnlocked = worldUnlocked && previousCompleted,
            )
        }

        LevelSelectUiState(
            worldName      = world?.displayName ?: "Mundo ${worldIndex + 1}",
            levels         = items,
            completedCount = items.count { it.result?.completed == true },
            totalCount     = worldLevels.size,
            totalStars     = items.sumOf { it.result?.stars ?: 0 },
            maxStars       = worldLevels.size * 3,
            isLoading      = false,
        )
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LevelSelectUiState(),
    )
}

