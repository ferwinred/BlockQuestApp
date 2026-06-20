// =====================================================================
// WorldDefinition.kt
// Block Quest — World catalog (5 mundos)
// =====================================================================

package com.blockquest.domain.model

/**
 * Static definition of a world. Mirrors the C# `WorldData`
 * ScriptableObject. The 5 worlds are:
 *
 *   0: Pradera   — grasslands, light pieces, easy
 *   1: Bosque    — forest, crystals, medium
 *   2: Desierto  — desert, heat-locks, hard
 *   3: Espacio   — space, black-holes, expert
 *   4: Final     — boss gauntlet, special pieces only
 */
data class WorldDefinition(
    val worldIndex: Int,
    val worldId: String,
    val displayName: String,
    val tagline: String,
    val themeName: String,         // matches the "themes" remote-config key
    val levelCount: Int,
    val unlockLevelId: String?,    // the level whose completion unlocks this world
    val unlockStarsRequired: Int = 0,  // 0 = complete the previous world's last level
    val ambientMusicId: String? = null,
    val backgroundImageId: String? = null,
) {
    init {
        require(worldIndex in 0..4) { "worldIndex must be 0..4" }
        require(levelCount > 0)
    }
}

/**
 * Aggregate of a world's content + the player's progress
 * in it. Used by the WorldMap screen.
 */
data class WorldState(
    val definition: WorldDefinition,
    val isUnlocked: Boolean,
    val completedLevels: Int,
    val totalLevels: Int,
    val totalStars: Int,
    val maxStars: Int,
) {
    val completionFraction: Float
        get() = if (maxStars == 0) 0f else totalStars.toFloat() / maxStars
    val levelsCompletedFraction: Float
        get() = if (totalLevels == 0) 0f else completedLevels.toFloat() / totalLevels
}
