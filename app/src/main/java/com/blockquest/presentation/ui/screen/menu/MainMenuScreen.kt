// =====================================================================
// MainMenuScreen.kt
// Block Quest — Main menu (Compose)
// =====================================================================

package com.blockquest.presentation.ui.screen.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockquest.presentation.ui.theme.BlockQuestTheme
import com.blockquest.presentation.viewmodel.MainMenuViewModel
import kotlinx.coroutines.launch

@Composable
fun MainMenuScreen(
    onPlayClicked: () -> Unit,
    onMissionsClicked: () -> Unit,
    onCosmeticsClicked: () -> Unit,
    viewModel: MainMenuViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Modal visibility + claimed-reward state
    var showDailyModal  by remember { mutableStateOf(false) }
    var isClaiming      by remember { mutableStateOf(false) }
    var claimedCoins    by remember { mutableIntStateOf(0) }
    var claimedGems     by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = "Block Quest",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text  = "150 niveles · 5 mundos",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPlayClicked) {
                Icon(
                    imageVector     = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier        = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Jugar", style = MaterialTheme.typography.titleLarge)
            }
            OutlinedButton(onClick = onMissionsClicked)  { Text("📋 Misiones") }
            OutlinedButton(onClick = onCosmeticsClicked) { Text("🎨 Skins y títulos") }

            // Daily reward button — visible only when a reward is available.
            if (state.dailyRewardAvailable) {
                Button(
                    onClick = { showDailyModal = true },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) {
                    Text("🎁 Reclamar regalo diario")
                }
            }

            Spacer(Modifier.height(24.dp))
            CurrencyBar(coins = state.currency.coins, gems = state.currency.gems)
        }
    }

    // ── Daily reward modal ────────────────────────────────────────────
    if (showDailyModal && state.dailyRewardConfig != null) {
        DailyRewardModal(
            state           = state.dailyReward,
            config          = state.dailyRewardConfig!!,
            isClaiming      = isClaiming,
            justClaimedCoins = claimedCoins,
            justClaimedGems  = claimedGems,
            onClaim = {
                // Only trigger the claim when nothing has been claimed yet.
                if (claimedCoins == 0 && claimedGems == 0) {
                    isClaiming = true
                    scope.launch {
                        val outcome = viewModel.claimDailyReward()
                        isClaiming   = false
                        claimedCoins = outcome?.coins ?: 0
                        claimedGems  = outcome?.gems  ?: 0
                    }
                } else {
                    // Second tap on "Cerrar" closes the modal.
                    showDailyModal = false
                    claimedCoins   = 0
                    claimedGems    = 0
                }
            },
            onDismiss = {
                showDailyModal = false
                claimedCoins   = 0
                claimedGems    = 0
            },
        )
    }
}

@Composable
private fun CurrencyBar(coins: Int, gems: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "🪙 $coins", style = MaterialTheme.typography.titleMedium)
        Text(text = "💎 $gems", style = MaterialTheme.typography.titleMedium)
    }
}

@Preview
@Composable
private fun MainMenuScreenPreview() {
    BlockQuestTheme {
        Surface { MainMenuScreen(onPlayClicked = {}, onMissionsClicked = {}, onCosmeticsClicked = {}) }
    }
}
