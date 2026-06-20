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
 * An immutable description of a piece's footprint. Local
 * coordinates are (col, row) where (0,0) is the top-left of the
 * bounding box. We do NOT support rotation (matches the C#
 * port's design decision — see Apéndice B, decision 5 of the
 * original spec).
 */
data class PieceShape(
    val id: String,
    val cells: List<Cell>,
) {
    init {
        require(cells.isNotEmpty()) { "PieceShape must have at least one cell" }
    }

    val size: Int = run {
        var maxX = 0
        var maxY = 0
        cells.forEach {
            if (it.col > maxX) maxX = it.col
            if (it.row > maxY) maxY = it.row
        }
        // Bounding-box width is `maxX + 1`, height `maxY + 1`.
        // We return `max` as a quick checksum (callers that need
        // the actual box call `boundingBox`).
        (maxX + 1) * (maxY + 1)
    }

    val boundingBox: Pair<Int, Int> = run {
        var maxX = 0
        var maxY = 0
        cells.forEach {
            if (it.col > maxX) maxX = it.col
            if (it.row > maxY) maxY = it.row
        }
        (maxX + 1) to (maxY + 1)
    }

    /**
     * Iterate the absolute (col, row) of every cell when the
     * piece is placed at `origin`. The iterator yields values
     * in declaration order, matching the C# port.
     */
    fun absoluteCellsAt(origin: Cell): Sequence<Cell> =
        cells.asSequence().map { Cell(origin.col + it.col, origin.row + it.row) }

    // -----------------------------------------------------------------
    // Built-in shape library. Identical to the C# `PieceShape`
    // library (Sección 6, MVP-02). 19 shapes that cover World 1-3
    // with the basic Block Blast / Woodoku piece set.
    // -----------------------------------------------------------------
    companion object {
        val Dot1x1 = PieceShape("dot_1x1", listOf(Cell(0, 0)))
        val LineH1x2 = PieceShape(
            "line_h1x2",
            listOf(Cell(0, 0), Cell(1, 0))
        )
        val LineH1x3 = PieceShape(
            "line_h1x3",
            listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0))
        )
        val LineH1x4 = PieceShape(
            "line_h1x4",
            listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0))
        )
        val LineH1x5 = PieceShape(
            "line_h1x5",
            listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0), Cell(4, 0))
        )
        val LineV2x1 = PieceShape(
            "line_v2x1",
            listOf(Cell(0, 0), Cell(0, 1))
        )
        val LineV3x1 = PieceShape(
            "line_v3x1",
            listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2))
        )
        val LineV4x1 = PieceShape(
            "line_v4x1",
            listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3))
        )
        val LineV5x1 = PieceShape(
            "line_v5x1",
            listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3), Cell(0, 4))
        )
        val Square2x2 = PieceShape(
            "square_2x2",
            listOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1))
        )
        val Square3x3 = PieceShape(
            "square_3x3",
            listOf(
                Cell(0, 0), Cell(1, 0), Cell(2, 0),
                Cell(0, 1), Cell(1, 1), Cell(2, 1),
                Cell(0, 2), Cell(1, 2), Cell(2, 2)
            )
        )
        val LCornerSmall = PieceShape(
            "l_corner_s",
            listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 2))
        )
        val LCornerLarge = PieceShape(
            "l_corner_l",
            listOf(
                Cell(0, 0), Cell(0, 1), Cell(0, 2),
                Cell(0, 3), Cell(1, 3), Cell(2, 3)
            )
        )
        val TBlock = PieceShape(
            "t_block",
            listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(1, 1))
        )
        val SBlock = PieceShape(
            "s_block",
            listOf(Cell(1, 0), Cell(2, 0), Cell(0, 1), Cell(1, 1))
        )
        val ZBlock = PieceShape(
            "z_block",
            listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1))
        )
        val UShape = PieceShape(
            "u_shape",
            listOf(
                Cell(0, 0), Cell(1, 0), Cell(2, 0),
                Cell(0, 1), Cell(2, 1)
            )
        )
        val Rect2x4 = PieceShape(
            "rect_2x4",
            listOf(
                Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0),
                Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(3, 1)
            )
        )
        val Cross5x5 = PieceShape(
            "cross_5x5",
            listOf(
                Cell(0, 2), Cell(1, 2), Cell(2, 2), Cell(3, 2), Cell(4, 2),
                Cell(2, 0), Cell(2, 1), Cell(2, 3), Cell(2, 4)
            )
        )
        val Scythe = PieceShape(
            "scythe_boss",
            listOf(
                Cell(0, 0), Cell(1, 0), Cell(2, 0),
                Cell(0, 1), Cell(0, 2), Cell(1, 2)
            )
        )

        /** The Pradera (World 1) pool, mirroring the C# port. */
        val PraderaPool: List<PieceShape> = listOf(
            Dot1x1, LineH1x2, LineH1x3, LineH1x4,
            LineV2x1, LineV3x1, LineV4x1,
            Square2x2, LCornerSmall, LCornerLarge,
            TBlock, SBlock, ZBlock, UShape, Rect2x4
        )

        /**
         * Lookup helper used by ScriptableObject importers
         * (the .json level specs reference shapes by id).
         */
        val Library: Map<String, PieceShape> = listOf(
            Dot1x1, LineH1x2, LineH1x3, LineH1x4, LineH1x5,
            LineV2x1, LineV3x1, LineV4x1, LineV5x1,
            Square2x2, Square3x3,
            LCornerSmall, LCornerLarge, TBlock, SBlock, ZBlock,
            UShape, Rect2x4, Cross5x5, Scythe
        ).associateBy { it.id }
    }
}

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
