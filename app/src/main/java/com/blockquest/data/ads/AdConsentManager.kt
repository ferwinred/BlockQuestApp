
// =====================================================================
// AdConsentManager.kt
// Block Quest — UMP (User Messaging Platform) consent wrapper
// =====================================================================
//
// Wraps the Google User Messaging Platform SDK so the rest
// of the app can ask "can I show personalized ads?" without
// importing the SDK directly.
//
// The UMP SDK is a separate dependency (`play-services-ads`
// bundles it as of v22). We keep the wrapper thin: the
// presentation layer calls `requestConsentIfNeeded(activity)`
// when the MainActivity is created. The consent form is
// shown only if the SDK decides the user is in a regulated
// region (EEA, UK, California under 17).
// =====================================================================

package com.blockquest.data.ads

import android.app.Activity
import android.content.Context
import com.blockquest.domain.repository.AdConsentState
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CompletableDeferred
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdConsentManager @Inject constructor(
    private val context: Context,
) {

    /**
     * Synchronous, "what's the consent state right now?"
     * Returns `Unknown` if we haven't asked yet.
     */
    fun currentState(): AdConsentState {
        val info = UserMessagingPlatform.getConsentInformation(context)
        return when (info.consentStatus) {
            ConsentInformation.ConsentStatus.OBTAINED ->
                AdConsentState.Obtained
            ConsentInformation.ConsentStatus.NOT_REQUIRED ->
                AdConsentState.NotRequired
            ConsentInformation.ConsentStatus.REQUIRED ->
                AdConsentState.Required
            ConsentInformation.ConsentStatus.UNKNOWN ->
                AdConsentState.Unknown
            else -> AdConsentState.Unknown
        }
    }

    /**
     * Request consent if the user is in a regulated region.
     * Shows the UMP form if necessary. Safe to call on every
     * app launch — the SDK dedupes.
     */
    suspend fun requestConsentIfNeeded(activity: Activity): AdConsentState {
        val params = com.google.android.ump.ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        val info = UserMessagingPlatform.getConsentInformation(context)

        val result = CompletableDeferred<AdConsentState>()

        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    // Even if there's an error (e.g. no internet), we
                    // return the current state so the app can continue.
                    result.complete(currentState())
                }
            },
            { requestError ->
                // Failed to update info (e.g. network error).
                result.complete(currentState())
            }
        )
        return result.await()
    }
}
