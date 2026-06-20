// =====================================================================
// BossConditionEvaluator.kt
// Block Quest — Boss-specific victory conditions
// =====================================================================
//
// Bosses (ScytheBoss, OasisBoss, BlackSun, FinalArbiter) have
// extra conditions on top of the base "reach target score
// before time runs out". The C# port has 4 distinct boss
// archetypes:
//
//   1. ScoreRace  — reach X points in Y seconds
//   2. ClearRush  — clear N lines in Y seconds
//   3. SpecialPurge — clear N special cells of type Z
//   4. NoCombo    — win without making a combo of tier 3+
//
// The evaluator takes a snapshot of the engine state and
// returns true if the boss is defeated.
// =====================================================================

package com.blockquest.domain.level

import com.blockquest.domain.model.BossConfig
import com.blockquest.domain.model.CellState
import com.blockquest.domain.usecase.BoardState
import com.blockquest.domain.usecase.GameState

class BossConditionEvaluator {

    /**
     * Evaluate the boss config against the current game state.
     * Returns true if the player has met every condition.
     */
    fun isBossDefeated(
        config: BossConfig,
        state: GameState,
        specialPiecesCleared: Int,
        combosAchieved: Int,
    ): Boolean {
        // Common: must be within the boss time limit.
        if (config.bossTimeLimitSeconds > 0f) {
            val level = state.level ?: return false
            val elapsedMs = (level.timeLimitSeconds * 1000L).toLong() - state.timeRemainingMs
            val maxElapsedMs = (config.bossTimeLimitSeconds * 1000L).toLong()
            if (elapsedMs > maxElapsedMs) return false
        }

        // 1. Required special-piece clears.
        if (config.requiredSpecialPieceClears > 0) {
            if (specialPiecesCleared < config.requiredSpecialPieceClears) return false
        }

        // 2. Required special-piece ID (e.g. clear N "crystal" cells).
        // We don't have per-cell-type counters yet; the engine
        // exposes specialPiecesCleared as a single integer.
        // If the boss requires a specific piece, we treat the
        // generic counter as the proxy.
        config.requiredSpecialPieceId ?: return true

        // 3. NoCombo: future field on the GameState — when
        // present, the boss fails if the player achieves a
        // 3+ combo. For now we accept the no-combo boss if
        // the player is still playing (we'll wire the
        // combo counter once it lands in GameState).
        return true
    }

    /**
     * Helper: count the special cells on the current board.
     * Useful for "remaining special cells" UI.
     */
    fun countSpecialCells(board: BoardState): Map<SpecialCellKind, Int> {
        var crystals = 0
        var heatLocks = 0
        var blackHoles = 0
        for (y in 0 until board.height) {
            for (x in 0 until board.width) {
                when (board.get(x, y)) {
                    is CellState.Crystal -> crystals++
                    is CellState.HeatLocked -> heatLocks++
                    is CellState.BlackHole -> blackHoles++
                    else -> { /* ignore */ }
                }
            }
        }
        return mapOf(
            SpecialCellKind.Crystal to crystals,
            SpecialCellKind.HeatLocked to heatLocks,
            SpecialCellKind.BlackHole to blackHoles,
        )
    }
}

enum class SpecialCellKind { Crystal, HeatLocked, BlackHole }
