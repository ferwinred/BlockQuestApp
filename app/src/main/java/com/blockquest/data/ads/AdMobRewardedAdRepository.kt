// =====================================================================
// AdMobRewardedAdRepository.kt
// Block Quest — AdRepository implementation backed by AdMob
// =====================================================================
//
// Architecture
// ------------
// 1. On app boot the consent manager (`AdConsentManager`)
//    asks for consent if the user is in a regulated region.
// 2. The repository pre-loads one rewarded ad per placement
//    in the background. When the user opts to "watch an ad",
//    we show the preloaded ad. After dismissal we kick off
//    another load.
// 3. The repo is thread-safe: a single Mutex per placement
//    guards the underlying `RewardedAd` so we never show
//    the same ad twice (which would be a violation of
//    Google's terms of service).
//
// Unit IDs
// ---------
// We pull the unit ids from `RemoteConfigRepository` so
// marketing can A/B test different networks / fallbacks.
// The default ids in `RemoteConfigService` are Google's
// well-known test ids — safe to use in dev.
//
// Test ids (from Google docs, last verified Jun 2026):
//   Rewarded:        ca-app-pub-3940256099942544/5224354917
//   Rewarded Int:    ca-app-pub-3940256099942544/5354046379
//   App Open:        ca-app-pub-3940256099942544/3419835294
// =====================================================================

package com.blockquest.data.ads

import android.app.Activity
import android.content.Context
import com.blockquest.domain.repository.AdConsentState
import com.blockquest.domain.repository.AdPlacement
import com.blockquest.domain.repository.AdRepository
import com.blockquest.domain.repository.AdResult
import com.blockquest.domain.repository.AdReward
import com.blockquest.domain.repository.RemoteConfigRepository
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdMobRewardedAdRepository @Inject constructor(
    private val context: Context,
    private val consent: AdConsentManager,
    private val remoteConfig: RemoteConfigRepository,
) : AdRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Per-placement state. The mutex guards the underlying
     * `RewardedAd` so a single impression consumes the
     * ad, and we never show the same ad twice.
     */
    private data class Slot(
        val mutex: Mutex = Mutex(),
        var ad: RewardedAd? = null,
    )

    private val slots = AdPlacement.values().associateWith { Slot() }

    private val _consentState = MutableStateFlow(AdConsentState.Unknown)
    init { _consentState.value = consent.currentState() }

    override fun observeConsent(): Flow<AdConsentState> = _consentState.asStateFlow()

    override suspend fun preloadRewarded(placement: AdPlacement) {
        val slot = slots[placement] ?: return
        slot.mutex.withLock {
            if (slot.ad != null) return@withLock  // already loaded
            val unitId = unitIdFor(placement)
            val deferred = CompletableDeferred<RewardedAd?>()
            RewardedAd.load(
                context,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        if (!deferred.isCompleted) deferred.complete(ad)
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (!deferred.isCompleted) deferred.complete(null)
                    }
                }
            )
            val ad = deferred.await()
            slot.ad = ad
        }
    }

    override suspend fun showRewarded(placement: AdPlacement): AdResult {
        val slot = slots[placement] ?: return AdResult.Failed("unknown_placement")
        // If we don't have a preloaded ad, try once more.
        if (slot.ad == null) {
            preloadRewarded(placement)
        }
        val ad = slot.ad ?: return AdResult.Failed("no_fill")

        val activity = currentActivity
            ?: return AdResult.Failed("no_activity")

        val resultDeferred = CompletableDeferred<AdResult>()

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // The user closed the ad. The result is
                // dispatched from `onUserEarnedReward`
                // (if they watched enough) or as
                // `Dismissed` (if they didn't).
                if (!resultDeferred.isCompleted) {
                    resultDeferred.complete(AdResult.Dismissed)
                }
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                if (!resultDeferred.isCompleted) {
                    resultDeferred.complete(AdResult.Failed(error.message))
                }
            }
            override fun onAdShowedFullScreenContent() {
                // Ad is on screen. The reward callback
                // (onUserEarnedReward) fires when the
                // player has watched enough to claim the
                // reward.
            }
        }

        // Consume the ad (we cannot show it again).
        slot.mutex.withLock { slot.ad = null }

        ad.show(activity) { rewardItem ->
            val amount = rewardItem.amount
            val result = AdResult.Completed(
                AdReward(
                    placement = placement,
                    amount = if (amount > 0) amount else placement.rewardAmount,
                )
            )
            if (!resultDeferred.isCompleted) resultDeferred.complete(result)
        }

        // Trigger the next preload in the background.
        scope.launch {
            runCatching { preloadRewarded(placement) }
        }

        return resultDeferred.await()
    }

    /**
     * Resolves the current Activity. We keep a WeakReference
     * in the Application class (set from each Activity's
     * `onResume`).
     */
    private val currentActivity: Activity?
        get() = ActivityHolder.current

    private fun unitIdFor(placement: AdPlacement): String {
        val key = "ad_unit_id_${placement.id}"
        return remoteConfig.getString(
            key = key,
            default = DEFAULT_UNIT_IDS.getValue(placement),
        )
    }

    companion object {
        /**
         * Google's well-known test ids. Safe to use in dev
         * and never produce real impressions. We default to
         * these in `default` so a fresh dev build never
         * accidentally serves real ads.
         */
        val DEFAULT_UNIT_IDS: Map<AdPlacement, String> = mapOf(
            AdPlacement.ContinueAfterGameOver to
                    "ca-app-pub-3940256099942544/5224354917",
            AdPlacement.DoubleDailyReward to
                    "ca-app-pub-3940256099942544/5224354917",
            AdPlacement.ExtraPowerUp to
                    "ca-app-pub-3940256099942544/5224354917",
            AdPlacement.SecondChance to
                    "ca-app-pub-3940256099942544/5224354917",
        )
    }
}

/**
 * Holds a WeakReference to the current Activity. Each
 * Activity sets this in `onResume` and clears it in
 * `onPause`. The AdMob SDK requires an Activity to
 * show full-screen content.
 */
object ActivityHolder {
    private var ref: java.lang.ref.WeakReference<Activity>? = null
    val current: Activity? get() = ref?.get()
    fun bind(activity: Activity) { ref = java.lang.ref.WeakReference(activity) }
    fun unbind(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }
}
