// =====================================================================
// MissionViewModel.kt
// Block Quest — Missions panel ViewModel
// =====================================================================

package com.blockquest.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockquest.domain.model.MissionProgress
import com.blockquest.domain.usecase.BootstrapMissionsUseCase
import com.blockquest.domain.usecase.ClaimMissionRewardUseCase
import com.blockquest.domain.usecase.ObserveMissionsUseCase
import com.blockquest.domain.usecase.RollDailyMissionsUseCase
import com.blockquest.domain.usecase.RollWeeklyMissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MissionPanelUiState(
    val dailies: List<MissionProgress> = emptyList(),
    val weeklies: List<MissionProgress> = emptyList(),
    val achievements: List<MissionProgress> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class MissionViewModel @Inject constructor(
    private val observe: ObserveMissionsUseCase,
    private val claim: ClaimMissionRewardUseCase,
    private val bootstrap: BootstrapMissionsUseCase,
    private val rollDaily: RollDailyMissionsUseCase,
    private val rollWeekly: RollWeeklyMissionsUseCase,
) : ViewModel() {

    private val _ui = MutableStateFlow(MissionPanelUiState())
    val ui: StateFlow<MissionPanelUiState> = _ui

    val progress: StateFlow<List<MissionProgress>> = observe().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList(),
    )

    init {
        viewModelScope.launch {
            // On first launch, ensure missions are seeded.
            bootstrap()
        }
    }

    fun claimReward(missionId: String) {
        viewModelScope.launch { claim(missionId) }
    }

    fun rollDailyNow() {
        viewModelScope.launch { rollDaily() }
    }

    fun rollWeeklyNow() {
        viewModelScope.launch { rollWeekly() }
    }
}
