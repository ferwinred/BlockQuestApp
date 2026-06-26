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

import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMapScreen(
    onWorldTapped: (Int) -> Unit = {},
    onBack: () -> Unit,
    onMissionsClick: () -> Unit = {},
    viewModel: WorldMapViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        bottomBar = {
            BottomNavBar(
                currentScreen = "Mapa",
                onMapClick = {},
                onMissionsClick = onMissionsClick,
                onHomeClick = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "MUNDOS",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (state.isLoading) {
                Text("Cargando…")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(state.worldStates, key = { it.definition.worldIndex }) { ws ->
                        WorldCard(
                            worldState = ws,
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
    onWorldHeaderTap: () -> Unit = {},
) {
    val alpha = if (worldState.isUnlocked) 1f else 0.6f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(enabled = worldState.isUnlocked, onClick = onWorldHeaderTap),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (worldState.isUnlocked) worldState.definition.displayName.uppercase()
                    else "BLOQUEADO",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = worldState.definition.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            
            if (worldState.isUnlocked) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "⭐ ${worldState.totalStars}/${worldState.maxStars}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD93D)
                    )
                    Text(
                        text = "${(worldState.completionFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray)
            }
        }
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
