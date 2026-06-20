// =====================================================================
// AdsAndCosmetics.kt
// Block Quest — Use cases for rewarded ads + cosmetics
// =====================================================================

package com.blockquest.domain.usecase

import com.blockquest.domain.model.CosmeticCatalog
import com.blockquest.domain.model.CosmeticInventory
import com.blockquest.domain.model.EquipResult
import com.blockquest.domain.repository.AdConsentState
import com.blockquest.domain.repository.AdPlacement
import com.blockquest.domain.repository.AdRepository
import com.blockquest.domain.repository.AdResult
import com.blockquest.domain.repository.AnalyticsRepository
import com.blockquest.domain.repository.CosmeticRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

// =====================================================================
// Rewarded ads
// =====================================================================

/**
 * Show a rewarded ad and apply the reward to the gameplay
 * engine if the user watched enough. The use case is the
 * single source of truth for "watch ad → grant reward",
 * so the analytics + reward logic are consistent across
 * placements.
 *
 * The reward itself is "N extra pieces" for the gameplay
 * context, but other placements might grant different
 * rewards (double daily, extra power-up, etc.). The
 * `onReward` callback is called only on `AdResult.Completed`.
 */
class ShowRewardedAdUseCase @Inject constructor (
    private val ads: AdRepository,
    private val analytics: AnalyticsRepository,
) {
    /**
     * @param placement the ad placement to show
     * @param onReward invoked only when the ad is completed.
     *   Receives the number of reward units the placement
     *   entitles (e.g. 3 for "ContinueAfterGameOver").
     */
    suspend operator fun invoke(
        placement: AdPlacement,
        onReward: suspend (Int) -> Unit,
    ): AdResult {
        analytics.logEvent(
            "ad_request",
            mapOf("placement" to placement.id, "consent" to consentLabel()),
        )
        return when (val result = ads.showRewarded(placement)) {
            is AdResult.Completed -> {
                analytics.logEvent(
                    "ad_completed",
                    mapOf("placement" to placement.id, "amount" to result.reward.amount),
                )
                onReward(result.reward.amount)
                result
            }
            AdResult.Dismissed -> {
                analytics.logEvent("ad_dismissed", mapOf("placement" to placement.id))
                result
            }
            is AdResult.Failed -> {
                analytics.logEvent(
                    "ad_failed",
                    mapOf("placement" to placement.id, "reason" to result.reason),
                )
                result
            }
        }
    }

    private suspend fun consentLabel(): String {
        // The consent state is just a label for analytics.
        // Real consent enforcement is in the AdMob SDK.
        return "unknown"
    }
}

class PreloadRewardedAdUseCase @Inject constructor (
    private val ads: AdRepository,
) {
    suspend operator fun invoke(placement: AdPlacement) = ads.preloadRewarded(placement)
}

class ObserveAdConsentUseCase(
    private val ads: AdRepository,
) {
    operator fun invoke(): Flow<AdConsentState> = ads.observeConsent()
}

// =====================================================================
// Cosmetics
// =====================================================================

class ObserveCosmeticsUseCase @Inject constructor(
    private val repo: CosmeticRepository,
) {
    data class State(
        val catalog: CosmeticCatalog,
        val inventory: CosmeticInventory,
    )
    operator fun invoke(): Flow<State> = kotlinx.coroutines.flow.combine(
        repo.observeCatalog(), repo.observeInventory()
    ) { c, i -> State(c, i) }
}

class EquipSkinUseCase @Inject constructor (
    private val repo: CosmeticRepository,
    private val analytics: AnalyticsRepository,
) {
    suspend operator fun invoke(skinId: String): EquipResult {
        val res = repo.equipSkin(skinId)
        if (res is EquipResult.Ok) {
            analytics.logEvent("skin_equipped", mapOf("skin_id" to skinId))
        }
        return res
    }
}

class EquipTitleUseCase @Inject constructor (
    private val repo: CosmeticRepository,
    private val analytics: AnalyticsRepository,
) {
    suspend operator fun invoke(titleId: String?): EquipResult {
        val res = repo.equipTitle(titleId)
        if (res is EquipResult.Ok) {
            analytics.logEvent(
                "title_equipped",
                mapOf("title_id" to (titleId ?: "none")),
            )
        }
        return res
    }
}

class GrantCosmeticUseCase(
    private val repo: CosmeticRepository,
    private val analytics: AnalyticsRepository,
) {
    suspend operator fun invoke(cosmeticId: String, source: String) {
        repo.grant(cosmeticId)
        analytics.logEvent(
            "cosmetic_granted",
            mapOf("cosmetic_id" to cosmeticId, "source" to source),
        )
    }
}
