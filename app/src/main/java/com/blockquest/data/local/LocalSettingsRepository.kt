package com.blockquest.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.blockquest.domain.repository.AppSettings
import com.blockquest.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSettingsRepository @Inject constructor(
    private val localStore: DataStore<Preferences>
) : SettingsRepository {

    private val keyMusic = floatPreferencesKey("music_volume")
    private val keySfx = floatPreferencesKey("sfx_volume")
    private val keyVibration = booleanPreferencesKey("vibration_enabled")

    override fun observeSettings(): Flow<AppSettings> {
        return localStore.data.map { prefs ->
            AppSettings(
                musicVolume = prefs[keyMusic] ?: 1.0f,
                sfxVolume = prefs[keySfx] ?: 1.0f,
                vibrationEnabled = prefs[keyVibration] ?: true
            )
        }
    }

    override suspend fun setMusicVolume(volume: Float) {
        localStore.edit { prefs ->
            prefs[keyMusic] = volume.coerceIn(0.0f, 1.0f)
        }
    }

    override suspend fun setSfxVolume(volume: Float) {
        localStore.edit { prefs ->
            prefs[keySfx] = volume.coerceIn(0.0f, 1.0f)
        }
    }

    override suspend fun setVibrationEnabled(enabled: Boolean) {
        localStore.edit { prefs ->
            prefs[keyVibration] = enabled
        }
    }
}
