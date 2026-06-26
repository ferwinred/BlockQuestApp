// =====================================================================
// GameplayViewModel.kt
// Block Quest — Gameplay VM (drives the engine + missions + UI state)
// =====================================================================

package com.blockquest.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.MissionEvent
import com.blockquest.domain.model.MissionProgress
import com.blockquest.domain.repository.AdPlacement
import com.blockquest.domain.repository.AdResult
import com.blockquest.domain.repository.AnalyticsRepository
import com.blockquest.domain.repository.PlayerRepository
import com.blockquest.domain.usecase.CompleteLevelUseCase
import com.blockquest.domain.usecase.GetLevelUseCase
import com.blockquest.domain.usecase.GameEvent
import com.blockquest.domain.usecase.GameState
import com.blockquest.domain.usecase.GameplayEngine
import com.blockquest.domain.usecase.ObserveCosmeticsUseCase
import com.blockquest.domain.usecase.PlacementResult
import com.blockquest.domain.usecase.PreloadRewardedAdUseCase
import com.blockquest.domain.usecase.ProcessMissionEventUseCase
import com.blockquest.domain.usecase.ShowRewardedAdUseCase
import com.blockquest.domain.scoring.LevelRewardService
import com.blockquest.presentation.designsystem.Palettes
import com.blockquest.presentation.designsystem.SemanticColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed class GameplayOverlay {
    data object None : GameplayOverlay()
    data class LevelComplete(
        val stars: Int,
        val score: Int,
        val rewardCoins: Int,
        val rewardGems: Int,
        val isFirstClear: Boolean,
        val newlyCompletedMissions: List<MissionProgress>,
    ) : GameplayOverlay()
    data class GameOver(
        val reason: String,
        val finalScore: Int,
        val canContinue: Boolean,
    ) : GameplayOverlay()
    data class MissionCompleted(val mission: MissionProgress) : GameplayOverlay()
}

data class GameplayUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val lastResult: PlacementResult? = null,
    val pendingOverlay: GameplayOverlay = GameplayOverlay.None,
    val pendingMissionCompletions: List<MissionProgress> = emptyList(),
)

@HiltViewModel
class GameplayViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val getLevel: GetLevelUseCase,
    private val completeLevel: CompleteLevelUseCase,
    private val analytics: AnalyticsRepository,
    private val players: PlayerRepository,
    private val progression: com.blockquest.domain.repository.ProgressionRepository,
    private val processMissionEvent: ProcessMissionEventUseCase,
    private val showRewardedAd: ShowRewardedAdUseCase,
    private val preloadRewardedAd: PreloadRewardedAdUseCase,
    private val observeCosmetics: ObserveCosmeticsUseCase,
    private val sound: com.blockquest.audio.SoundManager,
    private val haptic: com.blockquest.audio.HapticManager,
    val engine: GameplayEngine,
) : ViewModel() {

    private val levelId: String =
        savedState.get<String>("levelId") ?: error("missing levelId arg")

    private val _ui = MutableStateFlow(GameplayUiState())
    val ui: StateFlow<GameplayUiState> = _ui

    val state: StateFlow<GameState> = engine.state

    val events: SharedFlow<GameEvent> = engine.events

    val palette: StateFlow<SemanticColors> = observeCosmetics().map { state ->
        Palettes.forTheme(state.inventory.activeSkinId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Palettes.GrassLand,
    )

    val activeTitleId: StateFlow<String?> = observeCosmetics().map {
        it.inventory.activeTitleId
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val _isAdInProgress = MutableStateFlow(false)
    val isAdInProgress: StateFlow<Boolean> = _isAdInProgress

    private val _lastStats = MutableStateFlow<PlacementResult.Accepted?>(null)
    val lastStats: StateFlow<PlacementResult.Accepted?> = _lastStats

    init {
        // 1. Preload rewarded ad concurrently
        viewModelScope.launch {
            preloadRewardedAd(AdPlacement.ContinueAfterGameOver)
        }

        // 2. Start collecting events BEFORE the level starts to avoid missing events
        viewModelScope.launch {
            engine.events.collect { event ->
                when (event) {
                    is GameEvent.PiecePlaced -> {
                        sound.play(com.blockquest.audio.Sfx.PIECE_PLACE)
                        haptic.vibrate(com.blockquest.audio.HapticPattern.PIECE_PLACE)
                    }
                    is GameEvent.LinesCleared -> {
                        val total = event.rows.size + event.columns.size + event.squares3x3.size
                        if (total > 0) {
                            sound.play(com.blockquest.audio.Sfx.LINE_CLEAR)
                            haptic.vibrate(com.blockquest.audio.HapticPattern.LINE_CLEAR)
                        }
                    }
                    is GameEvent.ComboActivated -> {
                        sound.play(com.blockquest.audio.Sfx.COMBO)
                        haptic.vibrate(com.blockquest.audio.HapticPattern.COMBO)
                    }
                    is GameEvent.StreakUpdated -> {
                        if (event.level >= 3) {
                            sound.play(com.blockquest.audio.Sfx.STREAK_HIGH)
                        }
                    }
                    is GameEvent.LevelFailed -> {
                        sound.play(com.blockquest.audio.Sfx.GAME_OVER)
                        haptic.vibrate(com.blockquest.audio.HapticPattern.GAME_OVER)
                    }
                    is GameEvent.LevelCompleted -> {
                        sound.play(com.blockquest.audio.Sfx.LEVEL_COMPLETE)
                        haptic.vibrate(com.blockquest.audio.HapticPattern.LEVEL_COMPLETE)
                    }
                    is GameEvent.HeatUnlocked -> {
                        sound.play(com.blockquest.audio.Sfx.HEAT_UNLOCK)
                        haptic.vibrate(com.blockquest.audio.HapticPattern.HEAT_UNLOCK)
                    }
                    else -> Unit
                }
            }
        }

        // 3. Fetch level, start it, and THEN begin the game tick loop
        viewModelScope.launch {
            runCatching {
                val level = getLevel(levelId) ?: error("Level $levelId not found")
                engine.startLevel(level, attempt = 1)
                analytics.logEvent("level_start", mapOf("level_id" to levelId))
            }.onFailure { t ->
                _ui.value = _ui.value.copy(
                    isLoading = false,
                    errorMessage = t.message,
                )
            }.onSuccess {
                _ui.value = _ui.value.copy(isLoading = false)

                // Start game tick loop
                var lastMs = System.currentTimeMillis()
                while (true) {
                    val now = System.currentTimeMillis()
                    val delta = now - lastMs
                    if (delta > 0) {
                        tick(delta)
                    }
                    lastMs = now
                    kotlinx.coroutines.delay(16) // ~60 FPS
                }
            }
        }
    }

    fun place(trayIndex: Int, col: Int, row: Int) {
        val origin = Cell(col, row)
        val result = engine.place(trayIndex, origin)
        _ui.value = _ui.value.copy(lastResult = result)
        when (result) {
            is PlacementResult.Accepted -> {
                _lastStats.value = result
                onAccepted(result)
            }
            is PlacementResult.Completed -> {
                onCompleted(result)
            }
            is PlacementResult.GameOver -> {
                onGameOver(result)
            }
            PlacementResult.Rejected -> { /* nothing */ }
        }
    }

    fun undo() = engine.undo()
    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun tick(deltaMs: Long) = engine.tick(deltaMs)

    fun continueWithAd() {
        viewModelScope.launch {
            _isAdInProgress.value = true
            val result = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                showRewardedAd(AdPlacement.ContinueAfterGameOver) { amount ->
                    engine.continueWithExtraPieces(amount)
                    _ui.value = _ui.value.copy(pendingOverlay = GameplayOverlay.None)
                }
            } ?: AdResult.Failed("timeout")
            _isAdInProgress.value = false
            if (result is AdResult.Failed) {
                analytics.logEvent(
                    "ad_continue_failed",
                    mapOf("reason" to result.reason),
                )
                // Fallback a gems propuesto por el Roadmap
                // Idealmente la UI intercepta el error, pero podemos actualizar el overlay
                // para que el jugador intente con gemas
            }
        }
    }

    fun continueWithGems(costGems: Int = 50) {
        viewModelScope.launch {
            val ok = players.trySpendGems(costGems, "revive")
            if (ok) {
                engine.revive()
                _ui.value = _ui.value.copy(pendingOverlay = GameplayOverlay.None)
                analytics.logEvent("revive_gems", mapOf("cost" to costGems))
            }
        }
    }

    fun restartLevel() = engine.restartLevel()
    fun dismissOverlay() {
        _ui.value = _ui.value.copy(pendingOverlay = GameplayOverlay.None)
    }

    private fun onAccepted(result: PlacementResult.Accepted) {
        val s = engine.state.value
        val pieceCellCount = result.filledCells.size
        viewModelScope.launch {
            processMissionEvent(MissionEvent.PiecePlaced(cellCount = pieceCellCount))
            if (result.clearedRows.isNotEmpty() || result.clearedColumns.isNotEmpty()) {
                processMissionEvent(
                    MissionEvent.LinesCleared(
                        rowCount = result.clearedRows.size,
                        columnCount = result.clearedColumns.size,
                    )
                )
            }
            if (result.clearedSquares3x3.isNotEmpty()) {
                processMissionEvent(MissionEvent.SquaresCleared(count = result.clearedSquares3x3.size))
            }
            if (result.combo.ordinal > 0) {
                processMissionEvent(MissionEvent.ComboAchieved(result.combo))
            }
            processMissionEvent(MissionEvent.StreakAchieved(s.streak))
            processMissionEvent(MissionEvent.ScoreReached(s.score))
            if (result.totalCleared >= 5) {
                processMissionEvent(MissionEvent.BigClear(result.totalCleared))
            }
            if (result.clearedSquares3x3.isNotEmpty() &&
                (result.clearedRows.isNotEmpty() || result.clearedColumns.isNotEmpty())
            ) {
                processMissionEvent(
                    MissionEvent.PerfectClear(
                        rows = result.clearedRows.size,
                        cols = result.clearedColumns.size,
                        squares = result.clearedSquares3x3.size,
                    )
                )
            }
            if (result.additionalCleared.isNotEmpty()) {
                processMissionEvent(MissionEvent.SpecialCellsCleared(count = result.additionalCleared.size))
            }
        }
    }

    private fun onCompleted(result: PlacementResult.Completed) {
        val level = engine.state.value.level ?: return
        viewModelScope.launch {
            val existingResult = withTimeoutOrNull(3_000L) {
                progression.observeProgression().firstOrNull()
            }
            val isFirstClear = existingResult?.getResult(levelId)
                ?.let { it.completed } != true
            val reward = LevelRewardService.rewardsFor(
                level = level,
                stars = result.stars,
                isFirstClear = isFirstClear,
            )
            completeLevel(
                levelId = level.levelId,
                finalScore = result.finalScore,
                stars = result.stars,
                rewardCoins = reward.coins,
                rewardGems = reward.gems,
            )
            val newMissions = processMissionEvent(
                MissionEvent.LevelCompleted(
                    worldIndex = level.worldIndex,
                    score = result.finalScore,
                    stars = result.stars,
                )
            )
            _ui.value = _ui.value.copy(
                pendingOverlay = GameplayOverlay.LevelComplete(
                    stars = result.stars,
                    score = result.finalScore,
                    rewardCoins = reward.coins,
                    rewardGems = reward.gems,
                    isFirstClear = isFirstClear,
                    newlyCompletedMissions = newMissions,
                ),
                pendingMissionCompletions = newMissions,
            )
        }
    }

    private fun onGameOver(result: PlacementResult.GameOver) {
        val level = engine.state.value.level
        if (level != null) {
            viewModelScope.launch {
                processMissionEvent(
                    MissionEvent.LevelFailed(
                        worldIndex = level.worldIndex,
                        finalScore = result.finalScore,
                    )
                )
            }
        }
        _ui.value = _ui.value.copy(
            pendingOverlay = GameplayOverlay.GameOver(
                reason = result.reason,
                finalScore = result.finalScore,
                canContinue = result.canContinue,
            )
        )
    }
}
