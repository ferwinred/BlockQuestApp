// =====================================================================
// Models.kt
// Block Quest — Pure Kotlin domain model
// =====================================================================
//
// Design notes
// ------------
// * This file is part of the **domain** layer (Clean Architecture).
//   It has ZERO dependencies on Android, Compose, Firebase, or any
//   third-party SDK. Every class here could be unit-tested on the
//   host JVM with no emulator.
// * All collections exposed to upper layers are read-only (`List`,
//   not `MutableList`). Mutations go through the engine in
//   GameplayEngine.kt.
// * The `data class` keyword gives us free `equals`, `hashCode`,
//   `copy`, and `toString`. This is one of the wins of porting the
//   game from C# (struct-based) to Kotlin (data-class-based): we
//   drop our own `BoardSnapshot` value type and use the language
//   primitive.
// =====================================================================

package com.blockquest.domain.model

/**
 * Cell occupancy + special modifiers. Mirrors the C# `CellState`
 * enum from the Unity port, but as a sealed class so we can
 * pattern-match exhaustively in `when` expressions.
 */
sealed class CellState {
    /** Empty cell. Default state for every cell on a fresh board. */
    data object Empty : CellState()

    /** Plain block placed by the player. */
    data class Occupied(val pieceId: String) : CellState()

    /** Bosque: needs 2 passes to clear (crystal). */
    data class Crystal(val remainingHits: Int = 1) : CellState()

    /** Desierto: temporarily locks a row/column. */
    data class HeatLocked(val unlockAtMs: Long) : CellState()

    /** Espacio: consumes 1 adjacent non-empty neighbor when cleared. */
    data object BlackHole : CellState()
}

/**
 * Position on the board. We use a value class so the JVM emits a
 * primitive `int` for each axis — same perf as the C# Vector2Int.
 */
data class Cell(val col: Int, val row: Int) {
    operator fun plus(other: Cell) = Cell(col + other.col, row + other.row)

    companion object {
        val Zero = Cell(0, 0)
    }
}

/**
 * An immutable description of a piece's footprint.
 * MOVED to Piece.kt
 */
// PieceShape moved to Piece.kt

/**
 * Combo tier (Sección 2.3). The integer value is the score
 * multiplier applied on top of the line/3x3 base.
 */
enum class ComboType(val multiplier: Int, val displayName: String) {
    Single(1, ""),
    Double(2, "¡DOBLE!"),
    Triple(3, "¡TRIPLE L!"),
    Quad(4, "¡QUAD!"),
    Penta(5, "¡PENTA X!"),
    Ultra(6, "¡ULTRA CRUZ!")
}

/**
 * Level metadata. Mirrors the C# `LevelSpec` struct. Pure data,
 * no logic.
 */
data class LevelSpec(
    val levelId: String,
    val levelNumber: Int,
    val worldIndex: Int,
    val levelType: LevelType,
    val objective: LevelObjective,
    val targetScore: Int,
    val timeLimitSeconds: Float = 0f,
    val targetComboCount: Int = 0,
    val boardSize: Pair<Int, Int> = 8 to 8,
    val preFilled: List<Cell> = emptyList(),
    val piecePool: List<PieceShape> = emptyList(),
    val guaranteedPiece: PieceShape? = null,
    val guaranteedInHand: Int = -1,
    val rewardCoins: Int = 0,
    val rewardGems: Int = 0,
    val rewardSkinId: String? = null,
    val rewardTitleId: String? = null,
    val isMilestone: Boolean = false,
    val isBoss: Boolean = false,
    val silverMultiplier: Float = 1.25f,
    val goldMultiplier: Float = 1.50f,
    val bossConfig: BossConfig? = null,
)

enum class LevelType { Tutorial, Standard, Challenge, Milestone, Boss }
enum class LevelObjective { ScoreTarget, TimeAttack, Survival, Boss }

data class BossConfig(
    val bossId: String,
    val bossTimeLimitSeconds: Float,
    val requiredSpecialPieceClears: Int = 0,
    val requiredSpecialPieceId: String? = null,
)

/**
 * Currency state. Mirrors `CurrencyLedger` in the C# port but as
 * a data class (no need for a separate factory).
 */
data class CurrencyState(
    val coins: Int = 0,
    val gems: Int = 0,
)

/**
 * Top-level player state. Bundles the profile + currency
 * + auth identity into a single stream.
 */
data class PlayerState(
    val userId: String,
    val displayName: String = "Jugador",
    val currency: CurrencyState = CurrencyState(),
)

/**
 * Per-level persistent result. Mirrors `LevelResult` in the C#
 * port.
 */
data class LevelResult(
    val levelId: String,
    val completed: Boolean = false,
    val stars: Int = 0,           // 0..3
    val bestScore: Int = 0,
    val attempts: Int = 0,
)

/**
 * Aggregate of all per-level results. Mirrors `ProgressionData`.
 */
data class ProgressionState(
    val playerId: String,
    val displayName: String,
    val results: Map<String, LevelResult> = emptyMap(),
    val worldUnlocked: List<Boolean> = listOf(true, false, false, false, false),
    val unlockedSkins: List<String> = listOf("default"),
    val unlockedPowerUps: List<String> = emptyList(),
    val completedAchievements: List<String> = emptyList(),
) {
    init {
        require(worldUnlocked.size == 5) { "worldUnlocked must have exactly 5 entries" }
    }

    fun isWorldUnlocked(worldIndex: Int): Boolean =
        worldIndex in worldUnlocked.indices && worldUnlocked[worldIndex]

    fun getResult(levelId: String): LevelResult? = results[levelId]
}

/**
 * Daily reward state. Mirrors `DailyRewardState`.
 */
data class DailyRewardState(
    val lastClaimed: Long = 0L,    // epoch millis
    val currentStreak: Int = 0,    // 0..7
) {
    val dayNumberToShow: Int get() = ((currentStreak) % 7) + 1
}
