package com.blockquest.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockquest.domain.repository.AppSettings
import com.blockquest.domain.repository.SettingsRepository
import com.blockquest.domain.usecase.ResetProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.blockquest.domain.repository.PlayerRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val playerRepo: PlayerRepository,
    private val resetProgressUseCase: ResetProgressUseCase
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings()
        )

    fun setMusicVolume(volume: Float) {
        viewModelScope.launch {
            settingsRepo.setMusicVolume(volume)
        }
    }

    fun setSfxVolume(volume: Float) {
        viewModelScope.launch {
            settingsRepo.setSfxVolume(volume)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setVibrationEnabled(enabled)
        }
    }

    fun resetProgress() {
        viewModelScope.launch {
            resetProgressUseCase()
        }
    }

    fun linkWithGoogle(idToken: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = playerRepo.linkWithGoogle(idToken)
            onResult(result.isSuccess)
        }
    }
}
