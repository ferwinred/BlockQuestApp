// =====================================================================
// BoardValidator.kt
// Block Quest — Pure placement validation
// =====================================================================

package com.blockquest.domain.board

import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.CellState
import com.blockquest.domain.model.PieceShape
import com.blockquest.domain.usecase.BoardState

object BoardValidator {

    /**
     * Can the [shape] be placed at [origin] on [board]?
     * Returns true iff every cell of the piece is in bounds
     * AND currently `Empty`.
     */
    fun canPlace(board: BoardState, shape: PieceShape, origin: Cell): Boolean {
        val cells = shape.absoluteCellsAt(origin)
        for (cell in cells) {
            if (!board.inBounds(cell.col, cell.row)) return false
            if (board.get(cell) !is CellState.Empty) return false
        }
        return true
    }

    /**
     * Can any shape in [tray] be placed somewhere on [board]?
     */
    fun canAnyPieceBePlaced(board: BoardState, tray: List<PieceShape>): Boolean {
        if (tray.isEmpty()) return false
        for (shape in tray) {
            val (w, h) = shape.boundingBox
            for (y in 0..board.height - h) {
                for (x in 0..board.width - w) {
                    if (canPlace(board, shape, Cell(x, y))) return true
                }
            }
        }
        return false
    }

    /**
     * Greedy: find the placement that clears the most cells.
     * Returns null if no shape in "tray" can be placed.
     *
     * The "score" of a placement is the number of cells that
     * would be cleared (row + column + 3x3 squares) by that
     * placement. We pre-compute clearable counts and pick the
     * shape + origin that maximises the score. Ties are
     * broken by smaller shapes (so we keep the big pieces
     * for later).
     */
    data class PlacementHint(
        val shape: PieceShape,
        val origin: Cell,
        val cellsCleared: Int,
    )

    fun findBestPlacement(
        board: BoardState,
        tray: List<PieceShape>,
    ): PlacementHint? {
        if (tray.isEmpty()) return null
        var best: PlacementHint? = null
        for (shape in tray) {
            val (w, h) = shape.boundingBox
            for (y in 0..board.height - h) {
                for (x in 0..board.width - w) {
                    val origin = Cell(x, y)
                    if (!canPlace(board, shape, origin)) continue
                    val cleared = countCellsClearedBy(board, shape, origin)
                    if (best == null ||
                        cleared > best.cellsCleared ||
                        (cleared == best.cellsCleared && shape.cells.size < best.shape.cells.size)
                    ) {
                        best = PlacementHint(shape, origin, cleared)
                    }
                }
            }
        }
        return best
    }

    private fun countCellsClearedBy(
        board: BoardState,
        shape: PieceShape,
        origin: Cell,
    ): Int {
        // Simulate the placement and return the count of
        // cells that would be removed. We don't actually
        // mutate the board — we just count what *would*
        // happen.
        val filled = shape.absoluteCellsAt(origin).toHashSet()
        var cleared = 0
        for (y in 0 until board.height) {
            var full = true
            for (x in 0 until board.width) {
                val c = Cell(x, y)
                if (c !in filled && board.get(c) is CellState.Empty) {
                    full = false
                    break
                }
            }
            if (full) cleared += board.width
        }
        for (x in 0 until board.width) {
            var full = true
            for (y in 0 until board.height) {
                val c = Cell(x, y)
                if (c !in filled && board.get(c) is CellState.Empty) {
                    full = false
                    break
                }
            }
            if (full) cleared += board.height
        }
        for (x in 0..board.width - 3) {
            for (y in 0..board.height - 3) {
                var full = true
                for (dx in 0..2) for (dy in 0..2) {
                    val c = Cell(x + dx, y + dy)
                    if (c !in filled && board.get(c) is CellState.Empty) {
                        full = false
                    }
                }
                if (full) cleared += 9
            }
        }
        return cleared
    }
}
