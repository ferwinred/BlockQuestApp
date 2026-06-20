// =====================================================================
// FirebaseAnalyticsRepository.kt
// Block Quest — Analytics repository implementation
// =====================================================================

package com.blockquest.data.firebase

import android.os.Bundle
import com.blockquest.domain.repository.AnalyticsRepository
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAnalyticsRepository @Inject constructor(
    private val analytics: FirebaseAnalytics,
) : AnalyticsRepository {

    override fun logEvent(name: String, params: Map<String, Any?>) {
        val bundle = Bundle().apply {
            params.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Int -> putInt(k, v)
                    is Long -> putLong(k, v)
                    is Float -> putFloat(k, v)
                    is Double -> putDouble(k, v)
                    is Boolean -> putBoolean(k, v)
                    null -> { /* omit */ }
                    else -> putString(k, v.toString())
                }
            }
        }
        analytics.logEvent(name, bundle)
    }

    override fun setUserProperty(name: String, value: String) {
        analytics.setUserProperty(name, value)
    }
}
