// =====================================================================
// FirebaseRemoteConfigRepository.kt
// Block Quest — RemoteConfigRepository implementation
// =====================================================================

package com.blockquest.data.firebase

import com.blockquest.domain.repository.RemoteConfigRepository
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRemoteConfigRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : RemoteConfigRepository {

    override suspend fun fetchAndActivate(): Boolean = runCatching {
        val updates = remoteConfig.fetchAndActivate().await()
        updates
    }.getOrDefault(false)

    override fun getString(key: String, default: String): String =
        remoteConfig.getString(key).takeIf { it.isNotEmpty() } ?: default

    override fun getInt(key: String, default: Int): Int =
        runCatching { remoteConfig.getLong(key).toInt() }.getOrDefault(default)

    override fun getFloat(key: String, default: Float): Float =
        runCatching { remoteConfig.getDouble(key).toFloat() }.getOrDefault(default)

    override fun getBool(key: String, default: Boolean): Boolean =
        remoteConfig.getBoolean(key)
}
