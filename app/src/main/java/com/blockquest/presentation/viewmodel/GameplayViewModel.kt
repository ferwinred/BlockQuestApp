// =====================================================================
// GameplayViewModel.kt
// Block Quest — Gameplay VM (drives the engine + missions + UI state)
// =====================================================================

package com.blockquest.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.CosmeticInventory
import com.blockquest.domain.model.MissionEvent
import com.blockquest.domain.model.MissionProgress
import com.blockquest.domain.model.PieceShape
import com.blockquest.domain.repository.AdPlacement
import com.blockquest.domain.repository.AdResult
import com.blockquest.domain.repository.AnalyticsRepository
import com.blockquest.domain.repository.CosmeticRepository
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
import kotlinx.coroutines.flow.combine
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

    val state: StateFlow<GameState> = engine.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = GameState(),
    )

    val events: SharedFlow<GameEvent> = engine.events

    /**
     * The active palette. Bound to the player's equipped
     * skin. The GameplayScreen wraps the content in
     * `BlockQuestTheme(palette = ...)`.
     */
    val palette: StateFlow<SemanticColors> = observeCosmetics().map { state ->
        Palettes.forTheme(state.inventory.activeSkinId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Palettes.GrassLand,
    )

    /**
     * The active title id. The presentation layer can
     * render this next to the player's name.
     */
    val activeTitleId: StateFlow<String?> = observeCosmetics().map {
        it.inventory.activeTitleId
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    /**
     * One-shot event for "I'm currently showing a rewarded
     * ad" so the UI can show a loading spinner / dim the
     * board.
     */
    private val _isAdInProgress = MutableStateFlow(false)
    val isAdInProgress: StateFlow<Boolean> = _isAdInProgress

    init {
        viewModelScope.launch {
            // 1. Preload the first rewarded ad so the player can
            // continue after game-over with zero latency.
            launch {
                preloadRewardedAd(AdPlacement.ContinueAfterGameOver)
            }

            // 2. Audio / haptic event collector
            // Runs for the lifetime of the ViewModel. Each engine event is
            // mapped to a sound effect and/or a haptic pattern. The collector
            // never blocks: SoundManager.play() and HapticManager.vibrate()
            // are both synchronous and lightweight.
            launch {
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
                            // Only play at "high" streak levels (≥ 3) to avoid audio spam.
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

            // 3. Start Level
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
            }
        }
    }

    /**
     * Memos: the engine's "last frame" stats. We snapshot
     * them at every accepted placement so the UI can show
     * "you cleared 2 lines" feedback.
     */
    private val _lastStats = MutableStateFlow<PlacementResult.Accepted?>(null)
    val lastStats: StateFlow<PlacementResult.Accepted?> = _lastStats





    /**
     * Place a piece from the tray at (col, row). Used by
     * both the tap-tap UI and the drag-and-drop UI.
     */
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

    /**
     * Continue after game-over by watching a rewarded ad.
     * The ad call is async; while the ad is on screen we
     * flip `isAdInProgress = true` so the UI can disable
     * the buttons. When the ad completes, the engine
     * gets 3 extra pieces + a shield; if the user
     * dismissed the ad, we just leave the game-over
     * overlay up.
     */
    fun continueWithAd() {
        viewModelScope.launch {
            _isAdInProgress.value = true
            val result = showRewardedAd(AdPlacement.ContinueAfterGameOver) { amount ->
                engine.continueWithExtraPieces(amount)
                _ui.value = _ui.value.copy(pendingOverlay = GameplayOverlay.None)
            }
            _isAdInProgress.value = false
            if (result is AdResult.Failed) {
                // Surface a soft error in the UI. The
                // presentation layer can show a snackbar
                // or fall back to the "spend 50 gems"
                // path.
                analytics.logEvent(
                    "ad_continue_failed",
                    mapOf("reason" to result.reason),
                )
            }
        }
    }

    /**
     * Continue by spending 50 gems (the "no-ad" path).
     */
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

    fun continueWithExtraPieces(count: Int = 3) = engine.continueWithExtraPieces(count)
    fun usePowerUp(powerUpId: String, col: Int, row: Int) =
        engine.usePowerUp(powerUpId, Cell(col, row))
    fun restartLevel() = engine.restartLevel()
    fun dismissOverlay() {
        _ui.value = _ui.value.copy(pendingOverlay = GameplayOverlay.None)
    }

    fun claimDailyReward() = viewModelScope.launch {
        // Delegated to the player repo via the daily reward modal.
        players.claimDailyReward()
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    private fun onAccepted(result: PlacementResult.Accepted) {
        // Build the mission event(s) and forward.
        val s = engine.state.value
        val pieceCellCount = result.filledCells.size
        val piece = s.tray.firstOrNull()?.let { _ ->
            // We don't have direct access to the piece that
            // was placed; the engine already mutated the
            // state, so we use the placed cell count and
            // infer the events from the result.
            pieceCellCount
        } ?: 0
        viewModelScope.launch {
            // 1. Piece placed.
            processMissionEvent(
                MissionEvent.PiecePlaced(cellCount = pieceCellCount)
            )
            // 2. Lines / squares cleared.
            if (result.clearedRows.isNotEmpty() || result.clearedColumns.isNotEmpty()) {
                processMissionEvent(
                    MissionEvent.LinesCleared(
                        rowCount = result.clearedRows.size,
                        columnCount = result.clearedColumns.size,
                    )
                )
            }
            if (result.clearedSquares3x3.isNotEmpty()) {
                processMissionEvent(
                    MissionEvent.SquaresCleared(count = result.clearedSquares3x3.size)
                )
            }
            // 3. Combo / streak.
            if (result.combo.ordinal > 0) {
                processMissionEvent(MissionEvent.ComboAchieved(result.combo))
            }
            processMissionEvent(MissionEvent.StreakAchieved(s.streak))
            processMissionEvent(MissionEvent.ScoreReached(s.score))
            // 4. Big clear / perfect clear.
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
            // 5. Special cells cleared.
            if (result.additionalCleared.isNotEmpty()) {
                processMissionEvent(
                    MissionEvent.SpecialCellsCleared(count = result.additionalCleared.size)
                )
            }
        }
    }

    private fun onCompleted(result: PlacementResult.Completed) {
        val level = engine.state.value.level ?: return
        viewModelScope.launch {
            // Compute the reward.
            // Determine whether this is the player's first successful clear of this level.
            // We query the progression snapshot BEFORE recording the result so that a
            // stars == 0 entry (or a missing entry) reliably means "never completed before".
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
            // Persist.
            completeLevel(
                levelId = level.levelId,
                finalScore = result.finalScore,
                stars = result.stars,
                rewardCoins = reward.coins,
                rewardGems = reward.gems,
            )
            // Mission event.
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
