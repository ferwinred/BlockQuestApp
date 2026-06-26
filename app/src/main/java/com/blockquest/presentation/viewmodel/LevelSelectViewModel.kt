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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

import com.blockquest.domain.repository.LeaderboardEntry
import com.blockquest.domain.repository.LeaderboardRepository

@HiltViewModel
class LevelSelectViewModel @Inject constructor(
    private val observeLevels: ObserveAllLevelsUseCase,
    private val observeWorlds: ObserveWorldsUseCase,
    private val progressionRepo: ProgressionRepository,
    private val unlockService: UnlockService,
    private val leaderboardRepo: LeaderboardRepository,
    private val playerRepo: com.blockquest.domain.repository.PlayerRepository,
) : ViewModel() {

    /**
     * Returns a StateFlow of [LevelSelectUiState] scoped to [worldIndex].
     * Called once per screen entry; Hilt reuses the same ViewModel
     * instance while the screen is in the back-stack.
     */
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun ui(worldIndex: Int): StateFlow<LevelSelectUiState> = retryTrigger.flatMapLatest {
        combine(
            observeLevels(),
            observeWorlds(),
            progressionRepo.observeProgression(),
            playerRepo.observeCurrency(),
        ) { levels, worlds, progression, currency ->

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
                worldIndex     = worldIndex,
                levels         = items,
                completedCount = items.count { it.result?.completed == true },
                totalCount     = worldLevels.size,
                inventory      = currency.boosters,
                totalStars     = items.sumOf { it.result?.stars ?: 0 },
                maxStars       = worldLevels.size * 3,
                isLoading      = false,
            )
        }.catch { e ->
            emit(LevelSelectUiState(isLoading = false, errorMessage = e.message ?: "Error de red"))
        }
    }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LevelSelectUiState(),
    )

    fun observeTopScores(levelId: String): kotlinx.coroutines.flow.Flow<List<LeaderboardEntry>> {
        return leaderboardRepo.observeTopScores(levelId, limit = 10)
    }

    fun retry() {
        retryTrigger.value += 1
    }
}

