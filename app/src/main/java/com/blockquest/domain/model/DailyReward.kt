// =====================================================================
// DailyReward.kt
// Block Quest — Daily reward cycle model
// =====================================================================

package com.blockquest.domain.model

/**
 * A single day in the daily-reward cycle. The cycle is
 * 7 days by default; day 7 is a milestone (bonus gems + skin).
 */
data class DailyRewardDay(
    val dayNumber: Int,          // 1..N
    val coins: Int,
    val gems: Int = 0,
    val isMilestone: Boolean = false,
    val rewardSkinId: String? = null,
    val rewardTitleId: String? = null,
    val rewardPowerUpId: String? = null,
    val description: String = "",
)

/**
 * The full cycle, loaded from Firestore / Remote Config so
 * marketing can change the rewards without a release.
 *
 * Default cycle (Pradera world 1):
 *
 *   Day 1: 50 coins
 *   Day 2: 75 coins
 *   Day 3: 100 coins
 *   Day 4: 25 coins + 5 gems
 *   Day 5: 150 coins
 *   Day 6: 200 coins
 *   Day 7: 50 coins + 20 gems + "Lucky" skin (milestone)
 */
data class DailyRewardConfig(
    val cycle: List<DailyRewardDay>,
    val baseCoins: Int = 50,
    val streakBonusMultiplier: Float = 0.1f,
    val cooldownMs: Long = 24L * 60 * 60 * 1000,  // 24h
    val missResetTo: Int = 1,  // day to return to if user misses a day
) {
    init {
        require(cycle.isNotEmpty()) { "cycle must be non-empty" }
        require(cycle.size <= 35) { "cycle longer than 35 days is unreasonable" }
        require(cooldownMs > 0)
        require(streakBonusMultiplier in 0f..1f)
    }

    fun dayFor(streak: Int): DailyRewardDay {
        val idx = (streak - 1).mod(cycle.size).coerceAtLeast(0)
        return cycle[idx]
    }
}

/**
 * The just-claimed reward (returned by the repository when
 * the player successfully claims a day). The ViewModel
 * fires the appropriate analytics event and shows the
 * celebration modal.
 */
data class DailyRewardClaimed(
    val day: DailyRewardDay,
    val newStreak: Int,
    val coins: Int,
    val gems: Int,
    val isMilestone: Boolean,
    val nextAvailableAtMs: Long,
)
