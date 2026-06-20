// =====================================================================
// NavGraph.kt
// Block Quest — Navigation Compose graph
// =====================================================================

package com.blockquest.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blockquest.presentation.ui.screen.cosmetics.CosmeticsScreen
import com.blockquest.presentation.ui.screen.gameplay.GameplayScreen
import com.blockquest.presentation.ui.screen.levelselect.LevelSelectScreen
import com.blockquest.presentation.ui.screen.menu.MainMenuScreen
import com.blockquest.presentation.ui.screen.missions.MissionPanel
import com.blockquest.presentation.ui.screen.worldmap.WorldMapScreen

object Routes {
    const val MAIN_MENU    = "main_menu"
    const val WORLD_MAP    = "world_map"
    const val MISSIONS     = "missions"
    const val COSMETICS    = "cosmetics"
    const val LEVEL_SELECT = "level_select/{worldIndex}"
    const val GAMEPLAY     = "gameplay/{levelId}"

    fun levelSelect(worldIndex: Int) = "level_select/$worldIndex"
    fun gameplay(levelId: String)    = "gameplay/$levelId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockQuestNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.MAIN_MENU) {

        composable(Routes.MAIN_MENU) {
            MainMenuScreen(
                onPlayClicked      = { nav.navigate(Routes.WORLD_MAP) },
                onMissionsClicked  = { nav.navigate(Routes.MISSIONS) },
                onCosmeticsClicked = { nav.navigate(Routes.COSMETICS) },
            )
        }

        composable(Routes.WORLD_MAP) {
            // WorldMap now navigates to LevelSelect instead of directly to Gameplay.
            // The player taps a world card → sees the level grid → taps a bubble.
            WorldMapScreen(
                onLevelSelected = { levelId ->
                    // Legacy path kept for boss / direct deep-links:
                    // the WorldMap can still bypass LevelSelect if needed.
                    nav.navigate(Routes.gameplay(levelId))
                },
                onWorldTapped = { worldIndex ->
                    nav.navigate(Routes.levelSelect(worldIndex))
                },
                onBack = { nav.popBackStack() },
            )
        }

        // ── NEW: Level selection grid ─────────────────────────────────
        composable(Routes.LEVEL_SELECT) { entry ->
            val worldIndex = entry.arguments
                ?.getString("worldIndex")
                ?.toIntOrNull() ?: 0
            LevelSelectScreen(
                worldIndex      = worldIndex,
                onLevelSelected = { levelId ->
                    nav.navigate(Routes.gameplay(levelId))
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.MISSIONS) {
            androidx.compose.material3.Scaffold(
                topBar = {
                    androidx.compose.material3.TopAppBar(
                        title = { androidx.compose.material3.Text("Misiones") },
                        navigationIcon = {
                            androidx.compose.material3.IconButton(onClick = { nav.popBackStack() }) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Volver",
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    MissionPanel()
                }
            }
        }

        composable(Routes.COSMETICS) {
            CosmeticsScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.GAMEPLAY) { entry ->
            val levelId = entry.arguments?.getString("levelId") ?: return@composable
            GameplayScreen(
                levelId = levelId,
                onExit  = { nav.popBackStack() },
            )
        }
    }
}

