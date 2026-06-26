// =====================================================================
// DragController.kt
// Block Quest — Drag-and-drop state controller (presentation layer)
// =====================================================================
//
// The Gameplay screen needs a drag-and-drop UI. Compose's
// pointer input is too low-level to put inline in the
// `GameplayScreen` Composable, so we wrap it in a small
// state machine:
//
//   * The player presses a tray piece → `startDrag(i)`.
//   * The player moves the finger → `updateDrag(offset)`.
//   * The player releases → `endDrag()` returns either a
//     valid (trayIndex, targetCell) pair, or null if the
//     drop missed the board.
//
// The controller also exposes a "ghost" position that the
// UI uses to render a translucent preview of the piece
// following the finger.
// =====================================================================

package com.blockquest.presentation.ui.screen.gameplay.drag

import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.PieceShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt

data class DragState(
    val isDragging: Boolean = false,
    val trayIndex: Int = -1,
    val piece: PieceShape? = null,
    val originX: Float = 0f,
    val originY: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val cellSize: Float = 0f,
    val boardOriginX: Float = 0f,
    val boardOriginY: Float = 0f,
    val ghostCol: Int = -1,
    val ghostRow: Int = -1,
    val isValid: Boolean = false,
)

class DragController {

    private val _state = MutableStateFlow(DragState())
    val state = _state

    fun startDrag(
        trayIndex: Int,
        piece: PieceShape,
        touchX: Float,
        touchY: Float,
        cellSize: Float,
        boardOriginX: Float,
        boardOriginY: Float,
    ) {
        _state.value = _state.value.copy(
            isDragging = true,
            trayIndex = trayIndex,
            piece = piece,
            originX = touchX,
            originY = touchY,
            offsetX = touchX,
            offsetY = touchY,
            cellSize = cellSize,
            boardOriginX = boardOriginX,
            boardOriginY = boardOriginY,
            ghostCol = -1,
            ghostRow = -1,
            isValid = false,
        )
    }

    /**
     * Update the drag offset. Recomputes the ghost cell
     * position. Validity is now handled in the UI/ViewModel 
     * to avoid passing lambdas that capture state.
     */
    fun updateDrag(
        touchX: Float,
        touchY: Float,
    ) {
        val s = _state.value
        if (!s.isDragging) return
        
        val piece = s.piece ?: return
        val cs = s.cellSize
        if (cs <= 0f) return
        val (pw, ph) = piece.boundingBox

        val liftPx = 300f 
        val centerX = touchX
        val centerY = touchY - liftPx
        
        val ghostX = ((centerX - s.boardOriginX) / cs).roundToInt() - pw / 2
        val ghostY = ((centerY - s.boardOriginY) / cs).roundToInt() - ph / 2
        
        _state.value = s.copy(
            offsetX = touchX,
            offsetY = touchY,
            ghostCol = ghostX,
            ghostRow = ghostY,
        )
    }

    fun setValid(isValid: Boolean) {
        if (_state.value.isValid != isValid) {
            _state.value = _state.value.copy(isValid = isValid)
        }
    }

    /**
     * End the drag. Returns (trayIndex, targetCell) if the
     * drop was over a valid board cell, otherwise null.
     */
    fun endDrag(): DropResult? {
        val s = _state.value
        if (!s.isDragging) return null
        val piece = s.piece ?: run { _state.value = DragState(); return null }
        val result = if (s.isValid) {
            DropResult(
                trayIndex = s.trayIndex,
                cell = Cell(s.ghostCol, s.ghostRow),
            )
        } else null
        _state.value = DragState()
        return result
    }

    fun cancel() {
        _state.value = DragState()
    }
}

data class DropResult(
    val trayIndex: Int,
    val cell: Cell,
)
