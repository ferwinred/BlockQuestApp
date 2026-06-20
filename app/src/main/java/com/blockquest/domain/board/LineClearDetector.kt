// =====================================================================
// LineClearDetector.kt
// Block Quest — Row / column / 3x3 square detector
// =====================================================================

package com.blockquest.domain.board

import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.CellState
import com.blockquest.domain.usecase.BoardState

data class ClearedLines(
    val rows: List<Int>,
    val columns: List<Int>,
    val squares3x3: List<Cell>,
) {
    val totalCells: Int
        get() = rows.size * 8 + columns.size * 8 + squares3x3.size * 9  // assumes 8x8
    val isEmpty: Boolean
        get() = rows.isEmpty() && columns.isEmpty() && squares3x3.isEmpty()
}

object LineClearDetector {

    /**
     * Detect all clearable lines / squares on the current
     * board. A row is "full" iff every cell is non-empty.
     * A 3x3 square at (x, y) is "full" iff every one of
     * its 9 cells is non-empty.
     *
     * This is a pure read: it does not mutate the board.
     */
    fun detect(board: BoardState): ClearedLines {
        val rows = mutableListOf<Int>()
        val cols = mutableListOf<Int>()
        val squares = mutableListOf<Cell>()

        for (y in 0 until board.height) {
            if (rowFull(board, y)) rows.add(y)
        }
        for (x in 0 until board.width) {
            if (columnFull(board, x)) cols.add(x)
        }
        for (x in 0..board.width - 3) {
            for (y in 0..board.height - 3) {
                if (squareFull(board, x, y)) squares.add(Cell(x, y))
            }
        }
        return ClearedLines(rows, cols, squares)
    }

    private fun rowFull(board: BoardState, y: Int): Boolean {
        for (x in 0 until board.width) {
            if (board.get(x, y) is CellState.Empty) return false
        }
        return true
    }

    private fun columnFull(board: BoardState, x: Int): Boolean {
        for (y in 0 until board.height) {
            if (board.get(x, y) is CellState.Empty) return false
        }
        return true
    }

    private fun squareFull(board: BoardState, x: Int, y: Int): Boolean {
        for (dx in 0..2) for (dy in 0..2) {
            if (board.get(x + dx, y + dy) is CellState.Empty) return false
        }
        return true
    }
}
