// =====================================================================
// WorldMapViewModel.kt
// Block Quest — World map state (level catalogue + progression + unlocks)
// =====================================================================

package com.blockquest.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockquest.domain.level.UnlockService
import com.blockquest.domain.level.UnlockState
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.ProgressionState
import com.blockquest.domain.model.WorldDefinition
import com.blockquest.domain.model.WorldState
import com.blockquest.domain.repository.AnalyticsRepository
import com.blockquest.domain.repository.LevelRepository
import com.blockquest.domain.repository.ProgressionRepository
import com.blockquest.domain.repository.WorldRepository
import com.blockquest.domain.usecase.ObserveAllLevelsUseCase
import com.blockquest.domain.usecase.ObserveWorldsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorldMapUiState(
    val levels: List<LevelSpec> = emptyList(),
    val worlds: List<WorldDefinition> = emptyList(),
    val worldStates: List<WorldState> = emptyList(),
    val progression: ProgressionState? = null,
    val unlockState: UnlockState? = null,
    val pendingWorldUnlock: WorldDefinition? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class WorldMapViewModel @Inject constructor(
    observeLevels: ObserveAllLevelsUseCase,
    observeWorlds: ObserveWorldsUseCase,
    private val progressionRepo: ProgressionRepository,
    private val worldsRepo: WorldRepository,
    private val unlockService: UnlockService,
    private val analytics: AnalyticsRepository,
) : ViewModel() {

    private val _pendingWorld = MutableStateFlow<WorldDefinition?>(null)
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val ui: StateFlow<WorldMapUiState> = retryTrigger.flatMapLatest {
        combine(
            observeLevels(),
            observeWorlds(),
            progressionRepo.observeProgression(),
            _pendingWorld,
        ) { levels, worlds, prog, pendingWorld ->
            val unlocks = unlockService.evaluate(worlds, levels, prog)
            val worldStates = worlds.map { w ->
                val levelsInWorld = levels.filter { it.worldIndex == w.worldIndex }
                val totalLevels = levelsInWorld.size
                val completedLevels = levelsInWorld.count {
                    prog.results[it.levelId]?.completed == true
                }
                val totalStars = levelsInWorld.sumOf {
                    prog.results[it.levelId]?.stars ?: 0
                }
                val maxStars = totalLevels * 3
                WorldState(
                    definition = w,
                    isUnlocked = w.worldIndex in unlocks.unlockedWorlds,
                    completedLevels = completedLevels,
                    totalLevels = totalLevels,
                    totalStars = totalStars,
                    maxStars = maxStars,
                )
            }
            WorldMapUiState(
                levels = levels,
                worlds = worlds,
                worldStates = worldStates,
                progression = prog,
                unlockState = unlocks,
                pendingWorldUnlock = pendingWorld,
                isLoading = false,
            )
        }.catch { e ->
            emit(WorldMapUiState(isLoading = false, errorMessage = e.message ?: "Error de red"))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = WorldMapUiState(),
    )

    fun retry() {
        retryTrigger.value += 1
    }

    /**
     * Mark the world as known-unlocked. Called from the
     * "World unlocked!" dialog when the user dismisses it.
     */
    fun acknowledgeWorldUnlock() {
        val w = _pendingWorld.value ?: return
        viewModelScope.launch {
            progressionRepo.unlockWorld(w.worldIndex, method = "auto")
            analytics.logEvent("world_unlock_acknowledged", mapOf("world_index" to w.worldIndex))
            _pendingWorld.value = null
        }
    }

    fun showWorldUnlockIfAny(world: WorldDefinition) {
        _pendingWorld.value = world
    }

    fun selectLevel(level: LevelSpec, onSelected: (String) -> Unit) {
        viewModelScope.launch {
            analytics.logEvent(
                "level_select",
                mapOf("level_id" to level.levelId, "world_index" to level.worldIndex),
            )
            onSelected(level.levelId)
        }
    }
}
