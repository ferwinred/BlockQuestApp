// =====================================================================
// SpecialCellProcessor.kt
// Block Quest — Crystal / heat-lock / black-hole logic
// =====================================================================

package com.blockquest.domain.board

import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.CellState
import com.blockquest.domain.usecase.BoardState

data class SpecialCellResult(
    val additionalCleared: List<Cell>,
    val crystalsDemoted: Int = 0,
    val heatLocksSurvived: Int = 0,
    val blackHolesConsumed: Int = 0,
    val blackHoleExtraClears: Int = 0,
)

object SpecialCellProcessor {

    /**
     * Apply the special-cell rules to a list of "candidate"
     * cells (the ones that would be cleared by a line/column/
     * square). Returns the *additional* cells that should
     * also be cleared, plus counters used by analytics.
     *
     * The processor mutates the board — it expects that the
     * caller has already done the line-clear pass and that
     * the candidates are the union of those line cells.
     */
    fun process(board: BoardState, candidates: List<Cell>): SpecialCellResult {
        val additional = mutableListOf<Cell>()
        var crystals = 0
        var heatLocks = 0
        var blackHoles = 0
        var extraClears = 0

        for (c in candidates) {
            if (!board.inBounds(c.col, c.row)) continue
            when (val state = board.get(c.col, c.row)) {
                is CellState.Crystal -> {
                    val remaining = state.remainingHits - 1
                    if (remaining <= 0) {
                        board.set(c.col, c.row, CellState.Empty)
                        additional.add(c)
                    } else {
                        board.set(c.col, c.row, CellState.Crystal(remaining))
                    }
                    crystals++
                }
                is CellState.BlackHole -> {
                    board.set(c.col, c.row, CellState.Empty)
                    additional.add(c)
                    blackHoles++
                    // Consume one adjacent non-empty neighbor.
                    val neighbor = listOf(
                        Cell(c.col + 1, c.row),
                        Cell(c.col - 1, c.row),
                        Cell(c.col, c.row + 1),
                        Cell(c.col, c.row - 1)
                    ).firstOrNull {
                        board.inBounds(it.col, it.row) &&
                                board.get(it) !is CellState.Empty
                    }
                    if (neighbor != null) {
                        board.set(neighbor.col, neighbor.row, CellState.Empty)
                        additional.add(neighbor)
                        extraClears++
                    }
                }
                is CellState.HeatLocked -> {
                    // Survives — do not clear.
                    heatLocks++
                }
                is CellState.Empty -> { /* nothing to do */ }
                is CellState.Occupied -> {
                    board.set(c.col, c.row, CellState.Empty)
                    additional.add(c)
                }
            }
        }
        return SpecialCellResult(
            additionalCleared = additional,
            crystalsDemoted = crystals,
            heatLocksSurvived = heatLocks,
            blackHolesConsumed = blackHoles,
            blackHoleExtraClears = extraClears,
        )
    }
}
