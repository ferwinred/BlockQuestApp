// =====================================================================
// MainMenuViewModel.kt
// Block Quest — Top-bar currency + daily-reward state
// =====================================================================

package com.blockquest.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockquest.domain.model.CurrencyState
import com.blockquest.domain.model.DailyRewardClaimed
import com.blockquest.domain.model.DailyRewardConfig
import com.blockquest.domain.model.DailyRewardState
import com.blockquest.domain.repository.DailyRewardConfigRepository
import com.blockquest.domain.usecase.ClaimDailyRewardUseCase
import com.blockquest.domain.usecase.EnsureSignedInUseCase
import com.blockquest.domain.usecase.ObserveCurrencyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainMenuUiState(
    val currency: CurrencyState = CurrencyState(),
    val dailyReward: DailyRewardState = DailyRewardState(),
    val dailyRewardConfig: DailyRewardConfig? = null,
    val dailyRewardAvailable: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class MainMenuViewModel @Inject constructor(
    observeCurrency: ObserveCurrencyUseCase,
    private val claimDailyRewardUseCase: ClaimDailyRewardUseCase,
    private val ensureSignedIn: EnsureSignedInUseCase,
    private val dailyRewardConfigRepo: DailyRewardConfigRepository,
) : ViewModel() {

    private val _dailyReward = MutableStateFlow(DailyRewardState())
    private val _errorMessage = MutableStateFlow<String?>(null)

    val ui: StateFlow<MainMenuUiState> = combine(
        observeCurrency(),
        _dailyReward,
        dailyRewardConfigRepo.observeConfig(),
        _errorMessage,
    ) { currency, daily, config, errorMsg ->
        val now      = System.currentTimeMillis()
        val oneDayMs = config.cooldownMs
        val available = daily.lastClaimed == 0L ||
                (now - daily.lastClaimed) >= oneDayMs
        MainMenuUiState(
            currency             = currency,
            dailyReward          = daily,
            dailyRewardConfig    = config,
            dailyRewardAvailable = available,
            isLoading            = false,
            errorMessage         = errorMsg,
        )
    }.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5_000L),
        initialValue   = MainMenuUiState(),
    )

    init {
        viewModelScope.launch {
            runCatching {
                ensureSignedIn()
            }.onFailure { e ->
                _errorMessage.value = e.message ?: "Authentication failed"
            }
        }
    }

    /**
     * Attempts to claim the daily reward.
     * Returns the [DailyRewardClaimed] (coins + gems) on success, null if
     * the cooldown has not elapsed yet.
     * The UI is responsible for showing the result inside [DailyRewardModal].
     */
    suspend fun claimDailyReward(): DailyRewardClaimed? = claimDailyRewardUseCase()

    // Legacy no-arg wrapper kept for call-sites that don't need the result.
    fun onDailyRewardClicked() {
        viewModelScope.launch { claimDailyRewardUseCase() }
    }
}

