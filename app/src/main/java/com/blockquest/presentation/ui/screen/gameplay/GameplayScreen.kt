// =====================================================================
// GameplayScreen.kt
// Block Quest — Gameplay screen with real drag-and-drop
// =====================================================================

package com.blockquest.presentation.ui.screen.gameplay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockquest.domain.board.BoardValidator
import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.CellState
import com.blockquest.domain.model.PieceShape
import com.blockquest.presentation.ui.screen.gameplay.drag.DragController
import com.blockquest.presentation.viewmodel.GameplayOverlay
import com.blockquest.presentation.viewmodel.GameplayViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.blockquest.domain.model.ComboType
import com.blockquest.domain.usecase.GameEvent
import com.blockquest.domain.usecase.GameState
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameplayScreen(
    levelId: String,
    onExit: () -> Unit,
    viewModel: GameplayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val lastStats by viewModel.lastStats.collectAsStateWithLifecycle()
    val dragController = remember { DragController() }
    val dragState by dragController.state.collectAsStateWithLifecycle()
    val level = state.level

    // ── Animation state ──────────────────────────────────────────────────
    // Cells currently running a clear-pop animation.
    var clearingCells   by remember { mutableStateOf(setOf<Pair<Int,Int>>()) }
    // Cells currently running a heat-unlock-pulse animation.
    var heatUnlockCells by remember { mutableStateOf(setOf<Pair<Int,Int>>()) }
    // Combo burst trigger — changes value each time a combo fires.
    var comboBurstKey   by remember { mutableStateOf<Any?>(null) }
    // Latest score delta for the "+N pts" pop-up.
    var lastScoreDelta  by remember { mutableIntStateOf(0) }
    var scoreTrigger    by remember { mutableStateOf<Any?>(null) }

    // Collect engine events and populate the animation sets.
    // Each set is reset to empty one frame after being set so the
    // LaunchedEffect inside CellView triggers exactly once per event.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is GameEvent.LinesCleared -> {
                    val board = viewModel.state.value.board
                    val cells = buildSet {
                        event.rows.forEach    { r -> for (x in 0 until board.width)  add(x to r) }
                        event.columns.forEach { c -> for (y in 0 until board.height) add(c to y) }
                        event.squares3x3.forEach { origin ->
                            for (dy in 0 until 3) for (dx in 0 until 3)
                                add((origin.col + dx) to (origin.row + dy))
                        }
                    }
                    if (cells.isNotEmpty()) {
                        clearingCells = cells
                        kotlinx.coroutines.delay(220.milliseconds)
                        clearingCells = emptySet()
                    }
                }
                is GameEvent.HeatUnlocked -> {
                    heatUnlockCells = event.cells.map { it.col to it.row }.toSet()
                    kotlinx.coroutines.delay(300.milliseconds)
                    heatUnlockCells = emptySet()
                }
                is GameEvent.ComboActivated -> {
                    // New unique key triggers ComboParticleOverlay
                    comboBurstKey = System.nanoTime()
                    lastScoreDelta = event.scoreDelta
                    scoreTrigger   = System.nanoTime()
                }
                is GameEvent.PiecePlaced -> {
                    // Show score delta for plain placements too (if meaningful)
                    if (event.cellCount >= 4) {
                        lastScoreDelta = event.cellCount * 10
                        scoreTrigger   = System.nanoTime()
                    }
                }
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nivel ${level?.levelNumber ?: 0}") },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Salir",
                        )
                    }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScoreBar(
                    score = state.score,
                    target = level?.targetScore ?: 0,
                    streak = state.streak,
                    maxStreak = state.maxStreak,
                    maxCombo = state.maxCombo,
                    timeRemainingMs = state.timeRemainingMs,
                )
                if (level != null) {
                    Box {
                        BoardWithDragDrop(
                            state = state,
                            dragController = dragController,
                            clearingCells = clearingCells,
                            heatUnlockCells = heatUnlockCells,
                            onCellTap = { col, row ->
                                // Fallback path: tap-tap. The user
                                // has presumably already picked a
                                // piece by tapping it; we use the
                                // currently selected index from
                                // the drag state.
                                if (dragState.trayIndex >= 0) {
                                    viewModel.place(dragState.trayIndex, col, row)
                                }
                            },
                            onDrop = { trayIndex, col, row ->
                                viewModel.place(trayIndex, col, row)
                            },
                            engineState = state,
                        )
                        // Particle burst — shown on combos.
                        ComboParticleOverlay(
                            trigger = comboBurstKey,
                            modifier = Modifier.matchParentSize(),
                        )
                        // Score delta pop-up — shown at the board centre.
                        ScorePopup(
                            points  = lastScoreDelta,
                            trigger = scoreTrigger,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                TrayRow(
                    tray = state.tray,
                    selectedIndex = if (dragState.isDragging) dragState.trayIndex else -1,
                    onPieceTap = { idx -> /* tap-tap path */ },
                    dragController = dragController,
                    onDrop = { trayIndex, col, row ->
                        viewModel.place(trayIndex, col, row)
                    },
                    engineState = state,
                )
                lastStats?.let { stats ->
                    StatsBanner(
                        combo = stats.combo,
                        streak = state.streak,
                        totalCleared = stats.totalCleared,
                        points = stats.totalPoints,
                    )
                }
            }

            // Floating drag ghost.
            if (dragState.isDragging && dragState.piece != null) {
                DragGhost(
                    piece = dragState.piece!!,
                    cellSize = dragState.cellSize,
                    offsetX = dragState.offsetX,
                    offsetY = dragState.offsetY,
                    isValid = dragState.isValid,
                )
            }

            // Overlays.
            when (val overlay = ui.pendingOverlay) {
                is GameplayOverlay.LevelComplete -> LevelCompleteDialog(
                    overlay = overlay,
                    onContinue = {
                        viewModel.dismissOverlay()
                        onExit()
                    },
                    onReplay = {
                        viewModel.restartLevel()
                        viewModel.dismissOverlay()
                    },
                )
                is GameplayOverlay.GameOver -> GameOverDialog(
                    overlay = overlay,
                    isAdInProgress = viewModel.isAdInProgress.collectAsStateWithLifecycle().value,
                    onWatchAd = { viewModel.continueWithAd() },
                    onSpendGems = { viewModel.continueWithGems() },
                    onRetry = {
                        viewModel.restartLevel()
                        viewModel.dismissOverlay()
                    },
                    onExit = {
                        viewModel.dismissOverlay()
                        onExit()
                    },
                )
                is GameplayOverlay.MissionCompleted -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissOverlay() },
                        title = { Text("Misión completada") },
                        text = { Text(overlay.mission.spec.description) },
                        confirmButton = {
                            Button(onClick = { viewModel.dismissOverlay() }) {
                                Text("OK")
                            }
                        },
                    )
                }
                GameplayOverlay.None -> { /* nothing */ }
            }
        }
    }
}

@Composable
private fun BoardWithDragDrop(
    state: GameState,
    dragController: DragController,
    clearingCells: Set<Pair<Int,Int>> = emptySet(),
    heatUnlockCells: Set<Pair<Int,Int>> = emptySet(),
    onCellTap: (Int, Int) -> Unit,
    onDrop: (Int, Int, Int) -> Unit,
    engineState: GameState,
) {
    val level = state.level ?: return
    val (w, h) = level.boardSize
    val dragState by dragController.state.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(w.toFloat() / h.toFloat())
    ) {
        val cellSizePx = with(LocalDensity.current) { maxWidth.toPx() / w }
        val boardOriginX = with(LocalDensity.current) { 0.dp.toPx() }
        val boardOriginY = with(LocalDensity.current) { 0.dp.toPx() }

        // Board.
        Column(modifier = Modifier.fillMaxSize()) {
            for (y in 0 until h) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (x in 0 until w) {
                        val cellState = state.board.get(x, y)
                        CellView(
                            state           = cellState,
                            modifier        = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(1.dp)
                                .clickable { onCellTap(x, y) },
                            isClearing      = clearingCells.contains(x to y),
                            isHeatUnlocking = heatUnlockCells.contains(x to y),
                        )
                    }
                }
            }
        }

        // Ghost highlight (the cells the piece would occupy if dropped here).
        if (dragState.isDragging && dragState.isValid) {
            val (pw, ph) = dragState.piece?.boundingBox ?: (1 to 1)
            for (i in 0 until pw) {
                for (j in 0 until ph) {
                    val cx = dragState.ghostCol + i
                    val cy = dragState.ghostRow + j
                    if (cx in 0 until w && cy in 0 until h) {
                        Box(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        x = (cellSizePx * cx).roundToInt(),
                                        y = (cellSizePx * cy).roundToInt()
                                    )
                                }
                                .size(with(LocalDensity.current) { cellSizePx.toDp() })
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
            }
        }

        // Board size publisher for DragController.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    dragController.state.value = dragController.state.value.copy(
                        cellSize     = cellSizePx,
                        boardOriginX = boardOriginX,
                        boardOriginY = boardOriginY,
                    )
                }
        )
    }
}

// CellView — renders a single board cell with clear animations.
//
// Transitions:
//  • Clear (Empty ← anything): scale 1→0 + alpha 1→0 in 200 ms, then
//    scale bounces back to 1 (empty slot "opens up").
//  • HeatLocked: a subtle breathing scale (1.0→0.92→1.0) so the player
//    can see the cell is frozen without it being distracting.
//  • Normal fill: instant (no animation needed — the board updates are
//    already fast enough).
@Composable
private fun CellView(
    state: CellState,
    modifier: Modifier,
    isClearing: Boolean = false,        // true for the one frame the engine fires LinesCleared
    isHeatUnlocking: Boolean = false,   // true for the one frame the engine fires HeatUnlocked
) {
    val color = when (state) {
        is CellState.Empty      -> MaterialTheme.colorScheme.surfaceVariant
        is CellState.Occupied   -> MaterialTheme.colorScheme.primary
        is CellState.Crystal    -> MaterialTheme.colorScheme.tertiary
        is CellState.HeatLocked -> MaterialTheme.colorScheme.secondary
        is CellState.BlackHole  -> Color(0xFF1E1B4B)
    }

    // Scale animatable — drives the clear pop and the heat-unlock flash.
    val scaleAnim = remember { Animatable(1f) }
    val alphaAnim = remember { Animatable(1f) }
    val scope     = rememberCoroutineScope()

    // Clear animation: shrink + fade, then snap back to full size.
    LaunchedEffect(isClearing) {
        if (isClearing) {
            scaleAnim.animateTo(0.2f, animationSpec = tween(180))
            alphaAnim.animateTo(0f,   animationSpec = tween(100))
            // Reset instantly (the board state is already Empty at this point).
            scaleAnim.snapTo(1f)
            alphaAnim.snapTo(1f)
        }
    }

    // HeatLocked unlock flash: quick pulse to signal the cell is free.
    LaunchedEffect(isHeatUnlocking) {
        if (isHeatUnlocking) {
            scaleAnim.animateTo(1.25f, animationSpec = tween(120))
            scaleAnim.animateTo(1f,    animationSpec = tween(160))
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                alpha  = alphaAnim.value
            }
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

@Composable
private fun DragGhost(
    piece: PieceShape,
    cellSize: Float,
    offsetX: Float,
    offsetY: Float,
    isValid: Boolean,
) {
    val cellSizeDp = with(LocalDensity.current) { cellSize.toDp() }
    val (pw, ph) = piece.boundingBox
    val width = cellSizeDp * pw
    val height = cellSizeDp * ph
    val color = if (isValid) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
    }
    
    // Usamos graphicsLayer para optimizar el movimiento sin recomponer todo
    Box(
        modifier = Modifier
            .size(width, height)
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY - 300f // Lift compensation
            }
            .background(color, RoundedCornerShape(4.dp))
            .alpha(0.9f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cW = size.width / pw
            val cH = size.height / ph
            piece.cells.forEach { cell ->
                drawRect(
                    color = Color.White.copy(alpha = 0.4f),
                    topLeft = Offset(cell.col * cW + 1f, cell.row * cH + 1f),
                    size = androidx.compose.ui.geometry.Size(cW - 2f, cH - 2f)
                )
            }
        }
    }
}

@Composable
private fun ScoreBar(
    score: Int,
    target: Int,
    streak: Int,
    maxStreak: Int,
    maxCombo: ComboType,
    timeRemainingMs: Long,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("🏆 $score / $target", style = MaterialTheme.typography.titleMedium)
            Text("🔥 $streak (max $maxStreak)", style = MaterialTheme.typography.titleMedium)
            val seconds = (timeRemainingMs / 1000L).toInt()
            Text("⏱ $seconds s", style = MaterialTheme.typography.titleMedium)
        }
        if (target > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (score.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (maxCombo != ComboType.Single) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Combo máximo: ${maxCombo.displayName.ifBlank { maxCombo.name }}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun StatsBanner(
    combo: ComboType,
    streak: Int,
    totalCleared: Int,
    points: Int,
) {
    val text = buildString {
        if (combo != ComboType.Single) {
            append("🎯 ${combo.displayName.ifBlank { combo.name }} (×${combo.multiplier})")
        }
        if (totalCleared > 0) {
            append("  ·  ✨ $totalCleared celdas")
        }
        if (points > 0) {
            append("  ·  +$points pts")
        }
    }
    if (text.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// TrayRow — source of drag gestures.
//
// Each piece box owns a detectDragGesturesAfterLongPress block that
// calls DragController directly. The controller already knows the
// board origin and cell size (pushed by BoardWithDragDrop via
// onSizeChanged); the tray only needs to pass absolute screen
// coordinates so the ghost renders correctly.
@Composable
private fun TrayRow(
    tray: List<PieceShape>,
    selectedIndex: Int,
    onPieceTap: (Int) -> Unit,
    dragController: DragController,
    onDrop: (trayIndex: Int, col: Int, row: Int) -> Unit,
    engineState: GameState,
) {
    val dragByController by dragController.state.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tray.forEachIndexed { index, shape ->
            val isSelected = index == selectedIndex
            val border = if (isSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else null

            // Use onGloballyPositioned to get the absolute screen position of the slot
            var slotPosition by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(
                            alpha = if (isSelected) 1f else 0.7f
                        )
                    )
                    .let { if (border != null) it.border(border, RoundedCornerShape(6.dp)) else it }
                    .onGloballyPositioned { coordinates ->
                        slotPosition = coordinates.positionInRoot()
                    }
                    .pointerInput(index, shape) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localOffset ->
                                // Calculamos la posición inicial relativa al contenedor raíz del juego
                                val ds = dragController.state.value
                                dragController.startDrag(
                                    trayIndex    = index,
                                    piece        = shape,
                                    touchX       = slotPosition.x + localOffset.x,
                                    touchY       = slotPosition.y + localOffset.y,
                                    cellSize     = ds.cellSize,
                                    boardOriginX = ds.boardOriginX,
                                    boardOriginY = ds.boardOriginY,
                                )
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                // Usamos coordenadas absolutas para el movimiento
                                val currentX = dragByController.offsetX + dragAmount.x
                                val currentY = dragByController.offsetY + dragAmount.y
                                
                                dragController.updateDrag(
                                    touchX   = currentX,
                                    touchY   = currentY,
                                )
                                
                                // Actualizamos la validez por separado para evitar lambdas pesadas en el controller
                                val ds = dragController.state.value
                                val isValid = if (ds.ghostCol >= 0 && ds.ghostRow >= 0) {
                                    BoardValidator.canPlace(engineState.board, shape, Cell(ds.ghostCol, ds.ghostRow))
                                } else false
                                dragController.setValid(isValid)
                            },
                            onDragEnd = {
                                val drop = dragController.endDrag()
                                if (drop != null) {
                                    onDrop(drop.trayIndex, drop.cell.col, drop.cell.row)
                                }
                            },
                            onDragCancel = {
                                dragController.cancel()
                            },
                        )
                    }
                    .clickable { onPieceTap(index) },
                contentAlignment = Alignment.Center,
            ) {
                PieceView(
                    shape = shape,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .alpha(if (isSelected && dragByController.isDragging) 0f else 1f), // Hide original when dragging
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PieceView(
    shape: PieceShape,
    modifier: Modifier,
    tint: Color,
) {
    val (w, h) = shape.boundingBox
    BoxWithConstraints(
        modifier = modifier
    ) {
        val cellW = maxWidth / w
        val cellH = maxHeight / h
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until h) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until w) {
                        val filled = shape.cells.any { it.col == c && it.row == r }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(0.5.dp)
                                .background(
                                    if (filled) tint else Color.Transparent,
                                    RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

// Extra imports kept separate for clarity.
private typealias BorderStroke = androidx.compose.foundation.BorderStroke
