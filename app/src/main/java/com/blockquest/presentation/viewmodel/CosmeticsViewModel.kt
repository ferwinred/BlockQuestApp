// =====================================================================
// CosmeticsViewModel.kt
// Block Quest — Cosmetics / skins / titles store
// =====================================================================

package com.blockquest.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blockquest.domain.model.EquipResult
import com.blockquest.domain.model.Skin
import com.blockquest.domain.model.Title
import com.blockquest.domain.usecase.EquipSkinUseCase
import com.blockquest.domain.usecase.EquipTitleUseCase
import com.blockquest.domain.usecase.ObserveCosmeticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CosmeticsUiState(
    val skins: List<Skin> = emptyList(),
    val titles: List<Title> = emptyList(),
    val ownedSkinIds: Set<String> = emptySet(),
    val ownedTitleIds: Set<String> = emptySet(),
    val activeSkinId: String = "default",
    val activeTitleId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class CosmeticsViewModel @Inject constructor(
    private val observe: ObserveCosmeticsUseCase,
    private val equipSkin: EquipSkinUseCase,
    private val equipTitle: EquipTitleUseCase,
) : ViewModel() {

    val ui: StateFlow<CosmeticsUiState> = observe().map { state ->
        CosmeticsUiState(
            skins = state.catalog.skins.sortedBy { it.sortOrder },
            titles = state.catalog.titles.sortedBy { it.sortOrder },
            ownedSkinIds = state.inventory.ownedSkinIds,
            ownedTitleIds = state.inventory.ownedTitleIds,
            activeSkinId = state.inventory.activeSkinId,
            activeTitleId = state.inventory.activeTitleId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CosmeticsUiState(),
    )

    fun onSkinTapped(skinId: String) {
        viewModelScope.launch {
            val res = equipSkin(skinId)
            if (res is EquipResult.NotOwned) {
                // We don't surface this as a snackbar yet;
                // the button is just disabled in the UI.
            }
        }
    }

    fun onTitleTapped(titleId: String?) {
        viewModelScope.launch {
            equipTitle(titleId)
        }
    }
}
