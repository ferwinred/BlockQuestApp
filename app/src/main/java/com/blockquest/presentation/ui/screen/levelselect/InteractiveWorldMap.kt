package com.blockquest.presentation.ui.screen.levelselect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun InteractiveWorldMap(
    worldIndex: Int,
    levels: List<LevelSelectItem>,
    onLevelClick: (LevelSelectItem) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    // Sizes in Dp
    val mapWidthDp = 1000.dp
    val mapHeightDp = 4000.dp

    // Convert to Px for calculations
    val mapWidthPx = with(density) { mapWidthDp.toPx() }
    val mapHeightPx = with(density) { mapHeightDp.toPx() }

    val backgroundColor = when (worldIndex) {
        0 -> Color(0xFFE8F5E9) // Pradera
        1 -> Color(0xFFFFF3E0) // Desierto
        2 -> Color(0xFFEFEBE9) // Bosque
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 2f)
                    
                    // Simple constraints so they don't pan too far into the void
                    val maxPanX = (mapWidthPx * scale) / 2f
                    val maxPanY = (mapHeightPx * scale) / 2f
                    
                    val newX = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                    val newY = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                    
                    offset = Offset(newX, newY)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .size(width = mapWidthDp, height = mapHeightDp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            // Draw connections between levels
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                
                var first = true
                levels.forEachIndexed { index, _ ->
                    val y = mapHeightPx - (index + 1) * (mapHeightPx / 32)
                    val x = (mapWidthPx / 2) + sin(index * 0.8f) * (mapWidthPx * 0.3f)
                    
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                }
                
                drawPath(
                    path = path,
                    color = Color.DarkGray.copy(alpha = 0.5f),
                    style = Stroke(
                        width = 8.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                    )
                )
            }

            // Draw level nodes and assets
            levels.forEachIndexed { index, item ->
                // Start from bottom (level 1) to top (level 30)
                val yDp = mapHeightDp - ((index + 1) * (mapHeightDp.value / 32)).dp
                val xDp = (mapWidthDp / 2) + (sin(index * 0.8f) * (mapWidthDp.value * 0.3f)).dp

                Box(
                    modifier = Modifier.offset(x = xDp - 32.dp, y = yDp - 32.dp) // center node
                ) {
                    LevelNode(item = item, onClick = { onLevelClick(item) })
                }
            }
            
            // Draw dummy assets based on world index
            MapAssets(worldIndex = worldIndex, mapWidth = mapWidthDp, mapHeight = mapHeightDp)
        }
    }
}
