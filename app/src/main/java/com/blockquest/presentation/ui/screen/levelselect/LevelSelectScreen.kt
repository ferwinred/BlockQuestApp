// =====================================================================
// LevelSelectScreen.kt
// Block Quest — Level selection map for a single world
// =====================================================================

package com.blockquest.presentation.ui.screen.levelselect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockquest.domain.model.LevelResult
import com.blockquest.domain.model.LevelSpec
import com.blockquest.presentation.viewmodel.LevelSelectViewModel
import kotlinx.coroutines.launch
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelSelectScreen(
    worldIndex: Int,
    onLevelSelected: (String, List<String>) -> Unit,
    onBack: () -> Unit,
    onMissionsClick: () -> Unit = {},
    viewModel: LevelSelectViewModel = hiltViewModel(),
) {
    val state by viewModel.ui(worldIndex).collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var previewLevel by remember { mutableStateOf<LevelSpec?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomNavBar(
                currentScreen = "Mapa",
                onMapClick = {},
                onMissionsClick = onMissionsClick,
                onHomeClick = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.padding(8.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text("Reintentar")
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            CurrencyBox(icon = "🪙", value = "1.250")
                        }
                        
                        Text(
                            text = state.worldName.uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Map Path
                    InteractiveWorldMap(
                        worldIndex = state.worldIndex,
                        levels = state.levels,
                        onLevelClick = { item ->
                            if (!item.isUnlocked) {
                                scope.launch {
                                    snackbar.showSnackbar("🔒 Completa los niveles anteriores")
                                }
                            } else {
                                previewLevel = item.spec
                            }
                        }
                    )
                }
            }

            LevelPreviewSheet(
                level     = previewLevel,
                result    = previewLevel?.let { l ->
                    state.levels.find { it.spec.levelId == l.levelId }?.result
                },
                viewModel = viewModel,
                inventory = state.inventory,
                onDismiss = { previewLevel = null },
                onPlay    = { levelId, boosters ->
                    previewLevel = null
                    onLevelSelected(levelId, boosters)
                },
            )
        }
    }
}

@Composable
private fun LevelNode(
    item: LevelSelectItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLocked = !item.isUnlocked
    
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Stump Icon
        Surface(
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF795548), // Brown stump
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLocked) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                } else if (item.result?.completed == true) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD93D))
                }
            }
        }
        
        Text(
            text = "Nivel ${item.spec.levelNumber}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun BottomNavBar(
    currentScreen: String,
    onMapClick: () -> Unit,
    onMissionsClick: () -> Unit,
    onHomeClick: () -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentScreen == "Mapa",
            onClick = onMapClick,
            icon = { Icon(Icons.Default.Map, contentDescription = null) },
            label = { Text("Mapa") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentScreen == "Misiones",
            onClick = onMissionsClick,
            icon = { Icon(Icons.Default.Star, contentDescription = null) },
            label = { Text("Misiones") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentScreen == "Inicio",
            onClick = onHomeClick,
            icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null) },
            label = { Text("Inicio") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = Color.Gray,
                unselectedTextColor = Color.Gray,
                indicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun CurrencyBox(icon: String, value: String) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LevelPreviewSheet(
    level: LevelSpec?,
    result: LevelResult?,
    viewModel: LevelSelectViewModel,
    inventory: Map<String, Int>,
    onDismiss: () -> Unit,
    onPlay: (String, List<String>) -> Unit,
) {
    AnimatedVisibility(
        visible = level != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(onClick = onDismiss)
        )
    }

    AnimatedVisibility(
        visible = level != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.fillMaxSize()
    ) {
        var selectedBoosters by remember { mutableStateOf(emptyList<String>()) }
        var boosterSelectionManual by remember { mutableStateOf(false) }
        
        Box(contentAlignment = Alignment.BottomCenter) {
            level?.let { lvl ->
                val topScores by viewModel.observeTopScores(lvl.levelId)
                    .collectAsStateWithLifecycle(initialValue = emptyList())

                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Nivel ${lvl.levelNumber}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) { i ->
                                Icon(
                                    imageVector = if (i < (result?.stars ?: 0)) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = null,
                                    tint = if (i < (result?.stars ?: 0)) Color(0xFFFFD93D) else Color.Gray,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Leaderboard Section
                        if (topScores.isNotEmpty()) {
                            Text("Mejores Puntuaciones", style = MaterialTheme.typography.titleSmall)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                topScores.forEachIndexed { index, entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${index + 1}. ${entry.displayName}", style = MaterialTheme.typography.bodyMedium)
                                        Text("${entry.score}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }


                        // Booster Selection
                        Text("Equipar Boosters (Máx 3)", style = MaterialTheme.typography.titleSmall)
                        val availableBoosters = inventory.filterValues { it > 0 }
                        if (availableBoosters.isEmpty()) {
                            Text("No tienes boosters", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableBoosters.forEach { (boosterId, count) ->
                                    val isSelected = selectedBoosters.contains(boosterId)
                                    val name = when(boosterId) {
                                        "booster_bomb" -> "Bomba"
                                        "booster_reroll" -> "Mano"
                                        "booster_smart_move" -> "Auto"
                                        "booster_double_score" -> "Puntosx2"
                                        "booster_time_freeze" -> "Reloj"
                                        else -> boosterId
                                    }
                                    Surface(
                                        color = if (isSelected) Color(0xFF4CAF50) else Color.LightGray,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.clickable {
                                            boosterSelectionManual = true
                                            if (isSelected) {
                                                selectedBoosters = selectedBoosters - boosterId
                                            } else if (selectedBoosters.size < 3) {
                                                selectedBoosters = selectedBoosters + boosterId
                                            }
                                        }
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(name, color = if(isSelected) Color.White else Color.Black, fontSize = 12.sp)
                                            Text("x$count", color = if(isSelected) Color.White else Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val finalBoosters = if (boosterSelectionManual) {
                                    selectedBoosters
                                } else {
                                    inventory.filterValues { it > 0 }.keys.shuffled().take(3)
                                }
                                onPlay(lvl.levelId, finalBoosters)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text("¡JUGAR!")
                        }
                        
                        TextButton(onClick = onDismiss) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}
