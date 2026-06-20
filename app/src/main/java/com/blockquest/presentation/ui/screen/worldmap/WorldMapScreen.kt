// =====================================================================
// WorldMapScreen.kt
// Block Quest — World map (Compose)
// =====================================================================

package com.blockquest.presentation.ui.screen.worldmap

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.WorldDefinition
import com.blockquest.domain.model.WorldState
import com.blockquest.presentation.viewmodel.WorldMapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMapScreen(
    onLevelSelected: (String) -> Unit,
    onWorldTapped: (Int) -> Unit = {},   // NEW: navigates to LevelSelectScreen
    onBack: () -> Unit,
    viewModel: WorldMapViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mundo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
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
                .padding(16.dp)
        ) {
            if (state.isLoading) {
                Text("Cargando…")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.worldStates, key = { it.definition.worldIndex }) { ws ->
                        WorldCard(
                            worldState = ws,
                            levels = state.levels
                                .filter { it.worldIndex == ws.definition.worldIndex }
                                .sortedBy { it.levelNumber },
                            completedLevelIds = state.progression?.results
                                ?.filterValues { it.completed }
                                ?.keys
                                ?: emptySet(),
                            onLevelTap = { level ->
                                if (ws.isUnlocked) {
                                    viewModel.selectLevel(level, onLevelSelected)
                                }
                            },
                            // Tapping the world header opens LevelSelectScreen.
                            onWorldHeaderTap = {
                                if (ws.isUnlocked) onWorldTapped(ws.definition.worldIndex)
                            },
                        )
                    }
                }
            }
        }
    }

    state.pendingWorldUnlock?.let { world ->
        WorldUnlockDialog(
            world = world,
            onDismiss = { viewModel.acknowledgeWorldUnlock() },
        )
    }
}

@Composable
private fun WorldCard(
    worldState: WorldState,
    levels: List<LevelSpec>,
    completedLevelIds: Set<String>,
    onLevelTap: (LevelSpec) -> Unit,
    onWorldHeaderTap: () -> Unit = {},
) {
    val alpha = if (worldState.isUnlocked) 1f else 0.5f
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = worldState.isUnlocked, onClick = onWorldHeaderTap),
            ) {
                Column {
                    Text(
                        text = if (worldState.isUnlocked) worldState.definition.displayName
                        else "🔒 ${worldState.definition.worldIndex}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = worldState.definition.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (worldState.isUnlocked) {
                    Text(
                        text = "⭐ ${worldState.totalStars}/${worldState.maxStars}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (worldState.isUnlocked) {
                Spacer()
                LinearProgressIndicator(
                    progress = { worldState.completionFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer()
                // Grid of level buttons (3 per row).
                levels.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        row.forEach { level ->
                            LevelButton(
                                level = level,
                                completed = level.levelId in completedLevelIds,
                                stars = 0,  // we don't have per-level stars in this view
                                onClick = { onLevelTap(level) },
                                modifier = Modifier.weight(1f).padding(4.dp),
                            )
                        }
                        repeat(3 - row.size) {
                            Box(modifier = Modifier.weight(1f).padding(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelButton(
    level: LevelSpec,
    completed: Boolean,
    stars: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        completed -> MaterialTheme.colorScheme.primary
        level.isBoss -> MaterialTheme.colorScheme.tertiary
        level.isMilestone -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val foreground = when {
        completed -> MaterialTheme.colorScheme.onPrimary
        level.isBoss -> MaterialTheme.colorScheme.onTertiary
        level.isMilestone -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = level.levelNumber.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = foreground,
                fontWeight = FontWeight.Bold,
            )
            if (level.isBoss) {
                Text("BOSS", style = MaterialTheme.typography.labelSmall, color = foreground)
            }
        }
    }
}

@Composable
private fun WorldUnlockDialog(
    world: WorldDefinition,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🌟 ¡Mundo desbloqueado!",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = world.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer()
                Text(world.tagline, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("¡Vamos!") } },
    )
}

@Composable
private fun Spacer() = Spacer(
    modifier = Modifier.padding(vertical = 4.dp)
)
