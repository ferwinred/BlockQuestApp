// =====================================================================
// LevelCompleteDialog.kt
// Block Quest — "Nivel completado" celebration overlay
// =====================================================================

package com.blockquest.presentation.ui.screen.gameplay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blockquest.presentation.viewmodel.GameplayOverlay

@Composable
fun LevelCompleteDialog(
    overlay: GameplayOverlay.LevelComplete,
    onContinue: () -> Unit,
    onReplay: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* forced choice */ },
        title = {
            Text(
                text = "¡Nivel completado!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(3) { i ->
                        Text(
                            text = if (i < overlay.stars) "⭐" else "☆",
                            fontSize = 36.sp,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Puntaje: ${overlay.score}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (overlay.isFirstClear) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "🎉 Primera vez",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "🏆 +${overlay.rewardCoins} monedas" +
                            if (overlay.rewardGems > 0) "  💎 +${overlay.rewardGems}" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (overlay.newlyCompletedMissions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Misiones completadas:",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    overlay.newlyCompletedMissions.forEach { m ->
                        Text(
                            text = "✅ ${m.spec.description}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onContinue) {
                Text("Continuar")
            }
        },
        dismissButton = {
            TextButton(onClick = onReplay) {
                Text("Repetir")
            }
        },
    )
}
