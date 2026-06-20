// =====================================================================
// UnlockService.kt
// Block Quest — World and level unlock evaluator
// =====================================================================
//
// Pure functions that decide, given a player's progression,
// which worlds and levels should be unlocked.
//
// Rules
// -----
// World 0 (Pradera) is always unlocked.
//
// World N (N > 0) is unlocked when:
//   * World N-1's "boss" level (or last level if no boss)
//     has been completed with at least 1 star, AND
//   * The aggregate stars across world N-1 is ≥
//     `unlockStarsRequired` (configured per world).
//
// Individual levels are unlocked when:
//   * Their world is unlocked, AND
//   * At least one of:
//       a) They are level 0 in their world.
//       b) The previous level in their world has at least
//          1 star.
//
// The result is exposed as a Set<String> of unlocked level
// ids, plus a List<Int> of unlocked world indices, so the
// WorldMap screen can render the state directly.
// =====================================================================

package com.blockquest.domain.level

import com.blockquest.domain.model.LevelResult
import com.blockquest.domain.model.LevelSpec
import com.blockquest.domain.model.ProgressionState
import com.blockquest.domain.model.WorldDefinition
import javax.inject.Inject

data class UnlockState(
    val unlockedWorlds: Set<Int>,
    val unlockedLevelIds: Set<String>,
    val newlyUnlockedWorlds: List<Int>,
    val newlyUnlockedLevels: List<String>,
)

class UnlockService @Inject constructor() {

    /**
     * Evaluate all unlocks against the current state. Returns
     * a fresh `UnlockState` — does NOT mutate the input.
     */
    fun evaluate(
        worlds: List<WorldDefinition>,
        levels: List<LevelSpec>,
        progression: ProgressionState,
    ): UnlockState {
        val newlyWorlds = mutableListOf<Int>()
        val newlyLevels = mutableListOf<String>()

        val unlockedWorlds = HashSet<Int>()
        unlockedWorlds.add(0)  // Pradera is always open

        // World N unlocks when:
        //   * The last level of world N-1 (boss) is completed
        //   * Aggregate stars in world N-1 ≥ unlockStarsRequired
        for (world in worlds.sortedBy { it.worldIndex }) {
            if (world.worldIndex == 0) continue
            val prevWorld = world.worldIndex - 1
            val prevLevels = levels.filter { it.worldIndex == prevWorld }
                .sortedBy { it.levelNumber }
            val lastLevel = prevLevels.lastOrNull() ?: continue
            val lastResult = progression.results[lastLevel.levelId]
            val totalStars = prevLevels.sumOf {
                progression.results[it.levelId]?.stars ?: 0
            }
            val prevUnlocked = prevWorld in unlockedWorlds
            val meetsStars = totalStars >= world.unlockStarsRequired
            val beatsBoss = lastResult?.completed == true
            if (prevUnlocked && meetsStars && beatsBoss) {
                if (prevWorld !in progression.worldUnlocked.indices ||
                    !progression.worldUnlocked[prevWorld]) {
                    newlyWorlds.add(world.worldIndex)
                }
                unlockedWorlds.add(world.worldIndex)
            }
        }

        // Levels unlock per the rules in the file header.
        val unlockedLevelIds = HashSet<String>()
        for (level in levels) {
            if (level.worldIndex !in unlockedWorlds) continue
            if (level.levelNumber == 0) {
                unlockedLevelIds.add(level.levelId)
                continue
            }
            val prevInWorld = levels.firstOrNull {
                it.worldIndex == level.worldIndex &&
                        it.levelNumber == level.levelNumber - 1
            } ?: continue
            val prevResult = progression.results[prevInWorld.levelId]
            if (prevResult?.completed == true) {
                unlockedLevelIds.add(level.levelId)
            }
        }

        // Newly-unlocked levels: present in our set but not
        // in the player's pre-existing results.
        for (id in unlockedLevelIds) {
            if (progression.results[id] == null) newlyLevels.add(id)
        }

        return UnlockState(
            unlockedWorlds = unlockedWorlds,
            unlockedLevelIds = unlockedLevelIds,
            newlyUnlockedWorlds = newlyWorlds,
            newlyUnlockedLevels = newlyLevels,
        )
    }

    /**
     * Default unlock-star requirements per world. 0 for
     * world 0, then ramps up:
     *
     *   0 → n/a (always unlocked)
     *   1 → 3 stars  (1 star in 3 levels of world 0)
     *   2 → 9 stars  (3 stars in 3 levels of world 1)
     *   3 → 18 stars
     *   4 → 27 stars
     */
    fun defaultUnlockStars(worldIndex: Int): Int = when (worldIndex) {
        0 -> 0
        1 -> 3
        2 -> 9
        3 -> 18
        4 -> 27
        else -> 0
    }
}
