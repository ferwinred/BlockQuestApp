// =====================================================================
// LevelRewardService.kt
// Block Quest — Level completion reward calculator
// =====================================================================
//
// Pure functions that compute the reward (coins / gems /
// skins / titles / unlocks) the player earns for completing
// a level. The presenter calls `rewardsFor(level, score,
// stars)` and updates the player state.
//
// All values are computed deterministically from the level
// spec and the player's result — the server can re-derive
// the reward, so we never trust the client.
// =====================================================================

package com.blockquest.domain.scoring

import com.blockquest.domain.model.LevelSpec

data class LevelReward(
    val coins: Int = 0,
    val gems: Int = 0,
    val skinId: String? = null,
    val titleId: String? = null,
    val unlocksWorldIndex: Int? = null,
    val unlocksLevelId: String? = null,
    val firstClearBonusCoins: Int = 0,
)

object LevelRewardService {

    /**
     * Compute the reward for a level completion. Multipliers
     * (silver / gold) are applied on top of the level's base
     * rewards when the player exceeds 2 / 3 stars.
     */
    fun rewardsFor(level: LevelSpec, stars: Int, isFirstClear: Boolean): LevelReward {
        require(stars in 0..3) { "stars must be 0..3" }

        val baseCoins = level.rewardCoins
        val baseGems = level.rewardGems
        val starMultiplier = when (stars) {
            3 -> level.goldMultiplier
            2 -> level.silverMultiplier
            else -> 1f
        }
        val finalCoins = (baseCoins * starMultiplier).toInt().coerceAtLeast(baseCoins)
        val finalGems = (baseGems * starMultiplier).toInt().coerceAtLeast(baseGems)
        val firstClearBonus = if (isFirstClear) baseCoins else 0

        return LevelReward(
            coins = finalCoins,
            gems = finalGems,
            skinId = level.rewardSkinId?.takeIf { isFirstClear },
            titleId = level.rewardTitleId?.takeIf { isFirstClear },
            firstClearBonusCoins = firstClearBonus,
        )
    }
}
