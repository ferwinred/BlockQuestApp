package com.blockquest.domain.repository

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val musicVolume: Float = 1.0f,
    val sfxVolume: Float = 1.0f,
    val vibrationEnabled: Boolean = true
)

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun setMusicVolume(volume: Float)
    suspend fun setSfxVolume(volume: Float)
    suspend fun setVibrationEnabled(enabled: Boolean)
}
