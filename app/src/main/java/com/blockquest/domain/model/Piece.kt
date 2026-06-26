// =====================================================================
// Piece.kt
// Block Quest — Piece domain model and library
// =====================================================================

package com.blockquest.domain.model

/**
 * A Piece is a playable object in the game.
 * It consists of a [PieceShape] and metadata for rendering and logic.
 */
data class Piece(
    val id: String,
    val shape: PieceShape,
    val color: String? = null, // Optional color override
    val isBossPiece: Boolean = false,
    val specialEffect: String? = null, // e.g., "explosive", "healing"
)

/**
 * PieceShape defines the footprint of a piece.
 * Local coordinates (col, row) where (0,0) is top-left.
 */
data class PieceShape(
    val id: String,
    val cells: List<Cell>,
) {
    init {
        require(cells.isNotEmpty()) { "PieceShape must have at least one cell" }
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

    val size: Int = (boundingBox.first * boundingBox.second)

    fun absoluteCellsAt(origin: Cell): Sequence<Cell> =
        cells.asSequence().map { Cell(origin.col + it.col, origin.row + it.row) }

    companion object {
        // Basic Shapes
        val Dot1x1 = PieceShape("dot_1x1", listOf(Cell(0, 0)))
        val LineH1x2 = PieceShape("line_h1x2", listOf(Cell(0, 0), Cell(1, 0)))
        val LineH1x3 = PieceShape("line_h1x3", listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0)))
        val LineH1x4 = PieceShape("line_h1x4", listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0)))
        val LineH1x5 = PieceShape("line_h1x5", listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0), Cell(4, 0)))
        val LineV2x1 = PieceShape("line_v2x1", listOf(Cell(0, 0), Cell(0, 1)))
        val LineV3x1 = PieceShape("line_v3x1", listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2)))
        val LineV4x1 = PieceShape("line_v4x1", listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3)))
        val LineV5x1 = PieceShape("line_v5x1", listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3), Cell(0, 4)))
        val Square2x2 = PieceShape("square_2x2", listOf(Cell(0, 0), Cell(1, 0), Cell(0, 1), Cell(1, 1)))
        val Square3x3 = PieceShape("square_3x3", listOf(
            Cell(0, 0), Cell(1, 0), Cell(2, 0),
            Cell(0, 1), Cell(1, 1), Cell(2, 1),
            Cell(0, 2), Cell(1, 2), Cell(2, 2)
        ))
        
        // Complex Shapes
        val LCornerSmall = PieceShape("l_corner_s", listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1)))
        val LCornerLarge = PieceShape("l_corner_l", listOf(
            Cell(0, 0), Cell(0, 1), Cell(0, 2),
            Cell(0, 3), Cell(1, 3), Cell(2, 3)
        ))
        val TBlock = PieceShape("t_block", listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(1, 1)))
        val SBlock = PieceShape("s_block", listOf(Cell(1, 0), Cell(2, 0), Cell(0, 1), Cell(1, 1)))
        val ZBlock = PieceShape("z_block", listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1)))
        val UShape = PieceShape("u_shape", listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(0, 1), Cell(2, 1)))
        val Rect2x4 = PieceShape("rect_2x4", listOf(
            Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(3, 0),
            Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(3, 1)
        ))
        val Cross5x5 = PieceShape("cross_5x5", listOf(
            Cell(0, 2), Cell(1, 2), Cell(2, 2), Cell(3, 2), Cell(4, 2),
            Cell(2, 0), Cell(2, 1), Cell(2, 3), Cell(2, 4)
        ))

        // Boss Shapes
        val Scythe = PieceShape("scythe_boss", listOf(
            Cell(0, 0), Cell(1, 0), Cell(2, 0),
            Cell(0, 1), Cell(0, 2), Cell(1, 2)
        ))
        val DragonTail = PieceShape("boss_dragon_tail", listOf(
            Cell(0, 0), Cell(1, 0), 
            Cell(1, 1), Cell(2, 1),
            Cell(2, 2), Cell(3, 2)
        ))
        val GiantHammer = PieceShape("boss_giant_hammer", listOf(
            Cell(0, 0), Cell(1, 0), Cell(2, 0),
            Cell(1, 1),
            Cell(1, 2),
            Cell(1, 3)
        ))

        val PraderaPool: List<PieceShape> = listOf(
            Dot1x1, LineH1x2, LineH1x3, LineH1x4,
            LineV2x1, LineV3x1, LineV4x1,
            Square2x2, LCornerSmall, LCornerLarge,
            TBlock, SBlock, ZBlock, UShape, Rect2x4
        )

        val Library: Map<String, PieceShape> = listOf(
            Dot1x1, LineH1x2, LineH1x3, LineH1x4, LineH1x5,
            LineV2x1, LineV3x1, LineV4x1, LineV5x1,
            Square2x2, Square3x3,
            LCornerSmall, LCornerLarge, TBlock, SBlock, ZBlock,
            UShape, Rect2x4, Cross5x5, Scythe, DragonTail, GiantHammer
        ).associateBy { it.id }
    }
}
