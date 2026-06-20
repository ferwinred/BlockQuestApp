// =====================================================================
// DailyRewardModal.kt
// Block Quest — Daily-reward claim modal
// =====================================================================

package com.blockquest.presentation.ui.screen.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockquest.domain.model.DailyRewardConfig
import com.blockquest.domain.model.DailyRewardState

@Composable
fun DailyRewardModal(
    state: DailyRewardState,
    config: DailyRewardConfig,
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
    isClaiming: Boolean = false,
    justClaimedCoins: Int = 0,
    justClaimedGems: Int = 0,
) {
    val currentDay = state.currentStreak
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🎁 Recompensa diaria",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    text = "Racha actual: $currentDay días",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(config.cycle) { day ->
                        DayCell(
                            day = day,
                            isCurrent = day.dayNumber == ((currentDay % config.cycle.size) + 1),
                            isClaimed = day.dayNumber <= currentDay,
                        )
                    }
                }
                if (justClaimedCoins > 0 || justClaimedGems > 0) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (justClaimedCoins > 0) {
                                Text(
                                    "🪙 +$justClaimedCoins monedas",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (justClaimedGems > 0) {
                                Text(
                                    "💎 +$justClaimedGems gemas",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClaim, enabled = !isClaiming) {
                Text(if (justClaimedCoins > 0) "Cerrar" else "Reclamar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

@Composable
private fun DayCell(
    day: com.blockquest.domain.model.DailyRewardDay,
    isCurrent: Boolean,
    isClaimed: Boolean,
) {
    val background = when {
        isClaimed -> MaterialTheme.colorScheme.primary
        isCurrent -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = when {
        isClaimed -> MaterialTheme.colorScheme.onPrimary
        isCurrent -> MaterialTheme.colorScheme.onTertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(background)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Día ${day.dayNumber}",
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
            )
            Text(
                text = "🪙${day.coins}",
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                fontSize = 10.sp,
            )
            if (day.gems > 0) {
                Text(
                    text = "💎${day.gems}",
                    style = MaterialTheme.typography.labelMedium,
                    color = foreground,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
