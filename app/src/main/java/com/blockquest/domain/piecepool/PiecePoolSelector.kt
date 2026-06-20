// =====================================================================
// PiecePoolSelector.kt
// Block Quest — Seedable weighted-random piece selection
// =====================================================================
//
// The C# port uses System.Random with a per-level seed. The
// same seed + the same RNG sequence + the same input → the
// same output. This is the property we need for:
//   * Replay determinism (verify parity with the Unity port).
//   * Server-side anti-cheat: the server can re-simulate the
//     same seed and check that the player's reported tray
//     matches.
//
// The `Random` we use is the seedable `kotlin.random.Random`.
// It is thread-safe (the underlying impl is
// `XorWowRandom`, which is not — so we make the class
// thread-confined: one selector per level, never shared
// across threads without an external lock).
//
// Weights are per-shape and come from `LevelSpec.piecePool`.
// A "balanced" 8x8 level usually has weights that sum to
// 100, with single cells weighted highest and big shapes
// lowest (so the player doesn't get an unplayable tray).
// =====================================================================

package com.blockquest.domain.piecepool

import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.PieceShape
import kotlin.random.Random

/**
 * Weighted random selector over a finite pool of
 * [PieceShape]s. Construct once per level, drain until
 * the tray is full, discard.
 */
class PiecePoolSelector(
    private val pool: List<Pair<PieceShape, Int>>,
    private val random: Random = Random.Default,
) {
    init {
        require(pool.isNotEmpty()) { "pool must be non-empty" }
        pool.forEach { (shape, w) ->
            require(w > 0) { "weight for ${shape.id} must be > 0" }
        }
    }

    /** Cumulative weights are O(N) to build but O(log N) to query with binary search. */
    private val cumulative: List<Int> = run {
        var acc = 0
        pool.map { (shape, w) ->
            acc += w
            acc to shape
        }.let { items ->
            // store pairs (cum-weight, shape)
            items
        }.map { it.first }
    }
    private val total: Int = cumulative.last()

    fun select(): PieceShape {
        val r = random.nextInt(total)
        // binary search for the first index whose cum-weight > r
        var lo = 0
        var hi = cumulative.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cumulative[mid] <= r) lo = mid + 1 else hi = mid
        }
        return pool[lo].first
    }

    /**
     * Build a tray of [size] pieces. If the level specifies a
     * `guaranteedPiece` and a `guaranteedInHand` index, that
     * slot is filled with the guaranteed shape and the rest
     * are drawn from the pool.
     */
    fun buildTray(size: Int, level: LevelSpec): List<PieceShape> {
        require(size > 0)
        val out = ArrayList<PieceShape>(size)
        for (i in 0 until size) {
            out += select()
        }
        val guaranteed = level.guaranteedPiece
        val slot = level.guaranteedInHand
        if (guaranteed != null && slot in 0 until size) {
            out[slot] = guaranteed
        }
        return out
    }

    companion object {
        /**
         * Build a default Pradera pool. Same weights the C#
         * port uses (Sección 6.4 of the original spec).
         */
        fun praderaPool(): List<Pair<PieceShape, Int>> = listOf(
            PieceShape.Dot1x1 to 18,
            PieceShape.LineH1x2 to 16,
            PieceShape.LineH1x3 to 14,
            PieceShape.LineH1x4 to 10,
            PieceShape.LineH1x5 to 4,
            PieceShape.LineV2x1 to 14,
            PieceShape.LineV3x1 to 12,
            PieceShape.LineV4x1 to 8,
            PieceShape.LineV5x1 to 3,
            PieceShape.Square2x2 to 10,
            PieceShape.Square3x3 to 2,
            PieceShape.LCornerSmall to 8,
            PieceShape.LCornerLarge to 4,
            PieceShape.TBlock to 7,
            PieceShape.SBlock to 5,
            PieceShape.ZBlock to 5,
            PieceShape.UShape to 4,
            PieceShape.Rect2x4 to 3,
            PieceShape.Cross5x5 to 1,
            PieceShape.Scythe to 1,
        )

        /**
         * Derive a level-specific seed from the level id.
         * The C# port uses `(levelNumber * 397) ^ (worldIndex + 1)`.
         */
        fun deriveSeed(levelId: String, worldIndex: Int): Long {
            val hashed = levelId.fold(0L) { acc, c ->
                acc * 31L + c.code
            }
            return hashed xor ((worldIndex + 1).toLong() * 0x9E3779B97F4A7C15uL.toLong())
        }
    }
}

/**
 * A "smart" tray builder that avoids giving the player an
 * unwinnable tray. It re-rolls the tray if no piece fits on
 * the current board, with at most `maxAttempts` tries. The
 * fallback is the original tray (better to keep playing
 * than to softlock).
 */
class SmartTrayBuilder(
    private val pool: List<Pair<PieceShape, Int>>,
    private val maxAttempts: Int = 8,
    random: Random = Random.Default,
) {
    private val selector = PiecePoolSelector(pool, random)
    private val rng = random

    /**
     * Build a tray that maximises the chance of at least one
     * piece fitting. We don't promise anything — if the board
     * is so full that no piece can fit, every tray fails.
     *
     * `canPieceFit: (PieceShape) -> Boolean` should be a pure
     * function that does NOT depend on which other pieces are
     * in the tray.
     */
    fun buildTray(
        size: Int,
        level: LevelSpec,
        canPieceFit: (PieceShape) -> Boolean,
    ): List<PieceShape> {
        var best: List<PieceShape> = selector.buildTray(size, level)
        var bestFitCount = best.count(canPieceFit)
        repeat(maxAttempts) {
            val candidate = selector.buildTray(size, level)
            val fitCount = candidate.count(canPieceFit)
            if (fitCount > bestFitCount) {
                best = candidate
                bestFitCount = fitCount
            }
        }
        return best
    }
}

/**
 * Pure helper: check whether a piece would fit on an 8x8
 * board. The full `BoardValidator` lives in
 * `BoardValidator.kt` (TODO Phase 1.2).
 */
fun canPieceFitOnEmptyBoard(shape: PieceShape, width: Int = 8, height: Int = 8): Boolean {
    val (w, h) = shape.boundingBox
    return w <= width && h <= height
}
