// =====================================================================
// DailyRewardService.kt
// Block Quest — Daily login reward cycle
// =====================================================================

package com.blockquest.domain.scoring

import com.blockquest.domain.model.DailyRewardClaimed
import com.blockquest.domain.model.DailyRewardConfig
import com.blockquest.domain.model.DailyRewardDay
import com.blockquest.domain.model.DailyRewardState
// import kotlinx.datetime.Clock

object DailyRewardService {

    /**
     * Decide whether the player can claim today's reward.
     *
     * Rules:
     *  * `lastClaimed == 0` → first-ever claim, always allowed.
     *  * `now - lastClaimed >= cooldownMs` → claim allowed,
     *    advance the streak by 1.
     *  * `now - lastClaimed in (cooldownMs / 2, cooldownMs)` →
     *    claim allowed, streak advances by 1 (early bird
     *    bonus in the presentation layer).
     *  * `now - lastClaimed < cooldownMs / 2` → too early,
     *    not allowed.
     *
     * If the player misses a full cooldown period, the
     * streak resets to `config.missResetTo` (default 1).
     */
    fun canClaim(
        state: DailyRewardState,
        config: DailyRewardConfig,
        now: Long,
    ): Boolean {
        if (state.lastClaimed == 0L) return true
        val elapsed = now - state.lastClaimed
        if (elapsed < config.cooldownMs / 2) return false
        return true
    }

    /**
     * Compute the result of a successful claim. The caller
     * is responsible for persisting the new state.
     */
    fun claim(
        state: DailyRewardState,
        config: DailyRewardConfig,
        now: Long,
    ): DailyRewardClaimed {
        require(canClaim(state, config, now)) { "claim not allowed at $now" }
        val newStreak = if (state.lastClaimed == 0L) {
            1
        } else {
            val elapsed = now - state.lastClaimed
            if (elapsed >= config.cooldownMs * 2) {
                // missed a day → reset
                config.missResetTo
            } else {
                state.currentStreak + 1
            }
        }
        val day = config.dayFor(newStreak)
        val coins = day.coins
        val gems = day.gems
        return DailyRewardClaimed(
            day = day,
            newStreak = newStreak,
            coins = coins,
            gems = gems,
            isMilestone = day.isMilestone,
            nextAvailableAtMs = now + config.cooldownMs,
        )
    }

    /**
     * Time remaining (in millis) until the next claim is
     * allowed. Returns 0 if a claim is available right now.
     */
    fun timeUntilNextClaim(
        state: DailyRewardState,
        config: DailyRewardConfig,
        now: Long,
    ): Long {
        if (state.lastClaimed == 0L) return 0L
        val next = state.lastClaimed + config.cooldownMs
        return (next - now).coerceAtLeast(0L)
    }

    /**
     * Default Pradera cycle. Mirrors the C# `DailyRewardConfig`
     * defaults; the real one is loaded from Firestore.
     */
    fun praderaConfig(): DailyRewardConfig = DailyRewardConfig(
        cycle = listOf(
            DailyRewardDay(1, coins = 50),
            DailyRewardDay(2, coins = 75),
            DailyRewardDay(3, coins = 100, gems = 5),
            DailyRewardDay(4, coins = 25, gems = 5),
            DailyRewardDay(5, coins = 150),
            DailyRewardDay(6, coins = 200),
            DailyRewardDay(7, coins = 50, gems = 20, isMilestone = true,
                rewardSkinId = "lucky", description = "Día 7: ¡Suerte!"),
        )
    )
}
