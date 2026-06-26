// =====================================================================
// FirestoreSeeder.kt
// Block Quest — Firestore data seeder (DEBUG only)
// =====================================================================
//
// USO: Llamar una sola vez desde BlockQuestApp.onCreate() en debug.
// Este seeder escribe solo en colecciones de solo lectura del cliente
// (worlds, levels, missions, cosmetics, dailyRewardConfig).
// Los datos de jugador (players/) los crea FirebasePlayerRepository.
//
// IMPORTANTE: Eliminar o no llamar en release builds.
// Las Security Rules bloquean escrituras en estas colecciones
// desde el cliente en producción — este seeder requiere que las
// rules permitan escritura temporalmente en debug, o bien que
// se ejecute con un usuario admin (Firebase Emulator).
//
// Estructura de colecciones sembradas:
//   /worlds/{worldId}                 ← WorldDto
//   /levels/{levelId}                 ← LevelDto (con boardSize como map)
//   /missions/{missionId}             ← MissionDto
//   /cosmetics/skins/{skinId}         ← SkinDto
//   /cosmetics/titles/{titleId}       ← TitleDto
//   /config/dailyReward               ← DailyRewardConfigDto
// =====================================================================

package com.blockquest.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

object FirestoreSeeder {

    /**
     * Siembra todos los datos del juego si las colecciones están vacías.
     * Llama esto solo en debug desde BlockQuestApp.onCreate().
     *
     * Uso en BlockQuestApp.kt:
     * ```
     * if (BuildConfig.DEBUG) {
     *     val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
     *     FirestoreSeeder.seedIfNeeded(FirebaseFirestore.getInstance(), scope)
     * }
     * ```
     */
    fun seedIfNeeded(firestore: FirebaseFirestore, scope: CoroutineScope) {
        scope.launch {
            try {
                val worldsExist = firestore.collection("worlds").get().await()
                if (!worldsExist.isEmpty) {
                    Timber.d("FirestoreSeeder: data already exists, skipping seed.")
                    return@launch
                }
                Timber.d("FirestoreSeeder: seeding Firestore...")
                seedWorlds(firestore)
                seedLevels(firestore)
                seedMissions(firestore)
                seedSkins(firestore)
                seedTitles(firestore)
                seedDailyRewardConfig(firestore)
                Timber.d("FirestoreSeeder: seeding complete ✅")
            } catch (e: Exception) {
                Timber.e(e, "FirestoreSeeder: failed — ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // WORLDS — campos exactos de WorldDto
    // ─────────────────────────────────────────────────────────────────

    private suspend fun seedWorlds(firestore: FirebaseFirestore) {
        val worlds = listOf(
            mapOf(
                "worldIndex" to 0,
                "worldId" to "world_0",
                "displayName" to "Pradera",
                "tagline" to "El inicio del viaje",
                "themeName" to "pradera",
                "levelCount" to 30,
                "unlockLevelId" to null,
                "unlockStarsRequired" to 0,
                "ambientMusicId" to null,
                "backgroundImageId" to null,
                "schemaVersion" to 1,
            ),
            mapOf(
                "worldIndex" to 1,
                "worldId" to "world_1",
                "displayName" to "Desierto",
                "tagline" to "El calor del desafío",
                "themeName" to "desierto",
                "levelCount" to 30,
                "unlockLevelId" to "world0_level30",
                "unlockStarsRequired" to 90,
                "ambientMusicId" to null,
                "backgroundImageId" to null,
                "schemaVersion" to 1,
            ),
            mapOf(
                "worldIndex" to 2,
                "worldId" to "world_2",
                "displayName" to "Bosque",
                "tagline" to "La profundidad del verde",
                "themeName" to "bosque",
                "levelCount" to 30,
                "unlockLevelId" to "world1_level60",
                "unlockStarsRequired" to 90,
                "ambientMusicId" to null,
                "backgroundImageId" to null,
                "schemaVersion" to 1,
            ),
        )
        worlds.forEach { world ->
            firestore.collection("worlds")
                .document(world["worldId"] as String)
                .set(world, SetOptions.merge())
                .await()
        }
        Timber.d("FirestoreSeeder: worlds seeded (${worlds.size})")
    }

    // ─────────────────────────────────────────────────────────────────
    // LEVELS — campos exactos de LevelDto
    // boardSize es un map anidado { width, height }
    // piecePool usa los IDs del PieceShape.Library
    // IDs válidos: dot_1x1, line_h1x2, line_h1x3, line_h1x4, line_h1x5,
    //              line_v2x1, line_v3x1, line_v4x1, line_v5x1,
    //              square_2x2, square_3x3, l_corner_s, l_corner_l,
    //              t_block, s_block, z_block, u_shape, rect_2x4,
    //              cross_5x5, scythe
    // ─────────────────────────────────────────────────────────────────

        private suspend fun seedLevels(firestore: FirebaseFirestore) {
        val levels = buildList {
            // Mundo 0 - Pradera (1-30)
            for (i in 1..30) {
                val isTutorial = i <= 4
                val isSpecial = i == 10 || i == 20
                val isBoss = i == 30
                add(level(
                    id = "world0_level$i", number = i, world = 0,
                    targetScore = if (isTutorial) 300 * i else 1000 * i,
                    timeLimit = if (isSpecial || isBoss) (if (isBoss) 120.0 else 60.0) else 0.0,
                    piecePool = if (isBoss) listOf("line_h1x5", "square_3x3", "cross_5x5") else listOf("line_h1x2", "square_2x2"),
                    rewardCoins = if (isBoss) 200 else if (isSpecial) 100 else 50,
                    rewardGems = if (isBoss) 5 else if (isSpecial) 2 else 0,
                    difficulty = 1.0 + (i * 0.1),
                    isMilestone = isSpecial || isBoss,
                    isBoss = isBoss,
                    levelType = if (isTutorial) "Tutorial" else if (isSpecial) "Challenge" else if (isBoss) "Boss" else "Standard"
                ))
            }
            // Mundo 1 - Bosque (31-60)
            for (i in 31..60) {
                val isTutorial = i <= 34
                val isSpecial = i == 40 || i == 50
                val isBoss = i == 60
                add(level(
                    id = "world1_level$i", number = i, world = 1,
                    targetScore = if (isTutorial) 500 * (i-30) else 1500 * (i-30),
                    timeLimit = if (isSpecial || isBoss) (if (isBoss) 150.0 else 90.0) else 0.0,
                    piecePool = if (isBoss) listOf("line_h1x5", "square_3x3", "cross_5x5", "u_shape") else listOf("line_h1x3", "square_2x2", "t_block"),
                    rewardCoins = if (isBoss) 300 else if (isSpecial) 150 else 75,
                    rewardGems = if (isBoss) 10 else if (isSpecial) 3 else 0,
                    difficulty = 2.0 + ((i-30) * 0.15),
                    isMilestone = isSpecial || isBoss,
                    isBoss = isBoss,
                    levelType = if (isTutorial) "Tutorial" else if (isSpecial) "Challenge" else if (isBoss) "Boss" else "Standard"
                ))
            }
            // Mundo 2 - Desierto (61-90)
            for (i in 61..90) {
                val isTutorial = i <= 64
                val isSpecial = i == 70 || i == 80
                val isBoss = i == 90
                add(level(
                    id = "world2_level$i", number = i, world = 2,
                    targetScore = if (isTutorial) 800 * (i-60) else 2000 * (i-60),
                    timeLimit = if (isSpecial || isBoss) (if (isBoss) 180.0 else 100.0) else 0.0,
                    piecePool = if (isBoss) listOf("line_h1x5", "square_3x3", "cross_5x5", "scythe", "z_block") else listOf("line_h1x4", "square_2x2", "l_corner_s"),
                    rewardCoins = if (isBoss) 400 else if (isSpecial) 200 else 100,
                    rewardGems = if (isBoss) 15 else if (isSpecial) 5 else 0,
                    difficulty = 3.0 + ((i-60) * 0.2),
                    isMilestone = isSpecial || isBoss,
                    isBoss = isBoss,
                    levelType = if (isTutorial) "Tutorial" else if (isSpecial) "Challenge" else if (isBoss) "Boss" else "Standard"
                ))
            }
        }
        
        levels.forEach { level ->
            firestore.collection("levels")
                .document(level["levelId"] as String)
                .set(level, SetOptions.merge())
                .await()
        }
        Timber.d("FirestoreSeeder: levels seeded (${levels.size})")
    }

    private suspend fun seedMissions(firestore: FirebaseFirestore) {
        val missions = listOf(
            // ── Diarias ──
            mission("daily_place_50", "PlacePieces", 50, "Daily",
                "Coloca 50 piezas en cualquier nivel", rewardCoins = 30),
            mission("daily_clear_5_lines", "ClearLines", 5, "Daily",
                "Limpia 5 líneas en un día", rewardCoins = 50),
            mission("daily_score_2000", "ReachScore", 2000, "Daily",
                "Alcanza 2,000 puntos en cualquier nivel", rewardCoins = 40),
            mission("daily_combo_3", "AchieveCombos", 3, "Daily",
                "Logra 3 combos en un día", rewardCoins = 60),
            mission("daily_complete_3", "CompleteLevels", 3, "Daily",
                "Completa 3 niveles en un día", rewardCoins = 70),

            // ── Semanales ──
            mission("weekly_place_500", "PlacePieces", 500, "Weekly",
                "Coloca 500 piezas esta semana", rewardCoins = 150, rewardGems = 2),
            mission("weekly_clear_50_lines", "ClearLines", 50, "Weekly",
                "Limpia 50 líneas esta semana", rewardCoins = 200, rewardGems = 3),
            mission("weekly_complete_15", "CompleteLevels", 15, "Weekly",
                "Completa 15 niveles esta semana", rewardCoins = 250, rewardGems = 5),
            mission("weekly_score_20000", "ReachScore", 20000, "Weekly",
                "Acumula 20,000 puntos esta semana", rewardCoins = 180, rewardGems = 2),

            // ── Logros (Achievement) ──
            mission("ach_first_level", "CompleteLevels", 1, "Achievement",
                "Completa tu primer nivel", rewardCoins = 100,
                rewardTitleId = "novata"),
            mission("ach_complete_10", "CompleteLevels", 10, "Achievement",
                "Completa 10 niveles", rewardCoins = 200,
                rewardTitleId = "bloquera"),
            mission("ach_place_1000", "PlacePieces", 1000, "Achievement",
                "Coloca 1,000 piezas en total", rewardCoins = 300),
            mission("ach_clear_100_lines", "ClearLines", 100, "Achievement",
                "Limpia 100 líneas en total", rewardCoins = 400, rewardGems = 10),
            mission("ach_ultra_combo", "AchieveCombos", 1, "Achievement",
                "Logra un combo Ultra (6x)", rewardCoins = 500,
                rewardTitleId = "combo_master"),
            mission("ach_clear_10_squares", "ClearSquares", 10, "Achievement",
                "Limpia 10 cuadros 3x3", rewardCoins = 250, rewardGems = 5),
            mission("ach_complete_world0", "CompleteWorld", 1, "Achievement",
                "Completa todos los niveles de Pradera", rewardCoins = 600, rewardGems = 15,
                worldIndex = 0),
            mission("ach_complete_world1", "CompleteWorld", 1, "Achievement",
                "Completa todos los niveles de Desierto", rewardCoins = 800, rewardGems = 20,
                worldIndex = 1),
            mission("ach_complete_world2", "CompleteWorld", 1, "Achievement",
                "Completa todos los niveles de Bosque", rewardCoins = 1000, rewardGems = 25,
                worldIndex = 2),
            mission("ach_combo_50", "AchieveCombos", 50, "Achievement",
                "Logra 50 combos", rewardCoins = 400, rewardGems = 10),
            mission("ach_streak_7", "ClaimDailyStreak", 7, "Achievement",
                "Reclama 7 recompensas diarias seguidas", rewardCoins = 500, rewardGems = 15),
            mission("ach_space_streak", "AchieveStreak", 20, "Achievement",
                "Consigue una racha de 20 movimientos seguidos", rewardCoins = 1000, rewardGems = 50,
                rewardSkinId = "espacio"),
        )

        missions.forEach { m ->
            firestore.collection("missions")
                .document(m["missionId"] as String)
                .set(m, SetOptions.merge())
                .await()
        }
        Timber.d("FirestoreSeeder: missions seeded (${missions.size})")
    }

    private fun mission(
        id: String,
        type: String,
        target: Int,
        cadence: String,
        description: String,
        rewardCoins: Int = 0,
        rewardGems: Int = 0,
        rewardSkinId: String? = null,
        rewardTitleId: String? = null,
        minCombo: String? = null,
        worldIndex: Int? = null,
        weight: Int = 1,
    ): Map<String, Any?> = mapOf(
        "missionId" to id,
        "type" to type,
        "target" to target,
        "minCombo" to minCombo,
        "worldIndex" to worldIndex,
        "cadence" to cadence,
        "description" to description,
        "rewardCoins" to rewardCoins,
        "rewardGems" to rewardGems,
        "rewardSkinId" to rewardSkinId,
        "rewardTitleId" to rewardTitleId,
        "weight" to weight,
        "schemaVersion" to 1,
    )

    // ─────────────────────────────────────────────────────────────────
    // SKINS — campos exactos de SkinDto
    // rarity: Common | Uncommon | Rare | Epic | Legendary | Mythic
    // source: Default | LevelReward | MissionReward | DailyReward |
    //         IAP | Promotional | Achievement | Debug
    // ─────────────────────────────────────────────────────────────────

    private suspend fun seedSkins(firestore: FirebaseFirestore) {
        val skins = listOf(
            skin("default", "Pradera", "pradera",
                rarity = "Common", source = "Default", sortOrder = 0),
            skin("bosque", "Bosque", "bosque",
                rarity = "Uncommon", source = "LevelReward", sortOrder = 10,
                unlockHint = "Gana 3 estrellas en 5 niveles de Pradera"),
            skin("desierto", "Desierto", "desierto",
                rarity = "Rare", source = "LevelReward", sortOrder = 20,
                unlockHint = "Gana 3 estrellas en el nivel 10 de Pradera"),
            skin("espacio", "Espacio", "espacio",
                rarity = "Epic", source = "MissionReward", sortOrder = 30,
                unlockHint = "Completa 20 misiones de racha"),
            skin("neon", "Neón", "neon",
                rarity = "Rare", source = "IAP", sortOrder = 15,
                unlockHint = "Paquete de bienvenida"),
            skin("lucky", "Lucky", "lucky",
                rarity = "Mythic", source = "DailyReward", sortOrder = 50,
                unlockHint = "Reclama 7 recompensas diarias seguidas"),
            skin("final", "Final", "final",
                rarity = "Legendary", source = "Promotional", sortOrder = 40,
                unlockHint = "Disponible durante eventos especiales"),
        )

        skins.forEach { s ->
            firestore.collection("cosmetics").document("skins")
                .collection("items")
                .document(s["skinId"] as String)
                .set(s, SetOptions.merge())
                .await()
        }
        Timber.d("FirestoreSeeder: skins seeded (${skins.size})")
    }

    private fun skin(
        id: String,
        displayName: String,
        themeName: String,
        rarity: String,
        source: String,
        sortOrder: Int,
        boardTint: Long? = null,
        particlePreset: String? = null,
        unlockHint: String? = null,
    ): Map<String, Any?> = mapOf(
        "skinId" to id,
        "displayName" to displayName,
        "themeName" to themeName,
        "boardTint" to boardTint,
        "particlePreset" to particlePreset,
        "rarity" to rarity,
        "source" to source,
        "unlockHint" to unlockHint,
        "sortOrder" to sortOrder,
    )

    // ─────────────────────────────────────────────────────────────────
    // TITLES — campos exactos de TitleDto
    // position: Prefix | Suffix
    // ─────────────────────────────────────────────────────────────────

    private suspend fun seedTitles(firestore: FirebaseFirestore) {
        val titles = listOf(
            title("novata", "Novata", "Prefix", "🌱 Novata",
                rarity = "Common", source = "Default", sortOrder = 0),
            title("bloquera", "Bloquera", "Prefix", "🧱 Bloquera",
                rarity = "Uncommon", source = "LevelReward", sortOrder = 10,
                unlockHint = "Completa 10 niveles"),
            title("combo_master", "Combo Master", "Suffix", "Combo Master",
                rarity = "Rare", source = "MissionReward", sortOrder = 20,
                unlockHint = "Logra un combo Ultra"),
            title("racha", "En Racha", "Suffix", "🔥 En Racha",
                rarity = "Rare", source = "MissionReward", sortOrder = 21,
                unlockHint = "Logra una racha de 10"),
            title("diaria", "Diaria", "Prefix", "📅 Diaria",
                rarity = "Uncommon", source = "DailyReward", sortOrder = 30,
                unlockHint = "Reclama 3 recompensas diarias"),
            title("veterana", "Veterana", "Suffix", "⭐ Veterana",
                rarity = "Legendary", source = "Achievement", sortOrder = 40,
                unlockHint = "Gana 30 estrellas"),
            title("mvp", "MVP", "Prefix", "👑 MVP",
                rarity = "Mythic", source = "Promotional", sortOrder = 50,
                unlockHint = "Top 1 del leaderboard"),
        )

        titles.forEach { t ->
            firestore.collection("cosmetics").document("titles")
                .collection("items")
                .document(t["titleId"] as String)
                .set(t, SetOptions.merge())
                .await()
        }
        Timber.d("FirestoreSeeder: titles seeded (${titles.size})")
    }

    private fun title(
        id: String,
        displayName: String,
        position: String,
        text: String,
        rarity: String,
        source: String,
        sortOrder: Int,
        unlockHint: String? = null,
    ): Map<String, Any?> = mapOf(
        "titleId" to id,
        "displayName" to displayName,
        "position" to position,
        "text" to text,
        "rarity" to rarity,
        "source" to source,
        "unlockHint" to unlockHint,
        "sortOrder" to sortOrder,
    )

    // ─────────────────────────────────────────────────────────────────
    // DAILY REWARD CONFIG — campos exactos de DailyRewardConfigDto
    // Ciclo de 7 días con escalado de recompensas
    // ─────────────────────────────────────────────────────────────────

    private suspend fun seedDailyRewardConfig(firestore: FirebaseFirestore) {
        val cycle = listOf(
            dailyDay(1, coins = 50, description = "Día 1 - ¡Bienvenido de vuelta!"),
            dailyDay(2, coins = 75, description = "Día 2 - ¡Sigue así!"),
            dailyDay(3, coins = 100, gems = 1, description = "Día 3 - ¡Una gema para ti!"),
            dailyDay(4, coins = 100, description = "Día 4 - ¡Constancia recompensada!"),
            dailyDay(5, coins = 150, description = "Día 5 - ¡Casi en la cima!"),
            dailyDay(6, coins = 150, gems = 2, description = "Día 6 - ¡Increíble racha!"),
            dailyDay(7, coins = 300, gems = 5, isMilestone = true,
                description = "🎉 Día 7 - ¡Racha completa! Premio especial"),
        )

        val config = mapOf(
            "cycle" to cycle,
            "baseCoins" to 50,
            "streakBonusMultiplier" to 0.1,
            "cooldownMs" to 86_400_000L,   // 24 horas en ms
            "missResetTo" to 1,
            "schemaVersion" to 1,
        )

        firestore.collection("config")
            .document("dailyReward")
            .set(config, SetOptions.merge())
            .await()
        Timber.d("FirestoreSeeder: dailyRewardConfig seeded")
    }

    private fun dailyDay(
        dayNumber: Int,
        coins: Int,
        gems: Int = 0,
        isMilestone: Boolean = false,
        rewardSkinId: String? = null,
        rewardTitleId: String? = null,
        rewardPowerUpId: String? = null,
        description: String = "",
    ): Map<String, Any?> = mapOf(
        "dayNumber" to dayNumber,
        "coins" to coins,
        "gems" to gems,
        "isMilestone" to isMilestone,
        "rewardSkinId" to rewardSkinId,
        "rewardTitleId" to rewardTitleId,
        "rewardPowerUpId" to rewardPowerUpId,
        "description" to description,
    )
}