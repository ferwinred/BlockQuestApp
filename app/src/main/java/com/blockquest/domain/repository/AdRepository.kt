// =====================================================================
// AdRepository.kt
// Block Quest — Domain ad repository interface
// =====================================================================
//
// Ads are a side effect (SDK calls, network). The domain layer
// only knows about the *result* of an ad impression:
//
//   * `AdResult.Completed(reward)` — the user watched the
//     whole ad. Caller should grant the reward.
//   * `AdResult.Dismissed` — the user closed the ad early.
//   * `AdResult.Failed(reason)` — the ad failed to load or
//     to play. Caller should fall back to the no-ad reward
//     path (or surface the error).
//
// The implementation is responsible for:
//   * Pre-loading a rewarded ad after every successful
//     impression.
//   * Respecting the user's consent state (UMP).
//   * Failing gracefully on no-fill (AdMob returns "no
//     ad available" ~5% of the time on real traffic).
// =====================================================================

package com.blockquest.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * One ad placement. We model each placement by an id, e.g.
 * "continue_after_gameover" or "double_daily_reward".
 */
enum class AdPlacement(val id: String, val rewardAmount: Int) {
    ContinueAfterGameOver("continue_after_gameover", 3),
    DoubleDailyReward("double_daily_reward", 1),
    ExtraPowerUp("extra_power_up", 1),
    SecondChance("second_chance", 1),
}

/**
 * What the player gets for completing the ad. The repo
 * returns this so the use case can apply the reward
 * consistently (3 pieces, double daily, etc.).
 */
data class AdReward(
    val placement: AdPlacement,
    val amount: Int,
)

/**
 * The result of an ad impression. The use case consumes
 * the result and applies the reward (or surfaces the
 * failure).
 */
sealed class AdResult {
    data class Completed(val reward: AdReward) : AdResult()
    data object Dismissed : AdResult()
    data class Failed(val reason: String) : AdResult()
}

/**
 * Whether the player has consented to personalized ads
 * (via Google's UMP). We only request ads when consent
 * has been collected (required by GDPR/CCPA/etc.).
 */
enum class AdConsentState {
    Unknown,        // not asked yet
    Required,       // user is in a region where consent is required
    NotRequired,    // user is in a region where consent is not required
    Obtained,       // user has consented (or denied non-personalized)
}

/**
 * Ad repository interface. The implementation lives in
 * `data/ads/`.
 */
interface AdRepository {
    /**
     * Reactive consent state. Updated by the consent manager
     * when the user accepts/denies the UMP dialog.
     */
    fun observeConsent(): Flow<AdConsentState>

    /**
     * Show a rewarded ad for the given placement. Suspends
     * until the ad finishes (completed / dismissed / failed).
     */
    suspend fun showRewarded(placement: AdPlacement): AdResult

    /**
     * Preload the next rewarded ad for the given placement.
     * Cheap to call multiple times; the SDK dedupes.
     */
    suspend fun preloadRewarded(placement: AdPlacement)
}
