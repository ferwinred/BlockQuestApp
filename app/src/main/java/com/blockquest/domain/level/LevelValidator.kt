// =====================================================================
// LevelValidator.kt
// Block Quest — Static + dynamic level-spec validator
// =====================================================================
//
// The validator runs two passes:
//   1. Static: at level-creation time (Firestore write, JSON
//      deserialization, design tool import). Catches:
//      * Missing required fields
//      * Board size out of bounds (1..16)
//      * Piece pool that doesn't contain the guaranteed piece
//      * Pre-filled cells outside the board
//      * Time limits that are not strictly positive
//   2. Dynamic: at level-load time. Catches:
//      * Piece pool referencing shape ids that aren't in the
//        library
//      * Pre-filled cells that overlap each other
//      * Board so full that no piece in the pool can be placed
//        (unwinnable)
//
// The static validator returns a sealed result; the dynamic
// one returns a list of warnings (since the engine can still
// play an "unwinnable" level — it just calls Game Over
// immediately).
// =====================================================================

package com.blockquest.domain.level

import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.PieceShape
import com.blockquest.domain.piecepool.canPieceFitOnEmptyBoard

sealed class LevelValidationResult {
    data object Ok : LevelValidationResult()
    data class Invalid(val errors: List<String>) : LevelValidationResult()
}

sealed class LevelRuntimeWarning {
    data object EmptyPiecePool : LevelRuntimeWarning()
    data class UnknownShapeId(val id: String) : LevelRuntimeWarning()
    data class OverlappingPreFill(val cells: List<Cell>) : LevelRuntimeWarning()
    data object UnwinnableBoard : LevelRuntimeWarning()
    data class UnusedGuaranteedSlot(val pieceId: String, val slot: Int) : LevelRuntimeWarning()
}

object LevelValidator {

    /** Static pass: run when a level is created or imported. */
    fun validateStatic(level: LevelSpec): LevelValidationResult {
        val errors = mutableListOf<String>()

        if (level.levelId.isBlank()) errors.add("levelId is blank")
        if (level.worldIndex !in 0..4) errors.add("worldIndex must be 0..4")
        if (level.targetScore < 0) errors.add("targetScore must be >= 0")
        if (level.timeLimitSeconds < 0) errors.add("timeLimitSeconds must be >= 0")
        val (w, h) = level.boardSize
        if (w !in 1..16 || h !in 1..16) errors.add("boardSize must be 1..16 in both dimensions")

        if (level.isBoss && level.bossConfig == null) {
            errors.add("isBoss = true but bossConfig is null")
        }
        if (!level.isBoss && level.bossConfig != null) {
            errors.add("isBoss = false but bossConfig is set")
        }
        if (level.silverMultiplier < 1f || level.goldMultiplier < 1f) {
            errors.add("silver/gold multipliers must be >= 1")
        }
        if (level.goldMultiplier < level.silverMultiplier) {
            errors.add("goldMultiplier must be >= silverMultiplier")
        }
        if (level.guaranteedPiece != null && level.guaranteedPiece !in level.piecePool) {
            errors.add("guaranteedPiece is not in the piecePool")
        }
        if (level.guaranteedInHand < -1 || level.guaranteedInHand >= 3) {
            errors.add("guaranteedInHand must be -1 or in 0..2")
        }
        if (level.guaranteedInHand >= 0 && level.guaranteedPiece == null) {
            errors.add("guaranteedInHand set but guaranteedPiece is null")
        }

        // Pre-filled cells: all must be in bounds.
        for (cell in level.preFilled) {
            if (cell.col !in 0 until w || cell.row !in 0 until h) {
                errors.add("preFilled cell $cell is out of bounds for ${w}x$h")
            }
        }

        return if (errors.isEmpty()) LevelValidationResult.Ok
        else LevelValidationResult.Invalid(errors)
    }

    /**
     * Dynamic pass: run when the level is loaded for play.
     * Returns a list of warnings (non-fatal).
     */
    fun validateRuntime(level: LevelSpec): List<LevelRuntimeWarning> {
        val warnings = mutableListOf<LevelRuntimeWarning>()

        if (level.piecePool.isEmpty()) {
            warnings.add(LevelRuntimeWarning.EmptyPiecePool)
            return warnings
        }

        val unknown = level.piecePool
            .map { it.id }
            .filter { id -> id !in PieceShape.Library }
        unknown.forEach { warnings.add(LevelRuntimeWarning.UnknownShapeId(it)) }

        // Overlapping pre-fill.
        val seen = HashSet<Cell>()
        val overlapping = mutableListOf<Cell>()
        for (cell in level.preFilled) {
            if (!seen.add(cell)) overlapping.add(cell)
        }
        if (overlapping.isNotEmpty()) {
            warnings.add(LevelRuntimeWarning.OverlappingPreFill(overlapping))
        }

        // Unwinnable check: the board must have at least
        // one cell where some piece in the pool can be
        // placed. We simulate by pretending the board is
        // full EXCEPT for the cells outside level.preFilled.
        val occupied = level.preFilled.toHashSet()
        val (w, h) = level.boardSize
        val canPlayAnywhere = level.piecePool.any { shape ->
            val (sw, sh) = shape.boundingBox
            if (sw > w || sh > h) return@any false
            // Try every origin.
            for (y in 0..h - sh) {
                for (x in 0..w - sw) {
                    val cells = shape.absoluteCellsAt(Cell(x, y)).toList()
                    if (cells.none { it in occupied }) return@any true
                }
            }
            false
        }
        if (!canPlayAnywhere && level.preFilled.isNotEmpty()) {
            warnings.add(LevelRuntimeWarning.UnwinnableBoard)
        }

        if (level.guaranteedPiece != null && level.guaranteedInHand !in 0..2) {
            warnings.add(
                LevelRuntimeWarning.UnusedGuaranteedSlot(
                    level.guaranteedPiece.id,
                    level.guaranteedInHand,
                )
            )
        }

        return warnings
    }

    /** True iff every shape in the pool can fit on an empty 8x8 (sanity check). */
    fun allShapesFitOn8x8(level: LevelSpec): Boolean =
        level.piecePool.all { canPieceFitOnEmptyBoard(it, 8, 8) }
}
