// =====================================================================
// GameplayEngine.kt
// Block Quest — Pure-Kotlin gameplay engine (Clean Architecture, domain)
// =====================================================================
//
// v2 — events for missions, peak tracking (max combo, max
// streak, special cells cleared), better game-over flow
// (shield, continue, restart).
//
// The engine is the only place that mutates the board. Every
// command (place / undo / pause / resume / revive / continue /
// addExtraPieces) returns a `Result`-style value; every
// observable change emits a `GameEvent` on the events flow.
// =====================================================================

package com.blockquest.domain.usecase

import com.blockquest.domain.board.BoardValidator
import com.blockquest.domain.board.LineClearDetector
import com.blockquest.domain.board.SpecialCellProcessor
import com.blockquest.domain.model.BossConfig
import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.CellState
import com.blockquest.domain.model.ComboType
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.PieceShape
import com.blockquest.domain.piecepool.PiecePoolSelector
import com.blockquest.domain.scoring.DailyRewardService
import com.blockquest.domain.scoring.LevelRewardService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Clock

// -----------------------------------------------------------------
// State
// -----------------------------------------------------------------

data class GameState(
    val level: LevelSpec? = null,
    val board: BoardState = BoardState(),
    val tray: List<PieceShape> = emptyList(),
    val score: Int = 0,
    val streak: Int = 0,
    val streakMultiplier: Float = 1f,
    val timeRemainingMs: Long = 0L,
    val isPaused: Boolean = false,
    val isReviveUsed: Boolean = false,
    val movesRemainingOnShield: Int = 0,
    val attemptNumber: Int = 0,
    val piecesPlacedThisLevel: Int = 0,
    val maxCombo: ComboType = ComboType.Single,
    val maxStreak: Int = 0,
    val specialCellsCleared: Int = 0,
    val totalLinesCleared: Int = 0,
    val totalSquaresCleared: Int = 0,
    val extraPiecesFromContinue: Int = 0,
    val rngSeed: Long = 0L,
) {
    /** Convenience for the UI. */
    val trayIndices: List<Int> get() = tray.indices.toList()
}

// -----------------------------------------------------------------
// Mutable board
// -----------------------------------------------------------------

class BoardState(val width: Int = 8, val height: Int = 8) {
    private val cells: Array<Array<CellState>> =
        Array(height) { Array(width) { CellState.Empty } }

    fun get(col: Int, row: Int): CellState {
        require(col in 0 until width && row in 0 until height) {
            "($col,$row) is outside ${width}x$height"
        }
        return cells[row][col]
    }

    fun get(cell: Cell): CellState = get(cell.col, cell.row)

    fun set(col: Int, row: Int, state: CellState) {
        require(col in 0 until width && row in 0 until height) {
            "($col,$row) is outside ${width}x$height"
        }
        cells[row][col] = state
    }

    fun inBounds(col: Int, row: Int) =
        col in 0 until width && row in 0 until height

    fun isFull(): Boolean {
        cells.forEach { row ->
            row.forEach { if (it is CellState.Empty) return false }
        }
        return true
    }

    fun clear() {
        cells.forEach { row -> row.fill(CellState.Empty) }
    }

    fun preFill(cellsToFill: List<Cell>) {
        cellsToFill.forEach { set(it.col, it.row, CellState.Occupied("pre")) }
    }

    fun snapshot(): Array<Array<CellState>> =
        Array(height) { row -> cells[row].copyOf() }

    fun restore(snapshot: Array<Array<CellState>>) {
        require(snapshot.size == height) { "snapshot row count mismatch" }
        for (row in 0 until height) {
            require(snapshot[row].size == width) { "snapshot col count mismatch" }
            cells[row] = snapshot[row].copyOf()
        }
    }
}

// -----------------------------------------------------------------
// Outcomes
// -----------------------------------------------------------------

sealed class PlacementResult {
    data object Rejected : PlacementResult()
    data class Accepted(
        val filledCells: List<Cell>,
        val clearedRows: List<Int>,
        val clearedColumns: List<Int>,
        val clearedSquares3x3: List<Cell>,
        val additionalCleared: List<Cell>,
        val totalCleared: Int,
        val placementPoints: Int,
        val linePoints: Int,
        val squarePoints: Int,
        val streakPoints: Int,
        val combo: ComboType,
        val totalPoints: Int,
    ) : PlacementResult()
    data class GameOver(
        val reason: String,                   // "no_space" / "timeout" / "no_pieces"
        val finalScore: Int,
        val canContinue: Boolean = true,      // can the player continue with revives/ads?
        val isFirstAttempt: Boolean = false,
    ) : PlacementResult()
    data class Completed(
        val finalScore: Int,
        val stars: Int,
        val timeSpentMs: Long,
        val piecesPlaced: Int,
        val maxCombo: ComboType,
        val maxStreak: Int,
    ) : PlacementResult()
}

// -----------------------------------------------------------------
// Events (one-shot, for both UI animation + mission tracking)
// -----------------------------------------------------------------

sealed class GameEvent {
    data class PiecePlaced(val pieceId: String, val origin: Cell, val cellCount: Int) : GameEvent()
    data class LinesCleared(
        val rows: List<Int>,
        val columns: List<Int>,
        val squares3x3: List<Cell>,
    ) : GameEvent()
    /** One or more HeatLocked cells expired and became Empty during tick(). */
    data class HeatUnlocked(val cells: List<Cell>) : GameEvent()
    data class ScoreUpdated(val newScore: Int, val target: Int, val delta: Int) : GameEvent()
    data class ComboActivated(val type: ComboType, val multiplier: Int, val scoreDelta: Int) : GameEvent()
    data class StreakUpdated(val level: Int, val multiplier: Float) : GameEvent()
    data class StreakBroken(val finalLevel: Int) : GameEvent()
    data class LevelStarted(val levelId: String, val attempt: Int) : GameEvent()
    data class LevelCompleted(
        val levelId: String, val score: Int, val stars: Int,
        val timeSeconds: Float, val piecesPlaced: Int,
        val maxCombo: ComboType, val maxStreak: Int,
    ) : GameEvent()
    data class LevelFailed(
        val levelId: String?, val score: Int,
        val reason: String, val canContinue: Boolean,
    ) : GameEvent()
    data class LevelContinued(val method: String, val extraPieces: Int) : GameEvent()
    data class SpecialCellsCleared(val crystals: Int, val heatLocksSurvived: Int, val blackHoles: Int) : GameEvent()
    data class PowerUpUsed(val powerUpId: String, val levelId: String) : GameEvent()
    data class CurrencyChanged(val currency: String, val delta: Int, val newTotal: Int, val source: String) : GameEvent()
    data class WorldUnlocked(val worldIndex: Int) : GameEvent()
    data class LevelUnlocked(val levelId: String) : GameEvent()
    data class TrayRefreshed(val newTray: List<PieceShape>) : GameEvent()
    data class ExtraPiecesAwarded(val count: Int, val method: String) : GameEvent()
}

// -----------------------------------------------------------------
// The engine
// -----------------------------------------------------------------

class GameplayEngine @Inject constructor (
    private val clock: Clock,
    private val seedRandom: Random,
) {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private val undoStack: ArrayDeque<Array<Array<CellState>>> = ArrayDeque()
    private var pieceSelector: PiecePoolSelector? = null

    fun startLevel(level: LevelSpec, attempt: Int) {
        val board = BoardState(level.boardSize.first, level.boardSize.second)
        level.preFilled.forEach { cell ->
            board.set(cell.col, cell.row, CellState.Occupied("pre"))
        }
        undoStack.clear()
        val seed = PiecePoolSelector.deriveSeed(level.levelId, level.worldIndex)
        pieceSelector = PiecePoolSelector(
            pool = PiecePoolSelector.praderaPool(),
            random = seedRandom.let { Random(seed) },
        )
        val initialTray = pieceSelector!!.buildTray(TRAY_SIZE, level)
        _state.value = GameState(
            level = level,
            board = board,
            tray = initialTray,
            score = 0,
            streak = 0,
            streakMultiplier = 1f,
            timeRemainingMs = (level.timeLimitSeconds * 1000L).toLong(),
            isPaused = false,
            isReviveUsed = false,
            movesRemainingOnShield = 0,
            attemptNumber = attempt,
            piecesPlacedThisLevel = 0,
            maxCombo = ComboType.Single,
            maxStreak = 0,
            specialCellsCleared = 0,
            totalLinesCleared = 0,
            totalSquaresCleared = 0,
            extraPiecesFromContinue = 0,
            rngSeed = seed,
        )
        _events.tryEmit(GameEvent.LevelStarted(level.levelId, attempt))
        _events.tryEmit(GameEvent.TrayRefreshed(initialTray))
    }

    fun place(trayIndex: Int, origin: Cell): PlacementResult {
        val s = _state.value
        val level = s.level ?: return PlacementResult.Rejected
        if (s.isPaused) return PlacementResult.Rejected
        val tray = s.tray
        if (trayIndex !in tray.indices) return PlacementResult.Rejected
        val shape = tray[trayIndex]
        if (!canPlace(s.board, shape, origin)) return PlacementResult.Rejected

        // 1. Snapshot for undo.
        undoStack.addLast(s.board.snapshot())

        // 2. Fill the cells.
        val filled = mutableListOf<Cell>()
        shape.absoluteCellsAt(origin).forEach { cell ->
            s.board.set(cell.col, cell.row, CellState.Occupied(shape.id))
            filled.add(cell)
        }
        _events.tryEmit(GameEvent.PiecePlaced(shape.id, origin, shape.cells.size))

        // 3. Detect line clears.
        val clear = LineClearDetector.detect(s.board)
        val candidates = mutableListOf<Cell>()
        clear.rows.forEach { y ->
            for (x in 0 until s.board.width) candidates.add(Cell(x, y))
        }
        clear.columns.forEach { x ->
            for (y in 0 until s.board.height) candidates.add(Cell(x, y))
        }
        clear.squares3x3.forEach { origin3x3 ->
            for (dx in 0..2) for (dy in 0..2) {
                candidates.add(Cell(origin3x3.col + dx, origin3x3.row + dy))
            }
        }

        // 4. Special-cell pass.
        val special = SpecialCellProcessor.process(s.board, candidates)
        if (special.crystalsDemoted + special.blackHolesConsumed > 0) {
            _events.tryEmit(
                GameEvent.SpecialCellsCleared(
                    crystals = special.crystalsDemoted,
                    heatLocksSurvived = special.heatLocksSurvived,
                    blackHoles = special.blackHolesConsumed,
                )
            )
        }

        // 5. Scoring.
        val totalCleared = candidates.size + special.additionalCleared.size
        val placedPoints = shape.cells.size * SCORE_PER_CELL
        val cellsInLines = (clear.rows.size * s.board.width) +
                (clear.columns.size * s.board.height)
        val linePtsBase = cellsInLines * SCORE_PER_LINE_CELL
        val combo = detectCombo(clear.rows.size, clear.columns.size, clear.squares3x3.size)
        val linePoints = linePtsBase * combo.multiplier
        val squarePoints = clear.squares3x3.size * SQUARE_3X3_BONUS

        val (newStreak, newStreakMult) = updateStreak(totalCleared > 0)
        val streakPoints = if (newStreakMult > 1f && totalCleared > 0) {
            ((linePoints + squarePoints) * newStreakMult - (linePoints + squarePoints)).toInt()
        } else 0

        val totalPoints = placedPoints + linePoints + squarePoints + streakPoints
        val newScore = s.score + totalPoints

        val newMaxCombo = if (combo.ordinal > s.maxCombo.ordinal) combo else s.maxCombo
        val newMaxStreak = maxOf(s.maxStreak, newStreak)
        val newSpecials = s.specialCellsCleared +
                special.crystalsDemoted + special.blackHolesConsumed

        _state.value = s.copy(
            score = newScore,
            streak = newStreak,
            streakMultiplier = newStreakMult,
            maxCombo = newMaxCombo,
            maxStreak = newMaxStreak,
            specialCellsCleared = newSpecials,
            totalLinesCleared = s.totalLinesCleared + clear.rows.size + clear.columns.size,
            totalSquaresCleared = s.totalSquaresCleared + clear.squares3x3.size,
        )

        _events.tryEmit(GameEvent.ScoreUpdated(newScore, level.targetScore, totalPoints))
        if (combo != ComboType.Single) {
            _events.tryEmit(GameEvent.ComboActivated(combo, combo.multiplier, linePoints))
        }
        _events.tryEmit(GameEvent.StreakUpdated(newStreak, newStreakMult))
        if (clear.rows.isNotEmpty() || clear.columns.isNotEmpty() || clear.squares3x3.isNotEmpty()) {
            _events.tryEmit(GameEvent.LinesCleared(clear.rows, clear.columns, clear.squares3x3))
        }

        // 6. Consume the piece and (maybe) refresh the tray.
        val newTray = tray.toMutableList().also { it.removeAt(trayIndex) }
        val finalTray = if (newTray.isEmpty()) {
            val refreshed = pieceSelector?.buildTray(TRAY_SIZE, level) ?: emptyList()
            _events.tryEmit(GameEvent.TrayRefreshed(refreshed))
            refreshed
        } else newTray
        _state.value = _state.value.copy(
            tray = finalTray,
            piecesPlacedThisLevel = s.piecesPlacedThisLevel + 1,
        )

        // 7. Victory / boss / game over.
        if (level.targetScore in 1..newScore) {
            return emitCompleted(level, newScore)
        }
        if (level.isBoss && level.bossConfig != null
            && isBossSatisfied(level.bossConfig, newSpecials)
        ) {
            return emitCompleted(level, newScore)
        }
        if (s.movesRemainingOnShield > 0) {
            _state.value = _state.value.copy(
                movesRemainingOnShield = s.movesRemainingOnShield - 1
            )
        }
        if (s.movesRemainingOnShield == 0 && !canAnyPieceBePlaced(s.board, finalTray)) {
            return emitFailed("no_space", newScore)
        }

        return PlacementResult.Accepted(
            filledCells = filled,
            clearedRows = clear.rows,
            clearedColumns = clear.columns,
            clearedSquares3x3 = clear.squares3x3,
            additionalCleared = special.additionalCleared,
            totalCleared = totalCleared,
            placementPoints = placedPoints,
            linePoints = linePoints,
            squarePoints = squarePoints,
            streakPoints = streakPoints,
            combo = combo,
            totalPoints = totalPoints,
        )
    }

    fun pause() { _state.value = _state.value.copy(isPaused = true) }
    fun resume() { _state.value = _state.value.copy(isPaused = false) }

    fun tick(deltaMs: Long) {
        val s = _state.value
        if (s.isPaused || s.level?.timeLimitSeconds == null) return
        val newRemaining = (s.timeRemainingMs - deltaMs).coerceAtLeast(0L)

        // Unlock any HeatLocked cells whose timer has expired.
        // We check with the real clock (not the countdown timer) so that
        // the unlock is wall-clock accurate even if the player pauses.
        val nowMs = clock.now().toEpochMilliseconds()
        val unlockedCells = mutableListOf<Cell>()
        for (y in 0 until s.board.height) {
            for (x in 0 until s.board.width) {
                val cell = s.board.get(x, y)
                if (cell is CellState.HeatLocked && nowMs >= cell.unlockAtMs) {
                    s.board.set(x, y, CellState.Empty)
                    unlockedCells.add(Cell(x, y))
                }
            }
        }
        if (unlockedCells.isNotEmpty()) {
            _events.tryEmit(GameEvent.HeatUnlocked(unlockedCells))
        }

        _state.value = s.copy(timeRemainingMs = newRemaining)
        if (newRemaining == 0L && s.level.timeLimitSeconds > 0f) {
            emitFailed("timeout", s.score)
        }
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val snap = undoStack.removeLast()
        _state.value = _state.value.copy(
            board = _state.value.board.apply { restore(snap) }
        )
        return true
    }

    /**
     * Spend-gems revive: restore 3 moves and grant a shield
     * for the next 5 moves. Returns false if the player has
     * already used their one revive.
     */
    fun revive(movesToRestore: Int = 3, shieldMoves: Int = 5): Boolean {
        val cur = _state.value
        if (cur.isReviveUsed) return false
        if (undoStack.size < movesToRestore) return false
        repeat(movesToRestore) {
            val snap = undoStack.removeLast()
            cur.board.restore(snap)
        }
        _state.value = cur.copy(
            isReviveUsed = true,
            movesRemainingOnShield = shieldMoves,
            timeRemainingMs = ((cur.level?.timeLimitSeconds ?: 0f) * 1000L).toLong(),
        )
        _events.tryEmit(GameEvent.LevelContinued("revive", movesToRestore))
        return true
    }

    /**
     * Watch-an-ad continue: add N extra pieces to the tray
     * and reset the shield.
     */
    fun continueWithExtraPieces(count: Int = 3, shieldMoves: Int = 5): Boolean {
        val cur = _state.value
        val level = cur.level ?: return false
        if (cur.isReviveUsed) return false  // one continue per level
        val extras = pieceSelector?.buildTray(count, level) ?: return false
        val newTray = cur.tray + extras
        _state.value = cur.copy(
            tray = newTray,
            isReviveUsed = true,
            movesRemainingOnShield = shieldMoves,
            timeRemainingMs = maxOf(cur.timeRemainingMs, 5_000L),
            extraPiecesFromContinue = cur.extraPiecesFromContinue + count,
        )
        _events.tryEmit(GameEvent.LevelContinued("ad", count))
        _events.tryEmit(GameEvent.ExtraPiecesAwarded(count, "ad"))
        _events.tryEmit(GameEvent.TrayRefreshed(newTray))
        return true
    }

    /**
     * Spend-gems for an immediate power-up (Bomb, Clear Row,
     * Clear Col, Swap). For the MVP we only implement "Clear
     * Single Cell" (bomb) and "Clear Row".
     */
    fun usePowerUp(powerUpId: String, target: Cell): Boolean {
        val cur = _state.value
        if (!cur.board.inBounds(target.col, target.row)) return false
        when (powerUpId) {
            "bomb" -> {
                undoStack.addLast(cur.board.snapshot())
                for (dx in -1..1) for (dy in -1..1) {
                    val c = Cell(target.col + dx, target.row + dy)
                    if (cur.board.inBounds(c.col, c.row)) {
                        cur.board.set(c.col, c.row, CellState.Empty)
                    }
                }
            }
            "clear_row" -> {
                undoStack.addLast(cur.board.snapshot())
                for (x in 0 until cur.board.width) {
                    cur.board.set(x, target.row, CellState.Empty)
                }
            }
            "clear_col" -> {
                undoStack.addLast(cur.board.snapshot())
                for (y in 0 until cur.board.height) {
                    cur.board.set(target.col, y, CellState.Empty)
                }
            }
            else -> return false
        }
        _events.tryEmit(GameEvent.PowerUpUsed(powerUpId, cur.level?.levelId ?: ""))
        return true
    }

    fun restartLevel() {
        val s = _state.value
        val level = s.level ?: return
        startLevel(level, attempt = s.attemptNumber + 1)
    }

    // -----------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------

    // Delegate placement validation to BoardValidator (single source of truth).
    // The previous private copies duplicated logic and canAnyPieceBePlaced had
    // an off-by-one bug: it iterated 0..board.width-1 regardless of piece width,
    // which could produce false-negative "no moves" calls near the right edge.
    private fun canPlace(board: BoardState, shape: PieceShape, origin: Cell): Boolean =
        BoardValidator.canPlace(board, shape, origin)

    private fun canAnyPieceBePlaced(board: BoardState, tray: List<PieceShape>): Boolean =
        BoardValidator.canAnyPieceBePlaced(board, tray)

    private fun detectCombo(rows: Int, cols: Int, squares: Int): ComboType {
        val lines = rows + cols
        if (lines == 4 && squares >= 1) return ComboType.Ultra
        if (lines == 1) return ComboType.Single
        if (lines == 2) return ComboType.Double
        if (lines == 3) return ComboType.Triple
        if (lines == 4) return ComboType.Quad
        if (lines == 5) return ComboType.Penta
        if (lines >= 6) return ComboType.Ultra
        return ComboType.Single
    }

    private fun updateStreak(cleared: Boolean): Pair<Int, Float> {
        val s = _state.value
        val newLevel = if (cleared) s.streak + 1 else 0
        if (!cleared && s.streak > 0) {
            _events.tryEmit(GameEvent.StreakBroken(s.streak))
        }
        return newLevel to streakMultiplierFor(newLevel)
    }

    private fun streakMultiplierFor(level: Int): Float = when {
        level <= 1 -> 1f
        level == 2 -> 2f
        level == 3 -> 3f
        level == 4 -> 4f
        else -> 5f
    }

    private fun isBossSatisfied(config: BossConfig, specialPiecesCleared: Int): Boolean {
        if (config.requiredSpecialPieceClears > 0 &&
            specialPiecesCleared < config.requiredSpecialPieceClears) return false
        return true
    }

    private fun emitCompleted(level: LevelSpec, finalScore: Int): PlacementResult {
        val s = _state.value
        val stars = computeStars(level, finalScore)
        val result = PlacementResult.Completed(
            finalScore = finalScore,
            stars = stars,
            timeSpentMs = (level.timeLimitSeconds * 1000L).toLong() - s.timeRemainingMs,
            piecesPlaced = s.piecesPlacedThisLevel,
            maxCombo = s.maxCombo,
            maxStreak = s.maxStreak,
        )
        _events.tryEmit(
            GameEvent.LevelCompleted(
                levelId = level.levelId,
                score = finalScore,
                stars = stars,
                timeSeconds = result.timeSpentMs / 1000f,
                piecesPlaced = result.piecesPlaced,
                maxCombo = result.maxCombo,
                maxStreak = result.maxStreak,
            )
        )
        return result
    }

    private fun emitFailed(reason: String, finalScore: Int): PlacementResult {
        val s = _state.value
        val result = PlacementResult.GameOver(
            reason = reason,
            finalScore = finalScore,
            canContinue = !s.isReviveUsed,
            isFirstAttempt = s.attemptNumber == 1 && !s.isReviveUsed,
        )
        _events.tryEmit(
            GameEvent.LevelFailed(
                levelId = _state.value.level?.levelId,
                score = finalScore,
                reason = reason,
                canContinue = result.canContinue,
            )
        )
        return result
    }

    private fun computeStars(level: LevelSpec, score: Int): Int {
        if (level.targetScore <= 0) return 1
        val silver = (level.targetScore * level.silverMultiplier).toInt()
        val gold = (level.targetScore * level.goldMultiplier).toInt()
        return when {
            score >= gold -> 3
            score >= silver -> 2
            else -> 1
        }
    }

    companion object {
        const val TRAY_SIZE = 3
        const val SCORE_PER_CELL = 1
        const val SCORE_PER_LINE_CELL = 10
        const val SQUARE_3X3_BONUS = 50
    }
}
