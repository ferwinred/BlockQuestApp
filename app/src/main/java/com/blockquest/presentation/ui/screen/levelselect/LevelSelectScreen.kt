// =====================================================================
// LevelSelectScreen.kt
// Block Quest — Level selection grid for a single world (Semana 3)
//
// Flow:
//   WorldMapScreen → LevelSelectScreen(worldIndex) → GameplayScreen(levelId)
//
// Features:
//  • LazyVerticalGrid of level bubbles (3 columns).
//  • Each bubble shows: number, lock icon, star rating (0–3).
//  • Tap a locked level → shows a "locked" snackbar.
//  • Tap an unlocked level → opens a bottom-sheet preview with best
//    score, stars, and a "Jugar" button.
//  • World progress bar at the top (completed / total).
// =====================================================================

package com.blockquest.presentation.ui.screen.levelselect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockquest.domain.model.LevelResult
import com.blockquest.domain.model.LevelSpec
import com.blockquest.presentation.viewmodel.LevelSelectViewModel
import kotlinx.coroutines.launch

// ── Screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSelectScreen(
    worldIndex: Int,
    onLevelSelected: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: LevelSelectViewModel = hiltViewModel(),
) {
    val state by viewModel.ui(worldIndex).collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Level whose preview bottom-sheet is currently open (null = closed).
    var previewLevel by remember { mutableStateOf<LevelSpec?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.worldName.ifBlank { "Mundo ${worldIndex + 1}" },
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                Text(
                    text = "Cargando niveles…",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    // ── World progress bar ────────────────────────────
                    WorldProgressHeader(
                        worldName    = state.worldName,
                        completed    = state.completedCount,
                        total        = state.totalCount,
                        stars        = state.totalStars,
                        maxStars     = state.maxStars,
                    )

                    Spacer(Modifier.height(16.dp))

                    // ── Level grid ───────────────────────────────────
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.levels, key = { it.spec.levelId }) { item ->
                            LevelBubble(
                                item    = item,
                                onClick = {
                                    if (!item.isUnlocked) {
                                        scope.launch {
                                            snackbar.showSnackbar("🔒 Completa los niveles anteriores")
                                        }
                                    } else {
                                        previewLevel = item.spec
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── Level preview sheet ───────────────────────────────────
            LevelPreviewSheet(
                level     = previewLevel,
                result    = previewLevel?.let { l ->
                    state.levels.find { it.spec.levelId == l.levelId }?.result
                },
                onDismiss = { previewLevel = null },
                onPlay    = { levelId ->
                    previewLevel = null
                    onLevelSelected(levelId)
                },
            )
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────

@Composable
private fun WorldProgressHeader(
    worldName: String,
    completed: Int,
    total: Int,
    stars: Int,
    maxStars: Int,
) {
    val fraction = if (total > 0) completed.toFloat() / total else 0f
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$completed / $total niveles",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "⭐ $stars / $maxStars",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
private fun LevelBubble(
    item: LevelSelectItem,
    onClick: () -> Unit,
) {
    val isLocked = !item.isUnlocked
    val bgColor = when {
        isLocked           -> MaterialTheme.colorScheme.surfaceVariant
        item.spec.isBoss   -> MaterialTheme.colorScheme.tertiary
        item.result?.completed == true -> MaterialTheme.colorScheme.primary
        else               -> MaterialTheme.colorScheme.primaryContainer
    }
    val fgColor = when {
        isLocked           -> MaterialTheme.colorScheme.onSurfaceVariant
        item.spec.isBoss   -> MaterialTheme.colorScheme.onTertiary
        item.result?.completed == true -> MaterialTheme.colorScheme.onPrimary
        else               -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(bgColor)
            .alpha(if (isLocked) 0.55f else 1f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLocked) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Bloqueado",
                tint = fgColor,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.spec.levelNumber.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = fgColor,
                )
                if (item.spec.isBoss) {
                    Text(
                        text = "BOSS",
                        style = MaterialTheme.typography.labelSmall,
                        color = fgColor,
                    )
                } else {
                    StarRow(stars = item.result?.stars ?: 0, max = 3, size = 10.dp)
                }
            }
        }
    }
}

@Composable
private fun StarRow(
    stars: Int,
    max: Int,
    size: androidx.compose.ui.unit.Dp,
) {
    Row {
        repeat(max) { i ->
            Icon(
                imageVector = if (i < stars) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (i < stars) MaterialTheme.colorScheme.inversePrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(size),
            )
        }
    }
}

// ── Level preview bottom sheet (lightweight AnimatedVisibility overlay) ───

@Composable
private fun LevelPreviewSheet(
    level: LevelSpec?,
    result: LevelResult?,
    onDismiss: () -> Unit,
    onPlay: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = level != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec  = tween(280),
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(220),
        ) + fadeOut(tween(150)),
    ) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss),
        )
    }

    AnimatedVisibility(
        visible = level != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec  = tween(300),
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(220),
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            level?.let { lvl ->
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Handle bar
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        )

                        // Title
                        Text(
                            text = if (lvl.isBoss) "⚔️ BOSS — Nivel ${lvl.levelNumber}"
                                   else "Nivel ${lvl.levelNumber}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )

                        // Stars
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(3) { i ->
                                Icon(
                                    imageVector = if (i < (result?.stars ?: 0)) Icons.Filled.Star
                                                  else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    tint = if (i < (result?.stars ?: 0))
                                               MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }

                        // Best score
                        if (result?.bestScore != null && result.bestScore > 0) {
                            Text(
                                text = "Mejor puntaje: ${result.bestScore}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Objective
                        Text(
                            text = "Objetivo: ${lvl.objective.name.lowercase()
                                .replaceFirstChar { it.uppercase() }
                                .replace('_', ' ')}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Cancelar")
                            }
                            Button(
                                onClick  = { onPlay(lvl.levelId) },
                                modifier = Modifier.weight(2f),
                            ) {
                                Text(
                                    text = if (result?.completed == true) "🔄 Rejugar" else "▶ Jugar",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
