// =====================================================================
// GameplayScreen.kt
// Block Quest — Gameplay screen with improved visuals and distribution
// =====================================================================

package com.blockquest.presentation.ui.screen.gameplay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockquest.domain.board.BoardValidator
import com.blockquest.domain.model.Cell
import com.blockquest.domain.model.CellState
import com.blockquest.domain.model.ComboType
import com.blockquest.domain.model.PieceShape
import com.blockquest.domain.usecase.GameEvent
import com.blockquest.domain.usecase.GameState
import com.blockquest.presentation.ui.screen.gameplay.drag.DragController
import com.blockquest.presentation.viewmodel.GameplayOverlay
import com.blockquest.presentation.viewmodel.GameplayViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameplayScreen(
    levelId: String,
    onExit: () -> Unit,
    viewModel: GameplayViewModel = hiltViewModel(),
) {
    val _ignore = levelId
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val dragController = remember { DragController() }
    val dragState by dragController.state.collectAsStateWithLifecycle()
    val level = state.level

    var clearingCells   by remember { mutableStateOf(setOf<Pair<Int,Int>>()) }
    var heatUnlockCells by remember { mutableStateOf(setOf<Pair<Int,Int>>()) }
    var comboBurstKey   by remember { mutableStateOf<Any?>(null) }
    var lastScoreDelta  by remember { mutableIntStateOf(0) }
    var scoreTrigger    by remember { mutableStateOf<Any?>(null) }

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
                        delay(220.milliseconds)
                        clearingCells = emptySet()
                    }
                }
                is GameEvent.HeatUnlocked -> {
                    heatUnlockCells = event.cells.map { it.col to it.row }.toSet()
                    delay(300.milliseconds)
                    heatUnlockCells = emptySet()
                }
                is GameEvent.ComboActivated -> {
                    comboBurstKey = System.nanoTime()
                    lastScoreDelta = event.scoreDelta
                    scoreTrigger   = System.nanoTime()
                }
                is GameEvent.PiecePlaced -> {
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
        containerColor = Color(0xFFEBF5ED), // Pradera background
        topBar = {
            TopSection(
                worldName = "MUNDO PRADERA",
                levelNumber = level?.levelNumber ?: 0,
                onExit = onExit
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ScoreBar(
                    score = state.score,
                    target = level?.targetScore ?: 0,
                    timeRemainingMs = state.timeRemainingMs
                )

                if (level != null) {
                    Box(modifier = Modifier.padding(top = 10.dp)) {
                        BoardWithDragDrop(
                            state = state,
                            dragController = dragController,
                            clearingCells = clearingCells,
                            heatUnlockCells = heatUnlockCells,
                            onCellTap = { col, row ->
                                if (dragState.trayIndex >= 0) {
                                    viewModel.place(dragState.trayIndex, col, row)
                                }
                            }
                        )
                        ComboParticleOverlay(
                            trigger = comboBurstKey,
                            modifier = Modifier.matchParentSize(),
                        )
                        ScorePopup(
                            points  = lastScoreDelta,
                            trigger = scoreTrigger,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                Text(
                    text = "Toca una pieza para arrastrarla",
                    color = Color(0xFF3E6D43),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                TrayRow(
                    tray = state.tray,
                    selectedIndex = if (dragState.isDragging) dragState.trayIndex else -1,
                    onPieceTap = { /* tap-tap path */ },
                    dragController = dragController,
                    onDrop = { trayIndex, col, row ->
                        viewModel.place(trayIndex, col, row)
                    },
                    engineState = state,
                )

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = { /* Action */ },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3E6D43))
                ) {
                    Text("¡BIEN HECHO!", fontWeight = FontWeight.Black, fontSize = 20.sp)
                }

                Spacer(Modifier.height(20.dp))
            }

            if (dragState.isDragging && dragState.piece != null) {
                DragGhost(
                    piece = dragState.piece!!,
                    cellSize = dragState.cellSize,
                    offsetX = dragState.offsetX,
                    offsetY = dragState.offsetY,
                    isValid = dragState.isValid,
                )
            }

            // Dialogs
            when (val overlay = ui.pendingOverlay) {
                is GameplayOverlay.LevelComplete -> LevelCompleteDialog(
                    overlay = overlay,
                    onContinue = { viewModel.dismissOverlay(); onExit() },
                    onReplay = { viewModel.restartLevel(); viewModel.dismissOverlay() }
                )
                is GameplayOverlay.GameOver -> GameOverDialog(
                    overlay = overlay,
                    isAdInProgress = viewModel.isAdInProgress.collectAsStateWithLifecycle().value,
                    onWatchAd = { viewModel.continueWithAd() },
                    onSpendGems = { viewModel.continueWithGems() },
                    onRetry = { viewModel.restartLevel(); viewModel.dismissOverlay() },
                    onExit = { viewModel.dismissOverlay(); onExit() }
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
                GameplayOverlay.None -> { }
            }
        }
    }
}

@Composable
private fun TopSection(
    worldName: String,
    levelNumber: Int,
    onExit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onExit) {
            Icon(Icons.Default.Menu, contentDescription = null, tint = Color(0xFF3E6D43))
        }
        IconButton(onClick = { }) {
            Icon(Icons.Default.HelpOutline, contentDescription = null, tint = Color(0xFF3E6D43))
        }
        
        Surface(
            modifier = Modifier.weight(1f).height(40.dp),
            color = Color(0xFF3E6D43),
            shape = RoundedCornerShape(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "$worldName  Nivel $levelNumber",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🪙", fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "1.250",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun colorForPieceId(pieceId: String): Color {
    return when {
        pieceId.contains("dot") -> Color(0xFF2196F3) // Blue
        pieceId.contains("line") -> Color(0xFF2196F3) // Blue
        pieceId.contains("square") -> Color(0xFFFFB300) // Yellow/Orange
        pieceId.contains("t_block") -> Color(0xFFE53935) // Red
        pieceId.contains("s_block") -> Color(0xFF8E24AA) // Purple
        pieceId.contains("z_block") -> Color(0xFF8E24AA) // Purple
        pieceId.contains("l_corner") -> Color(0xFFFFB300) // Yellow/Orange
        else -> Color(0xFF2196F3)
    }
}

fun Modifier.beveledBlock(color: Color) = this.then(
    Modifier.drawBehind {
        val w = size.width
        val h = size.height
        val b = w * 0.15f

        drawRect(color)

        // Highlight
        val lightPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w - b, b)
            lineTo(b, b)
            lineTo(b, h - b)
            close()
        }
        drawPath(lightPath, Color.White.copy(alpha = 0.5f))

        // Shadow
        val darkPath = Path().apply {
            moveTo(w, 0f)
            lineTo(w, h)
            lineTo(0f, h)
            lineTo(b, h - b)
            lineTo(w - b, h - b)
            lineTo(w - b, b)
            close()
        }
        drawPath(darkPath, Color.Black.copy(alpha = 0.4f))
    }
)

@Composable
private fun CellView(
    state: CellState,
    modifier: Modifier,
    isClearing: Boolean = false,
    isHeatUnlocking: Boolean = false,
) {
    val color = when (state) {
        is CellState.Empty      -> Color.Transparent
        is CellState.Occupied   -> colorForPieceId(state.pieceId)
        is CellState.Crystal    -> Color(0xFF9D7EE8)
        is CellState.HeatLocked -> Color(0xFFFFB347)
        is CellState.BlackHole  -> Color(0xFF000000)
    }

    val scaleAnim = remember { Animatable(1f) }
    val alphaAnim = remember { Animatable(1f) }

    LaunchedEffect(isClearing) {
        if (isClearing) {
            scaleAnim.animateTo(0.2f, animationSpec = tween(180))
            alphaAnim.animateTo(0f,   animationSpec = tween(100))
            scaleAnim.snapTo(1f)
            alphaAnim.snapTo(1f)
        }
    }

    LaunchedEffect(isHeatUnlocking) {
        if (isHeatUnlocking) {
            scaleAnim.animateTo(1.25f, animationSpec = tween(120))
            scaleAnim.animateTo(1f,    animationSpec = tween(160))
        }
    }

    val isOccupied = state is CellState.Occupied || state is CellState.Crystal || state is CellState.HeatLocked

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleAnim.value
                scaleY = scaleAnim.value
                alpha  = alphaAnim.value
            }
            .then(if (isOccupied) Modifier.beveledBlock(color) else Modifier)
    )
}

@Composable
private fun BoardWithDragDrop(
    state: GameState,
    dragController: DragController,
    clearingCells: Set<Pair<Int,Int>> = emptySet(),
    heatUnlockCells: Set<Pair<Int,Int>> = emptySet(),
    onCellTap: (Int, Int) -> Unit,
) {
    val level = state.level ?: return
    val (w, h) = level.boardSize
    val dragState by dragController.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(w.toFloat() / h.toFloat())
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(8.dp)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                val cellSize = coords.size.width.toFloat() / w
                dragController.state.value = dragController.state.value.copy(
                    cellSize     = cellSize,
                    boardOriginX = pos.x,
                    boardOriginY = pos.y,
                )
            }
    ) {
        // Grid Lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellW = size.width / w
            val cellH = size.height / h
            for (i in 1 until w) {
                drawLine(Color.LightGray.copy(alpha = 0.5f), start = Offset(i * cellW, 0f), end = Offset(i * cellW, size.height))
            }
            for (i in 1 until h) {
                drawLine(Color.LightGray.copy(alpha = 0.5f), start = Offset(0f, i * cellH), end = Offset(size.width, i * cellH))
            }
        }

        val cellSizePx = dragState.cellSize

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
                                .clickable { onCellTap(x, y) },
                            isClearing      = clearingCells.contains(x to y),
                            isHeatUnlocking = heatUnlockCells.contains(x to y),
                        )
                    }
                }
            }
        }

        if (dragState.isDragging && dragState.isValid) {
            val (pw, ph) = dragState.piece?.boundingBox ?: (1 to 1)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val color = Color(0xFF6BCB77).copy(alpha = 0.2f)
                for (i in 0 until pw) {
                    for (j in 0 until ph) {
                        val cx = dragState.ghostCol + i
                        val cy = dragState.ghostRow + j
                        if (cx in 0 until w && cy in 0 until h) {
                            drawRect(
                                color = color,
                                topLeft = Offset(cellSizePx * cx, cellSizePx * cy),
                                size = Size(cellSizePx, cellSizePx)
                            )
                        }
                    }
                }
            }
        }
    }
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
    val color = if (isValid) colorForPieceId(piece.id).copy(alpha = 0.8f) else Color.Red.copy(alpha = 0.7f)
    
    Box(
        modifier = Modifier
            .size(width, height)
            .graphicsLayer {
                translationX = offsetX - (width.toPx() / 2)
                translationY = offsetY - 300f
            }
            .alpha(0.9f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until ph) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until pw) {
                        val filled = piece.cells.any { it.col == c && it.row == r }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(1.dp)
                                .then(if (filled) Modifier.beveledBlock(color) else Modifier)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBar(
    score: Int,
    target: Int,
    timeRemainingMs: Long,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "PUNTOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = score.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF3E6D43)
                        )
                        Text(
                            text = " / $target",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    val seconds = (timeRemainingMs / 1000L).toInt()
                    val mins = seconds / 60
                    val secs = seconds % 60
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "%02d:%02d".format(mins, secs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (score.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color = Color(0xFF4CAF50),
                trackColor = Color(0xFFE0E0E0)
            )
        }
    }
}

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
    val currentBoard = rememberUpdatedState(engineState.board)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tray.forEachIndexed { index, shape ->
            val isSelected = index == selectedIndex
            var slotPosition by remember { mutableStateOf(Offset.Zero) }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onGloballyPositioned { slotPosition = it.positionInRoot() }
                    .pointerInput(index, shape) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { localOffset ->
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
                                dragController.updateDragRelative(
                                    deltaX = dragAmount.x,
                                    deltaY = dragAmount.y,
                                )
                                val ds = dragController.state.value
                                val isValid = if (ds.ghostCol >= 0 && ds.ghostRow >= 0) {
                                    BoardValidator.canPlace(currentBoard.value, shape, Cell(ds.ghostCol, ds.ghostRow))
                                } else false
                                dragController.setValid(isValid)
                            },
                            onDragEnd = {
                                val drop = dragController.endDrag()
                                if (drop != null) onDrop(drop.trayIndex, drop.cell.col, drop.cell.row)
                            },
                            onDragCancel = { dragController.cancel() },
                        )
                    }
                    .clickable { onPieceTap(index) }
                    .padding(4.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PieceView(
                        shape = shape,
                        modifier = Modifier.padding(8.dp).alpha(if (isSelected && dragByController.isDragging) 0f else 1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PieceView(
    shape: PieceShape,
    modifier: Modifier,
) {
    val (w, h) = shape.boundingBox
    val pieceColor = colorForPieceId(shape.id)
    val trayCellSize = 22.dp

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.wrapContentSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (r in 0 until h) {
                Row(horizontalArrangement = Arrangement.Center) {
                    for (c in 0 until w) {
                        val filled = shape.cells.any { it.col == c && it.row == r }
                        Box(
                            modifier = Modifier
                                .size(trayCellSize)
                                .padding(1.dp)
                                .then(if (filled) Modifier.beveledBlock(pieceColor) else Modifier)
                        )
                    }
                }
            }
        }
    }
}
